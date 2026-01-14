import * as vscode from 'vscode';
import { MESSAGES } from '../constants';
import { IssueTreeProvider } from '../providers/IssueTreeProvider';
import { ConnectionManager } from '../services/ConnectionManager';
import { BurpIssue } from '../types';
import { AdvisoryPanel } from '../ui/AdvisoryPanel';

/**
 * Handlers for issue-related commands
 */
export class IssueCommands {
    constructor(
        private readonly context: vscode.ExtensionContext,
        private readonly connectionManager: ConnectionManager,
        private readonly issueTreeProvider: IssueTreeProvider
    ) { }

    /**
     * Command: Refresh Issues
     */
    public async refreshIssues(): Promise<void> {
        await vscode.window.withProgress({
            location: vscode.ProgressLocation.Notification,
            title: MESSAGES.REFRESHING_ISSUES,
            cancellable: false
        }, async () => {
            await this.issueTreeProvider.forceRefresh();
        });
    }

    /**
     * Command: View Issue Details
     */
    public viewIssueDetails(issue: BurpIssue): void {
        vscode.window.showInformationMessage(`Opening Advisory: ${issue.name}`);
        AdvisoryPanel.show(issue, this.context.extensionUri);
    }

    /**
     * Command: Open Diagnostic Advisory
     */
    public async openDiagnosticAdvisory(issueId: string): Promise<void> {
        const issue = await this.connectionManager.getIssueById(issueId);

        if (issue) {
            AdvisoryPanel.show(issue, this.context.extensionUri);
        } else {
            vscode.window.showErrorMessage(
                "Could not fetch issue details from Burp. The issue may have been removed."
            );
        }
    }
}