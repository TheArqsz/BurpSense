import * as vscode from 'vscode';
import { COMMANDS, DIAGNOSTIC_SOURCE, DISPLAY } from '../constants';

/**
 * Provides quick fix code actions for BurpSense diagnostics.
 * Shows "View Details" and "Remove Mapping" options in lightbulb menu.
 */
export class BurpCodeActionProvider implements vscode.CodeActionProvider {
    /**
     * Declares this provider supplies QuickFix actions.
     */
    public static readonly providedCodeActionKinds = [
        vscode.CodeActionKind.QuickFix
    ];

    /**
     * Creates code actions for BurpSense diagnostics.
     * 
     * @param document - Current document
     * @param range - Selection range
     * @param context - Contains diagnostics at cursor
     * @returns Array of available code actions
     */
    public provideCodeActions(
        document: vscode.TextDocument,
        range: vscode.Range | vscode.Selection,
        context: vscode.CodeActionContext
    ): vscode.CodeAction[] {
        const burpDiagnostics = context.diagnostics.filter(d => d.source === DIAGNOSTIC_SOURCE);
        return burpDiagnostics.flatMap(diagnostic => [
            this.createViewDetailsAction(diagnostic),
            this.createRemoveAction(diagnostic)
        ]);
    }

    /**
     * Creates "View Details" action.
     * Marked as preferred (shows at top of menu).
     * 
     * @param diagnostic - Diagnostic to view
     * @returns Code action that opens advisory panel
     */
    private createViewDetailsAction(diagnostic: vscode.Diagnostic): vscode.CodeAction {
        const issueId = diagnostic.code as string;
        const issueName = this.extractIssueName(diagnostic);
        const shortId = this.getShortId(issueId);

        const label = `View Details: ${issueName} [${shortId}]`;

        const action = new vscode.CodeAction(label, vscode.CodeActionKind.QuickFix);
        action.command = {
            command: COMMANDS.OPEN_DIAGNOSTIC_ADVISORY,
            title: 'Open Advisory',
            arguments: [diagnostic.code]
        };

        action.isPreferred = true;
        return action;
    }

    /**
     * Creates "Remove Mapping" action.
     * 
     * @param diagnostic - Diagnostic to remove
     * @returns Code action that removes the mapping
     */
    private createRemoveAction(diagnostic: vscode.Diagnostic): vscode.CodeAction {
        const issueId = diagnostic.code as string;
        const issueName = this.extractIssueName(diagnostic);
        const shortId = this.getShortId(issueId);

        const label = `Remove: ${issueName} [${shortId}]`;

        const action = new vscode.CodeAction(label, vscode.CodeActionKind.QuickFix);
        action.command = {
            command: COMMANDS.REMOVE_MAPPING,
            title: 'Remove Mapping',
            arguments: [issueId]
        };
        return action;
    }

    /**
     * Extracts issue name from diagnostic message.
     * Truncates long names with ellipsis.
     * 
     * @param diagnostic - Diagnostic with message
     * @returns Issue name or ID if extraction fails
     */
    private extractIssueName(diagnostic: vscode.Diagnostic): string {
        const match = diagnostic.message.match(/\[BurpSense\]\s+(?:Mapping lost:\s+)?(.+?)(?:\.|$)/);
        if (match) {
            let name = match[1].trim();
            if (name.length > DISPLAY.MAX_ISSUE_NAME_LENGTH) {
                name = name.substring(0, DISPLAY.TRUNCATED_NAME_LENGTH) + '...';
            }
            return name;
        }
        return diagnostic.code as string;
    }

    /**
     * Gets short version of issue ID for display.
     * 
     * @param fullId - Full issue ID
     * @returns First DISPLAY.SHORT_ID_LENGTH characters
     */
    private getShortId(fullId: string): string {
        return fullId.substring(0, DISPLAY.SHORT_ID_LENGTH);
    }
}