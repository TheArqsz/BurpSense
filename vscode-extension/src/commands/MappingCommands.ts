import * as path from 'path';
import * as vscode from 'vscode';
import { BUTTONS, CONFIG_KEYS, LIMITS, MESSAGES, STORAGE_KEYS, UI_LABELS } from '../constants';
import { IssueTreeProvider } from '../providers/IssueTreeProvider';
import { ConnectionManager } from '../services/ConnectionManager';
import { DiagnosticProvider } from '../services/DiagnosticProvider';
import { Logger } from '../services/Logger';
import { MappingManager } from '../services/MappingManager';
import { SmartSuggestionService } from '../services/SmartSuggestionService';
import { BurpIssue, BurpMapping } from '../types';

interface MappingQuickPickItem extends vscode.QuickPickItem {
    mapping: BurpMapping;
}

interface RemoveMappingQuickPickItem extends vscode.QuickPickItem {
    issueId: string;
}

/**
 * Handlers for mapping-related commands
 */
export class MappingCommands {
    constructor(
        private readonly context: vscode.ExtensionContext,
        private readonly connectionManager: ConnectionManager,
        private readonly mappingManager: MappingManager,
        private readonly diagnosticProvider: DiagnosticProvider,
        private readonly smartSuggestionService: SmartSuggestionService,
        private readonly issueTreeProvider: IssueTreeProvider
    ) { }

    /**
     * Command: Map Issue to File
     */
    public async mapIssueToFile(): Promise<void> {
        const editor = vscode.window.activeTextEditor;
        if (!editor) {
            vscode.window.showWarningMessage(MESSAGES.NO_ACTIVE_EDITOR);
            return;
        }

        let issues = this.issueTreeProvider.getCachedIssues();
        let usingStaleCache = false;

        if (issues.length === 0 || !this.issueTreeProvider.hasFreshCache()) {
            const hasStaleCache = issues.length > 0;
            const isDisconnected = !this.connectionManager.isConnected;

            if (isDisconnected && hasStaleCache) {
                usingStaleCache = true;
                Logger.info('Bridge disconnected with stale cache - skipping fetch', 'Mapping');
            } else {
                try {
                    const fetchPromise = this.connectionManager.getAllIssues();

                    const freshIssues = hasStaleCache
                        ? await fetchPromise
                        : await vscode.window.withProgress({
                            location: vscode.ProgressLocation.Notification,
                            title: "Loading issues...",
                            cancellable: false
                        }, async () => fetchPromise);

                    if (freshIssues.length > 0) {
                        issues = freshIssues;
                    }
                } catch (error) {
                    if (hasStaleCache) {
                        usingStaleCache = true;
                    } else {
                        vscode.window.showErrorMessage(
                            `Cannot load issues: ${error}\n\nTry refreshing when bridge is connected.`
                        );
                        return;
                    }
                }
            }
        }

        if (issues.length === 0) {
            vscode.window.showWarningMessage(MESSAGES.NO_ISSUES);
            return;
        }

        const lineNumber = editor.selection.active.line;
        const lineTextLower = editor.document.lineAt(lineNumber).text.toLowerCase();
        const suggestions = this.smartSuggestionService.getSuggestions(issues, lineTextLower);
        const actualLineText = editor.document.lineAt(lineNumber).text.trim();
        const contextBefore = lineNumber > 0
            ? editor.document.lineAt(lineNumber - 1).text.trim()
            : undefined;
        const contextAfter = lineNumber < editor.document.lineCount - 1
            ? editor.document.lineAt(lineNumber + 1).text.trim()
            : undefined;
        const recentMappings = this.context.globalState.get<string[]>(STORAGE_KEYS.RECENT_MAPPINGS, []);
        const recentIssues = issues.filter((i: { id: string; }) => recentMappings.includes(i.id));
        const items: any[] = [];

        const placeholder = usingStaleCache
            ? "Using cached issues (bridge offline) - Select issue to map"
            : "Select the Burp Issue to map to this code";

        if (suggestions.length > 0) {
            items.push({ label: 'Suggested', kind: vscode.QuickPickItemKind.Separator });
            items.push(...this.createQuickPickItems(suggestions));
        }

        if (recentIssues.length > 0) {
            items.push({ label: 'Recent', kind: vscode.QuickPickItemKind.Separator });
            items.push(...this.createQuickPickItems(recentIssues));
        }

        items.push({ label: 'All Issues', kind: vscode.QuickPickItemKind.Separator });
        items.push(...this.createQuickPickItems(issues));

        const selected = await vscode.window.showQuickPick(items.filter(i => i.raw), {
            placeHolder: placeholder
        });

        if (!selected) return;

        const issueId = selected.raw.id;
        const issueName = selected.raw.name;
        const absolutePath = editor.document.uri.fsPath;

        try {
            await vscode.window.withProgress({
                location: vscode.ProgressLocation.Notification,
                title: MESSAGES.CREATING_MAPPING,
                cancellable: false
            }, async (progress) => {
                progress.report({ message: "Saving to workspace..." });
                await this.mappingManager.saveMapping(absolutePath, {
                    issueId,
                    issueName,
                    line: lineNumber + 1,
                    matchText: actualLineText.substring(0, LIMITS.MAX_MATCH_TEXT_LENGTH),
                    contextBefore: contextBefore?.substring(0, LIMITS.MAX_CONTEXT_LENGTH),
                    contextAfter: contextAfter?.substring(0, LIMITS.MAX_CONTEXT_LENGTH),
                    status: 'confirmed'
                });
                const recent = this.context.globalState.get<string[]>(STORAGE_KEYS.RECENT_MAPPINGS, []);
                recent.unshift(issueId);
                const uniqueRecent = [...new Set(recent)].slice(0, LIMITS.MAX_RECENT_MAPPINGS);
                await this.context.globalState.update(STORAGE_KEYS.RECENT_MAPPINGS, uniqueRecent);

                progress.report({ message: "Refreshing diagnostics..." });
                await this.diagnosticProvider.refreshDiagnostics();
            });

            vscode.window.showInformationMessage(
                MESSAGES.MAPPING_CREATED.replace('%s', issueId).replace('%d', String(lineNumber + 1))
            );
        } catch (error: any) {
            vscode.window.showErrorMessage(`Failed to save mapping: ${error.message}`);
        }
    }

    /**
     * Command: Remove Mapping
     */
    public async removeMapping(arg?: any): Promise<void> {
        const editor = vscode.window.activeTextEditor;
        let idToRemove: string | undefined;
        let filePath: string | undefined;
        let lineNumber: number | undefined;

        if (typeof arg === 'string' && arg.length < 50 && !arg.includes('/')) {
            idToRemove = arg;
        }
        if (!idToRemove && editor) {
            const line = editor.selection.active.line;
            filePath = vscode.workspace.asRelativePath(editor.document.uri);
            lineNumber = line + 1;

            const diagnostics = vscode.languages.getDiagnostics(editor.document.uri)
                .filter(d => d.source === 'BurpSense' && d.range.start.line === line);

            if (diagnostics.length === 0) {
                vscode.window.showInformationMessage(MESSAGES.NO_MAPPING_ON_LINE);
                return;
            }

            if (diagnostics.length === 1) {
                idToRemove = diagnostics[0].code as string;
            } else {
                const selected = await vscode.window.showQuickPick<RemoveMappingQuickPickItem>(
                    diagnostics.map((d): RemoveMappingQuickPickItem => ({
                        label: d.message.replace('[BurpSense] ', ''),
                        detail: `Issue ID: ${d.code as string}`,
                        issueId: d.code as string
                    })),
                    { placeHolder: "Multiple issues on this line. Which to remove?" }
                );
                if (selected) idToRemove = selected.issueId;
            }
        }

        if (!idToRemove) return;
        const config = vscode.workspace.getConfiguration();
        const confirmDeletion = config.get<boolean>(CONFIG_KEYS.CONFIRM_MAPPING_DELETION, true);

        if (confirmDeletion) {
            const action = await vscode.window.showWarningMessage(
                `Remove a mapping for issue ${idToRemove}?`,
                { modal: true },
                BUTTONS.REMOVE,
                BUTTONS.CANCEL
            );

            if (action !== BUTTONS.REMOVE) {
                return;
            }
        }

        if (filePath && lineNumber) {
            await this.mappingManager.removeMapping(idToRemove, filePath, lineNumber);
        } else {
            await this.mappingManager.removeMapping(idToRemove);
        }

        await this.diagnosticProvider.refreshDiagnostics();
        vscode.window.showInformationMessage(
            MESSAGES.MAPPING_REMOVED.replace('%s', idToRemove)
        );
    }

    /**
     * Command: Remove Mapping from Tree
     */
    public async removeMappingFromTree(item: any): Promise<void> {
        if (!item || !item.rawData) {
            vscode.window.showErrorMessage(MESSAGES.INVALID_ISSUE_ITEM);
            return;
        }

        const issues: BurpIssue[] = Array.isArray(item.rawData)
            ? item.rawData
            : [item.rawData];

        if (issues.length === 0 || !issues[0]?.id || !issues[0]?.name) {
            vscode.window.showErrorMessage(MESSAGES.INVALID_ISSUE_ITEM);
            return;
        }

        const issueIds = issues.map(issue => issue.id);
        const issueName = issues[0].name;

        const allMappings = await this.mappingManager.loadMappings();
        const mappingsForIssue = allMappings.mappings.filter(m =>
            issueIds.includes(m.issueId)
        );

        if (mappingsForIssue.length === 0) {
            vscode.window.showInformationMessage(MESSAGES.NO_MAPPINGS_FOR_ISSUE);
            return;
        }

        if (mappingsForIssue.length === 1) {
            const config = vscode.workspace.getConfiguration();
            const confirmDeletion = config.get<boolean>(CONFIG_KEYS.CONFIRM_MAPPING_DELETION, true);

            if (confirmDeletion) {
                const mapping = mappingsForIssue[0];
                const location = `${path.basename(mapping.filePath)}:${mapping.line}`;

                const action = await vscode.window.showWarningMessage(
                    MESSAGES.CONFIRM_REMOVE_MAPPING
                        .replace('%s', issueName)
                        .replace('%s', location),
                    { modal: true },
                    BUTTONS.REMOVE,
                    BUTTONS.CANCEL
                );

                if (action !== BUTTONS.REMOVE) {
                    return;
                }
            }

            const mapping = mappingsForIssue[0];
            await this.mappingManager.removeMapping(mapping.issueId, mapping.filePath, mapping.line);
            await this.diagnosticProvider.refreshDiagnostics();
            vscode.window.showInformationMessage(
                MESSAGES.MULTIPLE_MAPPINGS_REMOVED.replace('%s', mapping.issueId)
            );
            return;
        }

        const action = await vscode.window.showQuickPick([
            {
                label: UI_LABELS.REMOVE_ALL_OPTION.replace('%d', String(mappingsForIssue.length)),
                description: UI_LABELS.REMOVE_ALL_DESCRIPTION,
                detail: UI_LABELS.REMOVE_ALL_DETAIL.replace('%s', issueName),
                action: 'all'
            },
            {
                label: UI_LABELS.CHOOSE_SPECIFIC_OPTION,
                description: UI_LABELS.CHOOSE_SPECIFIC_DESCRIPTION,
                detail: UI_LABELS.CHOOSE_SPECIFIC_DETAIL,
                action: 'choose'
            },
            {
                label: UI_LABELS.CANCEL_OPTION,
                description: '',
                detail: UI_LABELS.CANCEL_DETAIL,
                action: 'cancel'
            }
        ], {
            placeHolder: MESSAGES.MANAGE_MAPPINGS_PLACEHOLDER
                .replace('%d', String(mappingsForIssue.length)),
            title: MESSAGES.MANAGE_MAPPINGS_TITLE.replace('%s', issueName)
        });

        if (!action || action.action === 'cancel') {
            return;
        }

        if (action.action === 'all') {
            const config = vscode.workspace.getConfiguration();
            const confirmDeletion = config.get<boolean>(CONFIG_KEYS.CONFIRM_MAPPING_DELETION, true);

            if (confirmDeletion) {
                const confirm = await vscode.window.showWarningMessage(
                    MESSAGES.CONFIRM_REMOVE_ALL_MAPPINGS
                        .replace('%d', String(mappingsForIssue.length))
                        .replace('%s', issueName),
                    { modal: true },
                    BUTTONS.REMOVE,
                    BUTTONS.CANCEL
                );

                if (confirm !== BUTTONS.REMOVE) {
                    return;
                }
            }

            await this.mappingManager.removeMapping(mappingsForIssue[0].issueId);
            await this.diagnosticProvider.refreshDiagnostics();
            vscode.window.showInformationMessage(
                MESSAGES.ALL_MAPPINGS_REMOVED
                    .replace('%d', String(mappingsForIssue.length))
                    .replace('%s', issueName)
            );
        } else {
            const selected = await vscode.window.showQuickPick(
                mappingsForIssue.map((m): MappingQuickPickItem => ({
                    label: `${UI_LABELS.FILE_ICON} ${path.basename(m.filePath)}`,
                    description: `Line ${m.line}`,
                    detail: m.matchText || UI_LABELS.NO_PREVIEW,
                    mapping: m,
                    picked: false
                })),
                {
                    canPickMany: true,
                    placeHolder: MESSAGES.SELECT_MAPPINGS_TO_REMOVE
                        .replace('%d', String(mappingsForIssue.length)),
                    title: MESSAGES.MANAGE_MAPPINGS_TITLE.replace('%s', issueName)
                }
            );

            if (!selected || selected.length === 0) {
                return;
            }

            const config = vscode.workspace.getConfiguration();
            const confirmDeletion = config.get<boolean>(CONFIG_KEYS.CONFIRM_MAPPING_DELETION, true);

            if (confirmDeletion) {
                const locationsList = selected
                    .map(s => `  • ${s.mapping.filePath}:${s.mapping.line}`)
                    .join('\n');

                const confirm = await vscode.window.showWarningMessage(
                    MESSAGES.CONFIRM_REMOVE_SELECTED_MAPPINGS
                        .replace('%d', String(selected.length))
                        .replace('%s', issueName)
                        .replace('%s', locationsList),
                    { modal: true },
                    BUTTONS.REMOVE,
                    BUTTONS.CANCEL
                );

                if (confirm !== BUTTONS.REMOVE) {
                    return;
                }
            }

            for (const item of selected) {
                await this.mappingManager.removeMapping(
                    item.mapping.issueId,
                    item.mapping.filePath,
                    item.mapping.line
                );
            }

            await this.diagnosticProvider.refreshDiagnostics();
            vscode.window.showInformationMessage(
                MESSAGES.MULTIPLE_MAPPINGS_REMOVED
                    .replace('%d', String(selected.length))
                    .replace('%s', issueName)
            );
        }
    }

    /**
     * Command: Bulk Remove Mappings
     */
    public async bulkRemoveMappings(): Promise<void> {
        const store = await this.mappingManager.loadMappings();

        if (store.mappings.length === 0) {
            vscode.window.showInformationMessage('No mappings to remove');
            return;
        }

        const selected = await vscode.window.showQuickPick(
            store.mappings.map(m => ({
                label: m.issueId,
                description: `Line ${m.line} in ${path.basename(m.filePath)}`,
                detail: m.matchText,
                mapping: m
            })),
            {
                canPickMany: true,
                placeHolder: 'Select mappings to remove (use Ctrl/Cmd to select multiple)'
            }
        );

        if (selected && selected.length > 0) {
            const ids = selected.map(s => s.mapping.issueId);

            const confirm = await vscode.window.showWarningMessage(
                `Remove ${ids.length} mappings?`,
                { modal: true },
                BUTTONS.REMOVE
            );

            if (confirm === BUTTONS.REMOVE) {
                const count = await this.mappingManager.removeMappings(ids);
                await this.diagnosticProvider.refreshDiagnostics();
                vscode.window.showInformationMessage(`Removed ${count} mappings`);
            }
        }
    }

    /**
     * Command: Export Mappings
     */
    public async exportMappings(): Promise<void> {
        await this.mappingManager.exportMappings();
    }

    /**
     * Command: Import Mappings
     */
    public async importMappings(): Promise<void> {
        await this.mappingManager.importMappings();
        await this.diagnosticProvider.refreshDiagnostics();
    }

    /**
     * Helper: Create quick pick items from issues
     */
    private createQuickPickItems(issues: BurpIssue[]) {
        return issues.map(i => ({
            label: i.name,
            description: i.id,
            detail: `${i.severity} | ${i.baseUrl}`,
            raw: i
        }));
    }
}