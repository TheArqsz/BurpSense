import * as path from 'path';
import * as vscode from 'vscode';
import { CONFIG_KEYS, CONFIG_SECTION, DIAGNOSTIC_SOURCE, DRIFT, LIMITS, SIMILARITY } from '../constants';
import { BurpMapping, DriftDetail, LineDriftResult, MappingProcessResult } from '../types';
import { ConnectionManager } from './ConnectionManager';
import { Logger } from './Logger';
import { MappingManager } from './MappingManager';

/**
 * Manages VS Code diagnostics for mapped issues.
 * Handles line drift detection as code changes.
 * Auto-updates mappings when code moves within reasonable bounds.
 */
export class DiagnosticProvider {
    private readonly diagnosticCollection: vscode.DiagnosticCollection;
    private driftNotificationShownPerWorkspace: Map<string, boolean> = new Map();

    constructor(
        private context: vscode.ExtensionContext,
        private connectionManager: ConnectionManager,
        private mappingManager: MappingManager
    ) {
        this.diagnosticCollection = vscode.languages.createDiagnosticCollection(CONFIG_SECTION);
        this.context.subscriptions.push(this.diagnosticCollection);

        vscode.workspace.onDidChangeWorkspaceFolders(() => {
            this.driftNotificationShownPerWorkspace.clear();
        });
    }

    /**
     * Refreshes all diagnostics across workspace.
     * Detects line drift and auto-updates mappings where possible.
     * Only shows drift notification once per workspace session.
     */
    public async refreshDiagnostics(): Promise<void> {
        try {
            const store = await this.mappingManager.loadMappings();
            const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
            if (!workspaceFolder) {
                this.diagnosticCollection.clear();
                return;
            }

            if (store.mappings.length === 0) {
                this.diagnosticCollection.clear();
                return;
            }

            const pendingUpdates: BurpMapping[] = [];
            const driftDetails: DriftDetail[] = [];
            const diagnosticsByFile = new Map<string, vscode.Diagnostic[]>();
            const openDocs = new Map(vscode.workspace.textDocuments.map(d => [d.uri.toString(), d]));

            for (const mapping of store.mappings) {
                const fileUri = vscode.Uri.joinPath(workspaceFolder.uri, mapping.filePath);
                const document = openDocs.get(fileUri.toString());

                const diagnostic = document
                    ? this.processMappingWithDriftDetection(document, mapping, pendingUpdates, driftDetails)
                    : this.createBasicDiagnostic(mapping);

                if (diagnostic) {
                    const existing = diagnosticsByFile.get(fileUri.toString()) || [];
                    existing.push(diagnostic);
                    diagnosticsByFile.set(fileUri.toString(), existing);
                }
            }

            if (pendingUpdates.length > 0) {
                await this.mappingManager.updateMappings(pendingUpdates);
                this.notifyAboutDrift(driftDetails);
            }

            this.diagnosticCollection.clear();
            diagnosticsByFile.forEach((diags, uriStr) => {
                this.diagnosticCollection.set(vscode.Uri.parse(uriStr), diags);
            });
        } catch (error) {
            Logger.error('BurpSense: Failed to refresh diagnostics', error);
            vscode.window.showErrorMessage(`BurpSense: Failed to refresh diagnostics - ${error}`);
        }
    }

    /**
     * Resets drift notification flag for current workspace.
     * Call when user explicitly refreshes or changes workspace.
     */
    public resetDriftNotifications(): void {
        this.driftNotificationShownPerWorkspace.clear();
    }

    /**
     * Processes a mapping with drift detection and auto-adjustment.
     * Collects drift details for batch notification.
     * 
     * @param document - Open document to check against
     * @param mapping - Original mapping
     * @param pendingUpdates - Array to collect updates for batch write
     * @param driftDetails - Array to collect drift info for notification
     * @returns Diagnostic or undefined if mapping is invalid
     */
    private processMappingWithDriftDetection(
        document: vscode.TextDocument,
        mapping: BurpMapping,
        pendingUpdates: BurpMapping[],
        driftDetails: DriftDetail[]
    ): vscode.Diagnostic | undefined {
        const result = this.processMapping(document, mapping);

        if (result.shouldUpdate) {
            const newLine = result.newLine! - 1;
            const newText = document.lineAt(newLine).text.trim();

            const newContextBefore = newLine > 0
                ? document.lineAt(newLine - 1).text.trim()
                : undefined;
            const newContextAfter = newLine < document.lineCount - 1
                ? document.lineAt(newLine + 1).text.trim()
                : undefined;

            pendingUpdates.push({
                ...mapping,
                line: result.newLine!,
                matchText: newText.substring(0, LIMITS.MAX_MATCH_TEXT_LENGTH),
                contextBefore: newContextBefore?.substring(0, LIMITS.MAX_CONTEXT_LENGTH),
                contextAfter: newContextAfter?.substring(0, LIMITS.MAX_CONTEXT_LENGTH)
            });

            if (mapping.line !== result.newLine!) {
                Logger.info(`Automatic drift adjustment: ${mapping.issueId} moved from ${mapping.line} to ${result.newLine!}`, 'Diagnostics');
                driftDetails.push({
                    file: path.basename(mapping.filePath),
                    from: mapping.line,
                    to: result.newLine!
                });
            }
        }

        return result.diagnostic;
    }

    /**
     * Creates a diagnostic without drift detection.
     * Used for closed documents where the current state cannot be verified.
     * 
     * @param mapping - Mapping data
     * @returns Diagnostic at original line number
     */
    private createBasicDiagnostic(mapping: BurpMapping): vscode.Diagnostic {
        const line = mapping.line - 1;
        const range = new vscode.Range(line, 0, line, 200);

        const displayName = mapping.issueName || mapping.issueId;
        const message = `[BurpSense] ${displayName}`;

        const diagnostic = new vscode.Diagnostic(
            range,
            message,
            this.mapStatusToSeverity(mapping.status)
        );

        diagnostic.code = mapping.issueId;
        diagnostic.source = DIAGNOSTIC_SOURCE;

        return diagnostic;
    }

    /**
     * Processes a mapping to determine current line and update needs.
     * Returns diagnostic and whether mapping should be updated.
     * 
     * @param document - Document containing the code
     * @param mapping - Original mapping
     * @returns Processing result with diagnostic and update flag
     */
    private processMapping(
        document: vscode.TextDocument,
        mapping: BurpMapping
    ): MappingProcessResult {
        const currentLine = mapping.line - 1;
        const safeCurrentLine = Math.min(currentLine, document.lineCount - 1);
        const lineContent = document.lineAt(safeCurrentLine).text;

        const normalizedLineContent = lineContent.trim();
        const normalizedMatchText = (mapping.matchText || "").trim();

        if (normalizedMatchText && normalizedLineContent.includes(normalizedMatchText)) {
            return {
                diagnostic: this.createDiagnostic(currentLine, mapping),
                shouldUpdate: false
            };
        }

        if (normalizedMatchText && this.isSimilarEnough(normalizedLineContent, normalizedMatchText)) {
            return {
                diagnostic: this.createDiagnostic(currentLine, mapping),
                shouldUpdate: true,
                newLine: currentLine + 1
            };
        }

        const driftResult = this.findLineDrift(document, mapping);

        if (driftResult.found) {
            return {
                diagnostic: this.createDiagnostic(driftResult.line!, mapping),
                shouldUpdate: true,
                newLine: driftResult.line! + 1
            };
        }

        return {
            diagnostic: this.createOrphanedDiagnostic(safeCurrentLine, mapping),
            shouldUpdate: false
        };
    }

    /**
     * Checks if two strings are similar enough to be considered the same code.
     * Uses fast paths for common cases, falls back to Levenshtein for fuzzy matching.
     * 
     * @param current - Code as it exists now
     * @param original - Code when mapping was created
     * @returns True if similarity >= 70%
     * 
     * @remarks
     * For strings >200 chars, uses sampling instead of full comparison.
     */
    private isSimilarEnough(current: string, original: string): boolean {
        if (!current || !original) return false;

        const s1 = current.toLowerCase().replace(/\s+/g, ' ').trim();
        const s2 = original.toLowerCase().replace(/\s+/g, ' ').trim();

        if (s1 === s2) return true;

        const maxLength = Math.max(s1.length, s2.length);
        const minLength = Math.min(s1.length, s2.length);

        if ((maxLength - minLength) / maxLength > SIMILARITY.LENGTH_DIFFERENCE_THRESHOLD) {
            return false;
        }

        if (s1.includes(s2) || s2.includes(s1)) {
            return true;
        }

        if (maxLength > SIMILARITY.MAX_STRING_LENGTH_FOR_LEVENSHTEIN) {
            const sample1 = s1.substring(0, SIMILARITY.SAMPLE_LENGTH);
            const sample2 = s2.substring(0, SIMILARITY.SAMPLE_LENGTH);
            return sample1.includes(sample2) || sample2.includes(sample1);
        }

        const similarity = this.calculateSimilarity(current, original);
        return similarity >= SIMILARITY.MINIMUM_THRESHOLD;
    }

    /**
     * Calculates similarity using Levenshtein distance.
     * 
     * @param str1 - First string
     * @param str2 - Second string
     * @returns Similarity ratio from 0.0 to 1.0
     */
    private calculateSimilarity(str1: string, str2: string): number {
        const s1 = str1.toLowerCase().replace(/\s+/g, ' ').trim();
        const s2 = str2.toLowerCase().replace(/\s+/g, ' ').trim();

        if (s1 === s2) return 1.0;
        if (!s1 || !s2) return 0.0;

        const maxLength = Math.max(s1.length, s2.length);

        if (maxLength > SIMILARITY.MAX_STRING_LENGTH_FOR_LEVENSHTEIN) {
            Logger.warn(`calculateSimilarity: string too long (${maxLength} chars)`, 'Diagnostics');
            return 0.0;
        }

        const maxAllowedDistance = Math.floor(maxLength * (1 - SIMILARITY.MINIMUM_THRESHOLD));
        const distance = this.levenshteinDistance(s1, s2, maxAllowedDistance);

        if (distance > maxAllowedDistance) {
            return 0;
        }

        return 1 - (distance / maxLength);
    }

    /**
     * Computes Levenshtein distance with early termination.
     * 
     * @param str1 - First string
     * @param str2 - Second string
     * @param maxDistance - Stop if distance exceeds this (returns maxDistance + 1)
     * @returns Edit distance between strings
     */
    private levenshteinDistance(str1: string, str2: string, maxDistance?: number): number {
        if (str1 === str2) return 0;

        const len1 = str1.length;
        const len2 = str2.length;

        if (len1 === 0) return len2;
        if (len2 === 0) return len1;

        const lengthDiff = Math.abs(len1 - len2);
        if (maxDistance !== undefined && lengthDiff > maxDistance) {
            return maxDistance + 1;
        }

        let prevRow = Array.from({ length: len2 + 1 }, (_, i) => i);
        let currRow = new Array(len2 + 1);

        for (let i = 1; i <= len1; i++) {
            currRow[0] = i;
            let minInRow = i;

            for (let j = 1; j <= len2; j++) {
                const cost = str1[i - 1] === str2[j - 1] ? 0 : 1;
                currRow[j] = Math.min(
                    prevRow[j] + 1,        // deletion
                    currRow[j - 1] + 1,    // insertion
                    prevRow[j - 1] + cost  // substitution
                );
                minInRow = Math.min(minInRow, currRow[j]);
            }

            if (maxDistance !== undefined && minInRow > maxDistance) {
                return maxDistance + 1;
            }

            [prevRow, currRow] = [currRow, prevRow];
        }

        return prevRow[len2];
    }

    /**
     * Finds where a mapping's target code has moved to.
     * 
     * Search strategy:
     * 1. Nearby lines
     * 2. Context match
     * 3. Proximity
     * 
     * For files >2000 lines, uses sampling.
     * 
     * @param document - Document to search
     * @param mapping - Original mapping with match text and context
     * @returns Line number if found, undefined otherwise
     */
    private findLineDrift(
        document: vscode.TextDocument,
        mapping: BurpMapping
    ): LineDriftResult {
        const searchText = (mapping.matchText || "").trim();
        if (!searchText) return { found: false };

        const originalLine = Math.min(mapping.line - 1, document.lineCount - 1);
        const lineCount = document.lineCount;

        const radius = Math.min(DRIFT.SEARCH_RADIUS_LINES, DRIFT.MAX_FULL_SCAN_LINES / 2);
        const startLine = Math.max(0, originalLine - radius);
        const endLine = Math.min(lineCount - 1, originalLine + radius);

        for (let i = startLine; i <= endLine; i++) {
            if (document.lineAt(i).text.trim().includes(searchText)) {
                return { found: true, line: i };
            }
        }

        const useFullSearch = lineCount <= DRIFT.LARGE_FILE_THRESHOLD;
        const searchIndices = useFullSearch
            ? Array.from({ length: lineCount }, (_, i) => i)
            : this.getSearchIndices(lineCount, originalLine, DRIFT.MAX_FULL_SCAN_ITERATIONS);


        if (mapping.contextBefore || mapping.contextAfter) {
            const normalizedContextBefore = mapping.contextBefore?.trim();
            const normalizedContextAfter = mapping.contextAfter?.trim();

            for (const i of searchIndices) {
                const target = document.lineAt(i).text.trim();
                if (!target.includes(searchText)) continue;

                let contextMatch = true;

                if (normalizedContextBefore && i > 0) {
                    const before = document.lineAt(i - 1).text.trim();
                    if (!before.includes(normalizedContextBefore)) {
                        contextMatch = false;
                    }
                }

                if (normalizedContextAfter && i < lineCount - 1) {
                    const after = document.lineAt(i + 1).text.trim();
                    if (!after.includes(normalizedContextAfter)) {
                        contextMatch = false;
                    }
                }

                if (contextMatch) {
                    return { found: true, line: i };
                }
            }
        }

        const candidates: number[] = [];
        for (let i = 0; i < document.lineCount; i++) {
            if (document.lineAt(i).text.includes(searchText)) {
                candidates.push(i);
            }
        }

        if (candidates.length > 0) {
            const closest = candidates.reduce((prev, curr) =>
                Math.abs(curr - originalLine) < Math.abs(prev - originalLine) ? curr : prev
            );
            return { found: true, line: closest };
        }

        return { found: false };
    }

    /**
     * Gets line indices to search based on file size.
     * Small files: all lines. Large files: evenly-spaced samples.
     * 
     * @param totalLines - Document line count
     * @returns Array of line numbers to check
     */
    private getSearchIndices(
        totalLines: number,
        originalLine: number,
        maxSamples: number
    ): number[] {
        const indices = new Set<number>();

        indices.add(0);
        indices.add(totalLines - 1);

        const nearSamples = Math.floor(maxSamples * 0.6);
        const farSamples = maxSamples - nearSamples;

        const nearRadius = Math.floor(totalLines * 0.2);
        const nearStart = Math.max(0, originalLine - nearRadius);
        const nearEnd = Math.min(totalLines - 1, originalLine + nearRadius);
        const nearStep = Math.max(1, Math.floor((nearEnd - nearStart) / nearSamples));

        for (let i = nearStart; i <= nearEnd; i += nearStep) {
            indices.add(i);
        }

        const farStep = Math.max(1, Math.floor(totalLines / farSamples));
        for (let i = 0; i < totalLines; i += farStep) {
            indices.add(i);
        }

        return Array.from(indices).sort((a, b) => a - b);
    }

    /**
     * Creates diagnostic with appropriate severity based on status.
     * Attaches issue ID as diagnostic code for quick actions.
     * 
     * @param line - Zero-based line number
     * @param mapping - Mapping data with status
     * @returns Configured diagnostic
     */
    private createDiagnostic(line: number, mapping: BurpMapping): vscode.Diagnostic {
        const range = new vscode.Range(line, 0, line, 200);
        const displayName = mapping.issueName || mapping.issueId;
        const message = `[BurpSense] ${displayName}`;

        const diagnostic = new vscode.Diagnostic(
            range,
            message,
            this.mapStatusToSeverity(mapping.status)
        );

        diagnostic.code = mapping.issueId;
        diagnostic.source = DIAGNOSTIC_SOURCE;

        return diagnostic;
    }

    /**
     * Creates warning diagnostic for lost mapping.
     * Shows original match text to help user locate code.
     * 
     * @param line - Original line number (may be wrong now)
     * @param mapping - Original mapping
     * @returns Warning diagnostic with "mapping lost" message
     */
    private createOrphanedDiagnostic(line: number, mapping: BurpMapping): vscode.Diagnostic {
        const range = new vscode.Range(line, 0, line, 200);
        const displayName = mapping.issueName || mapping.issueId;
        const message = `[BurpSense] Mapping lost: ${displayName}. Original text: "${mapping.matchText || 'unknown'}"`;

        const diagnostic = new vscode.Diagnostic(
            range,
            message,
            vscode.DiagnosticSeverity.Warning
        );

        diagnostic.source = DIAGNOSTIC_SOURCE;
        diagnostic.code = mapping.issueId;

        return diagnostic;
    }

    /**
     * Maps BurpSense status to VS Code diagnostic severity.
     * 
     * Status -> Severity:
     * - confirmed -> Error (red squiggle)
     * - remediated -> Info (blue)
     * - false_positive -> Warning (yellow)
     * 
     * @param status - Mapping status
     * @returns VS Code diagnostic severity
     */
    private mapStatusToSeverity(status: string): vscode.DiagnosticSeverity {
        switch (status) {
            case 'confirmed':
                return vscode.DiagnosticSeverity.Error;
            case 'remediated':
                return vscode.DiagnosticSeverity.Information;
            default:
                return vscode.DiagnosticSeverity.Warning;
        }
    }

    /**
     * Shows notification about auto-adjusted mappings.
     * Only notifies once per workspace session unless user opts out.
     * 
     * @param drifts - Array of drift details
     */
    private notifyAboutDrift(drifts: DriftDetail[]): void {
        if (drifts.length === 0) return;

        const config = vscode.workspace.getConfiguration();
        if (!config.get<boolean>(CONFIG_KEYS.SHOW_DRIFT_NOTIFICATIONS, true)) {
            return;
        }

        const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
        const workspaceKey = workspaceFolder?.uri.toString() || 'default';

        if (this.driftNotificationShownPerWorkspace.get(workspaceKey)) {
            return;
        }

        this.driftNotificationShownPerWorkspace.set(workspaceKey, true);

        const message = drifts.length === 1
            ? `Mapping adjusted: ${drifts[0].file} line ${drifts[0].from} → ${drifts[0].to}`
            : `${drifts.length} mappings auto-adjusted to track code changes`;

        vscode.window.showInformationMessage(
            `BurpSense: ${message}`,
            'View Details',
            'Don\'t Show Again'
        ).then(action => {
            if (action === 'View Details') {
                this.showDriftDetails(drifts);
            } else if (action === 'Don\'t Show Again') {
                const config = vscode.workspace.getConfiguration();
                config.update(CONFIG_KEYS.SHOW_DRIFT_NOTIFICATIONS, false, vscode.ConfigurationTarget.Global);
            }
        });
    }

    /**
     * Opens webview panel showing detailed drift corrections.
     * Displays table of files, original lines and new lines.
     * 
     * @param drifts - Drift details to display
     */
    private showDriftDetails(drifts: DriftDetail[]): void {
        const panel = vscode.window.createWebviewPanel(
            'burpsenseDriftDetails',
            'Line Drift Corrections',
            vscode.ViewColumn.Beside,
            {}
        );

        const rows = drifts.map(d =>
            `<tr>
                <td>${d.file}</td>
                <td style="text-align: center">${d.from}</td>
                <td style="text-align: center">→</td>
                <td style="text-align: center">${d.to}</td>
                <td style="text-align: center">${d.to > d.from ? '+' : ''}${d.to - d.from}</td>
            </tr>`
        ).join('');

        panel.webview.html = `<!DOCTYPE html>
<html>
<head>
    <style>
        body {
            font-family: var(--vscode-font-family);
            padding: 20px;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            margin-top: 16px;
        }
        th, td {
            padding: 8px;
            text-align: left;
            border-bottom: 1px solid var(--vscode-panel-border);
        }
        th {
            font-weight: 600;
            color: var(--vscode-foreground);
        }
        .info {
            background: var(--vscode-textCodeBlock-background);
            padding: 12px;
            border-radius: 4px;
            margin-bottom: 16px;
        }
    </style>
</head>
<body>
    <h1>Line Drift Corrections</h1>
    <div class="info">
        BurpSense automatically tracked these mappings as your code changed.
        Mappings have been updated to point to the correct lines.
    </div>
    <table>
        <thead>
            <tr>
                <th>File</th>
                <th>From Line</th>
                <th></th>
                <th>To Line</th>
                <th>Change</th>
            </tr>
        </thead>
        <tbody>
            ${rows}
        </tbody>
    </table>
</body>
</html>`;
    }
}