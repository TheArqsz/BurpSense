import * as vscode from 'vscode';
import { CONFIDENCE_LEVELS, CONFIG_KEYS, CONFIG_SECTION, CONTEXT_KEYS, SEVERITY_LEVELS, SEVERITY_WEIGHTS } from '../constants';
import { IssueTreeProvider } from '../providers/IssueTreeProvider';
import { ConnectionManager } from '../services/ConnectionManager';
import { FilterPreset } from '../types';

/**
 * Handlers for filter-related commands
 */
export class FilterCommands {
    constructor(
        private readonly connectionManager: ConnectionManager,
        private readonly issueTreeProvider: IssueTreeProvider
    ) { }

    /**
     * Command: Toggle Scope
     */
    public async toggleScope(): Promise<void> {
        const config = vscode.workspace.getConfiguration();
        const currentState = config.get<boolean>(CONFIG_KEYS.IN_SCOPE_ONLY) ?? true;
        const newState = !currentState;

        await config.update(CONFIG_KEYS.IN_SCOPE_ONLY, !currentState, vscode.ConfigurationTarget.Global);

        await vscode.commands.executeCommand('setContext', CONFIG_KEYS.IN_SCOPE_ONLY, newState);

        await this.issueTreeProvider.forceRefresh();

        const message = newState ? 'Showing only in-scope issues' : 'Showing all issues';
        vscode.window.showInformationMessage(`BurpSense filter: ${message}`);
    }

    /**
     * Command: Select Minimum Severity
     */
    public async selectMinSeverity(): Promise<void> {
        const selection = await vscode.window.showQuickPick([...SEVERITY_LEVELS], {
            placeHolder: 'Select Minimum Severity'
        });

        if (selection) {
            await vscode.workspace.getConfiguration()
                .update(CONFIG_KEYS.MIN_SEVERITY, selection, vscode.ConfigurationTarget.Global);

            await this.issueTreeProvider.forceRefresh();
        }
    }

    /**
     * Command: Select Minimum Confidence
     */
    public async selectMinConfidence(): Promise<void> {
        const selection = await vscode.window.showQuickPick([...CONFIDENCE_LEVELS], {
            placeHolder: 'Select Minimum Confidence'
        });

        if (selection) {
            await vscode.workspace.getConfiguration()
                .update(CONFIG_KEYS.MIN_CONFIDENCE, selection, vscode.ConfigurationTarget.Global);
            await this.issueTreeProvider.forceRefresh();
        }
    }

    /**
     * Command: Filter Preview
     */
    public async filterPreview(): Promise<void> {
        const allIssues = await this.connectionManager.getAllIssues();

        const countBySeverity = (severity: string) => {
            return allIssues.filter((i: { severity: string | number; }) => {
                const issueWeight = SEVERITY_WEIGHTS[i.severity] || 0;
                const thresholdWeight = SEVERITY_WEIGHTS[severity] || 0;
                return issueWeight >= thresholdWeight;
            }).length;
        };

        const presets: FilterPreset[] = [
            {
                label: '$(shield) All Issues',
                detail: `Would show all ${allIssues.length} issues`,
                filters: { severity: SEVERITY_LEVELS[SEVERITY_LEVELS.length - 1], confidence: CONFIDENCE_LEVELS[CONFIDENCE_LEVELS.length - 1], inScope: false }
            },
            {
                label: '$(error) High Severity Only',
                detail: `Would show ${countBySeverity('HIGH')} issues`,
                filters: { severity: 'HIGH', confidence: CONFIDENCE_LEVELS[CONFIDENCE_LEVELS.length - 1], inScope: false }
            },
            {
                label: '$(warning) High + Medium',
                detail: `Would show ${countBySeverity('MEDIUM')} issues`,
                filters: { severity: 'MEDIUM', confidence: CONFIDENCE_LEVELS[CONFIDENCE_LEVELS.length - 1], inScope: false }
            },
            {
                label: '$(check) In-Scope High + Medium (Recommended)',
                detail: 'Most common - shows important in-scope findings',
                filters: { severity: 'MEDIUM', confidence: 'FIRM', inScope: true }
            }
        ];

        const selected = await vscode.window.showQuickPick(presets, {
            placeHolder: 'Select filter preset'
        });

        if (selected) {
            const config = vscode.workspace.getConfiguration();
            await config.update(CONFIG_KEYS.MIN_SEVERITY, selected.filters.severity, vscode.ConfigurationTarget.Global);
            await config.update(CONFIG_KEYS.MIN_CONFIDENCE, selected.filters.confidence, vscode.ConfigurationTarget.Global);
            await config.update(CONFIG_KEYS.IN_SCOPE_ONLY, selected.filters.inScope, vscode.ConfigurationTarget.Global);
            vscode.window.showInformationMessage('Filter preset applied. Refreshing issues...');
            await this.issueTreeProvider.forceRefresh();
        }
    }

    /**
     * Command: Search Issues
     */
    public async searchIssues(): Promise<void> {
        const searchText = await vscode.window.showInputBox({
            prompt: "Search issues by name, URL or ID",
            placeHolder: "e.g. SQL injection, example.com or issue ID",
            value: this.issueTreeProvider.getFilterText()
        });

        if (searchText !== undefined) {
            if (searchText.trim()) {
                this.issueTreeProvider.setFilter(searchText);
            } else {
                this.issueTreeProvider.clearSearchFilter();
            }
        }
    }

    /**
     * Command: Clear Search Filter
     */
    public clearSearchFilter(): void {
        this.issueTreeProvider.clearSearchFilter();
        vscode.commands.executeCommand('setContext', CONTEXT_KEYS.HAS_FILTER, false);
        vscode.window.showInformationMessage('Search filter cleared');
    }
}