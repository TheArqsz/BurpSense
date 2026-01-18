import * as path from 'path';
import * as vscode from 'vscode';
import { FILE_PATHS, LIMITS, MESSAGES } from '../constants';
import { BurpMapping, MappingsStore } from '../types';
import { Logger } from './Logger';

/**
 * Thread-safe manager for issue-to-code mappings.
 * Prevents race conditions when multiple sources write simultaneously.
 * 
 * Storage: `.burpsense/mappings.json` in workspace root
 */
export class MappingManager {
    private writeLock: Promise<void> = Promise.resolve();
    private hasLoggedMissingFile: boolean = false;

    /**
     * Acquires write lock for serialized file access.
     * 
     * @returns Release function to call in finally
     */
    private async acquireLock(): Promise<() => void> {
        const currentLock = this.writeLock;
        let releaseLock: () => void;
        this.writeLock = new Promise<void>(resolve => { releaseLock = resolve; });
        await currentLock;
        return releaseLock!;
    }

    /**
     * Saves a single issue mapping to the workspace.
     * 
     * If a mapping for the same issue ID already exists, it will be updated.
     * Otherwise, a new mapping is created. The mapping is immediately persisted
     * to the workspace's .burpsense/mappings.json file.
     * 
     * @param absolutePath - Absolute file system path to the source file
     * @param mapping - Mapping data (without filePath, which is derived from absolutePath)
     * 
     * @returns Promise that resolves when the mapping is saved
     * 
     * @throws Error if the file is not part of the workspace
     * 
     * @example
     * ```typescript
     * await mappingManager.saveMapping('/workspace/src/app.ts', {
     *     issueId: '123',
     *     issueName: 'SQL Injection',
     *     line: 42,
     *     matchText: 'const query = ...',
     *     status: 'confirmed'
     * });
     * ```
     */
    public async saveMapping(
        absolutePath: string,
        mapping: Omit<BurpMapping, 'filePath'>
    ): Promise<void> {
        const release = await this.acquireLock();
        try {
            const uri = this.mappingsUri;
            if (!uri) return;

            const relativePath = this.convertToRelativePath(absolutePath);
            const store = await this.loadMappings();

            const newMapping: BurpMapping = { ...mapping, filePath: relativePath };

            const index = store.mappings.findIndex(m =>
                m.issueId === mapping.issueId &&
                m.filePath === relativePath &&
                m.line === mapping.line
            );

            if (index >= 0) {
                store.mappings[index] = newMapping;
            } else {
                store.mappings.push(newMapping);
            }

            await this.writeStore(uri, store);
        } finally {
            release();
        }
    }

    /**
     * Validates mappings file structure and content.
     * Checks schema, types, and value constraints.
     * 
     * @param data - Parsed JSON data to validate
     * @returns True if data matches MappingsStore schema
     */
    private validateMappingsStore(data: any): data is MappingsStore {
        if (!data || typeof data !== 'object') {
            Logger.error('Mappings: not an object', undefined, 'Mapping');
            return false;
        }

        if (typeof data.version !== 'string' || typeof data.project !== 'string') {
            Logger.error('Mappings: invalid version/project', undefined, 'Mapping');
            return false;
        }

        if (!Array.isArray(data.mappings)) {
            Logger.error('Mappings: not an array', undefined, 'Mapping');
            return false;
        }

        const validStatuses = ['confirmed', 'false_positive', 'remediated'];

        return data.mappings.every((m: any, index: number) => {
            if (!m || typeof m !== 'object') {
                Logger.error(`Mapping ${index}: not an object`, undefined, 'Mapping');
                return false;
            }

            if (typeof m.issueId !== 'string' || m.issueId.length === 0 || m.issueId.length > 100) {
                Logger.error(`Mapping ${index}: invalid issueId`, undefined, 'Mapping');
                return false;
            }

            if (typeof m.filePath !== 'string' || m.filePath.length === 0 || m.filePath.length > 500) {
                Logger.error(`Mapping ${index}: invalid filePath`, undefined, 'Mapping');
                return false;
            }

            if (typeof m.line !== 'number' || m.line < 1 || m.line > 1000000 || !Number.isInteger(m.line)) {
                Logger.error(`Mapping ${index}: invalid line ${m.line}`, undefined, 'Mapping');
                return false;
            }

            if (typeof m.status !== 'string' || !validStatuses.includes(m.status)) {
                Logger.error(`Mapping ${index}: invalid status ${m.status}`, undefined, 'Mapping');
                return false;
            }

            if (m.matchText && (typeof m.matchText !== 'string' || m.matchText.length > LIMITS.MAX_MATCH_TEXT_LENGTH * 2)) {
                Logger.error(`Mapping ${index}: matchText invalid`, undefined, 'Mapping');
                return false;
            }

            return true;
        });
    }

    /**
     * Loads all mappings from workspace storage.
     * Returns default empty store if file doesn't exist or is corrupted.
     * Migrates legacy field names (burpId - issueId) automatically.
     * 
     * @returns Validated mappings store
     */
    public async loadMappings(): Promise<MappingsStore> {
        const uri = this.mappingsUri;
        if (!uri) return this.getDefaultStore();

        try {
            const content = await vscode.workspace.fs.readFile(uri);
            const data = JSON.parse(Buffer.from(content).toString('utf-8'));

            if (!this.validateMappingsStore(data)) {
                Logger.error('Invalid mappings file format, using defaults', undefined, 'Mapping');
                vscode.window.showWarningMessage(
                    'BurpSense: Mappings file is corrupted. Creating new mappings file.'
                );
                return this.getDefaultStore();
            }

            data.mappings = data.mappings.map((m: any) => ({
                ...m,
                issueId: m.issueId || m.burpId
            }));

            return data;
        } catch (error) {
            if (error instanceof vscode.FileSystemError && error.code === 'FileNotFound') {
                if (!this.hasLoggedMissingFile) {
                    Logger.info('Mappings file not found, creating default', 'Mapping');
                    this.hasLoggedMissingFile = true;
                }
            }
            return this.getDefaultStore();
        }
    }

    /**
     * Updates multiple mappings atomically in a single write operation.
     * 
     * Only updates mappings that already exist in the store - mappings with
     * non-existent issue IDs are silently ignored. Only fields that have
     * changed values are updated to minimize unnecessary writes.
     * 
     * @param updates - Array of BurpMapping objects with updated values
     * 
     * @returns Promise that resolves when all updates are persisted to disk
     * 
     * @example
     * ```typescript
     * await mappingManager.updateMappings([
     *     { issueId: '123', filePath: 'src/app.ts', line: 42, ... },
     *     { issueId: '456', filePath: 'src/utils.ts', line: 100, ... }
     * ]);
     * ```
     */
    public async updateMappings(updates: BurpMapping[]): Promise<void> {
        const release = await this.acquireLock();
        try {
            const store = await this.loadMappings();
            let changed = false;

            for (const update of updates) {
                const index = store.mappings.findIndex(m => m.issueId === update.issueId);
                if (index >= 0) {
                    const existing = store.mappings[index];

                    if (existing.line !== update.line) {
                        store.mappings[index].line = update.line;
                        changed = true;
                    }

                    if (update.matchText && existing.matchText !== update.matchText) {
                        store.mappings[index].matchText = update.matchText;
                        changed = true;
                    }

                    if (update.contextBefore && existing.contextBefore !== update.contextBefore) {
                        store.mappings[index].contextBefore = update.contextBefore;
                        changed = true;
                    }

                    if (update.contextAfter && existing.contextAfter !== update.contextAfter) {
                        store.mappings[index].contextAfter = update.contextAfter;
                        changed = true;
                    }
                }
            }

            if (changed) {
                const uri = this.mappingsUri;
                if (uri) await this.writeStore(uri, store);
            }
        } finally {
            release();
        }
    }

    /**
     * Removes a single mapping by issue ID.
     * No-op if mapping doesn't exist.
     * 
     * @param issueId - ID of mapping to remove
     * @param filePath - Optional file path to remove specific mapping
     * @param line - Optional line to remove specific mapping
     */
    public async removeMapping(issueId: string, filePath?: string, line?: number): Promise<void> {
        const release = await this.acquireLock();
        try {
            const store = await this.loadMappings();
            const initialLength = store.mappings.length;

            if (filePath && line) {
                store.mappings = store.mappings.filter(m =>
                    !(m.issueId === issueId && m.filePath === filePath && m.line === line)
                );
            } else {
                store.mappings = store.mappings.filter(m => m.issueId !== issueId);
            }

            if (store.mappings.length !== initialLength) {
                const uri = this.mappingsUri;
                if (uri) {
                    await this.writeStore(uri, store);
                }
            }
        } finally {
            release();
        }
    }

    /**
     * Gets all mappings for a specific issue ID.
     * Used to check how many locations are mapped to the same issue.
     * 
     * @param issueId - The issue ID to search for
     * @returns Array of all mappings for this issue
     */
    public async getMappingsForIssue(issueId: string): Promise<BurpMapping[]> {
        const store = await this.loadMappings();
        return store.mappings.filter(m => m.issueId === issueId);
    }

    /**
     * Removes multiple mappings with progress UI for large batches.
     * Shows progress notification if count exceeds BULK_OPERATION_THRESHOLD.
     * 
     * @param issueIds - Array of issue IDs to remove
     * @returns Number of mappings actually removed
     */
    public async removeMappings(issueIds: string[]): Promise<number> {
        const release = await this.acquireLock();
        try {
            const store = await this.loadMappings();
            const initialLength = store.mappings.length;

            if (issueIds.length > LIMITS.BULK_OPERATION_THRESHOLD) {
                return await vscode.window.withProgress({
                    location: vscode.ProgressLocation.Notification,
                    title: "BurpSense",
                    cancellable: false
                }, async (progress) => {
                    progress.report({ message: MESSAGES.REMOVING_MAPPINGS.replace('%d', String(issueIds.length)) });

                    store.mappings = store.mappings.filter(m => !issueIds.includes(m.issueId));
                    const removedCount = initialLength - store.mappings.length;

                    if (removedCount > 0) {
                        progress.report({ message: 'Saving changes...' });
                        const uri = this.mappingsUri;
                        if (uri) {
                            await this.writeStore(uri, store);
                        }
                    }

                    return removedCount;
                });
            } else {
                store.mappings = store.mappings.filter(m => !issueIds.includes(m.issueId));
                const removedCount = initialLength - store.mappings.length;

                if (removedCount > 0) {
                    const uri = this.mappingsUri;
                    if (uri) {
                        await this.writeStore(uri, store);
                    }
                }

                return removedCount;
            }
        } finally {
            release();
        }
    }

    /**
     * Exports all mappings to a user-selected JSON file.
     * Opens save dialog with default name based on workspace.
     * Shows progress notification during export.
     */
    public async exportMappings(): Promise<void> {
        const release = await this.acquireLock();
        let store: MappingsStore;
        try {
            store = await this.loadMappings();
        } finally {
            release();
        }

        const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
        let defaultUri: vscode.Uri | undefined;

        if (workspaceFolder) {
            defaultUri = vscode.Uri.joinPath(workspaceFolder.uri, FILE_PATHS.EXPORT_DEFAULT_NAME);
        }

        const uri = await vscode.window.showSaveDialog({
            defaultUri: defaultUri,
            filters: { 'JSON Files': ['json'] },
            saveLabel: 'Export Mappings'
        });

        if (uri) {
            await vscode.window.withProgress({
                location: vscode.ProgressLocation.Notification,
                title: "BurpSense",
                cancellable: false
            }, async (progress) => {
                progress.report({
                    message: MESSAGES.EXPORTING_MAPPINGS.replace('%d', String(store.mappings.length))
                });

                const content = Buffer.from(JSON.stringify(store, null, 2), 'utf-8');
                await vscode.workspace.fs.writeFile(uri, content);

                progress.report({ message: 'Export complete!' });
                await new Promise(resolve => setTimeout(resolve, 500));
            });

            vscode.window.showInformationMessage(
                `Exported ${store.mappings.length} mappings to ${uri.fsPath}`
            );
        }
    }

    /**
     * Imports mappings from a JSON file, merging with existing.
     * Skips mappings that already exist (by issue ID).
     * Shows progress and result summary.
     */
    public async importMappings(): Promise<void> {
        const uris = await vscode.window.showOpenDialog({
            canSelectMany: false,
            filters: { 'JSON Files': ['json'] },
            openLabel: 'Import Mappings'
        });

        if (!uris || uris.length === 0) return;

        await vscode.window.withProgress({
            location: vscode.ProgressLocation.Notification,
            title: "BurpSense",
            cancellable: false
        }, async (progress) => {
            try {
                progress.report({ message: MESSAGES.IMPORTING_MAPPINGS });
                const content = await vscode.workspace.fs.readFile(uris[0]);

                progress.report({ message: 'Parsing mappings...' });
                const imported: MappingsStore = JSON.parse(Buffer.from(content).toString('utf-8'));

                if (!imported.mappings || !Array.isArray(imported.mappings)) {
                    throw new Error('Invalid mappings file format');
                }

                progress.report({ message: 'Merging with existing mappings...' });

                const release = await this.acquireLock();
                try {
                    const current = await this.loadMappings();

                    const existingIds = new Set(current.mappings.map(m => m.issueId));
                    const newMappings = imported.mappings.filter(m => !existingIds.has(m.issueId));

                    if (newMappings.length === 0) {
                        vscode.window.showInformationMessage('No new mappings to import (all already exist)');
                        return;
                    }

                    progress.report({ message: `Saving ${newMappings.length} new mappings...` });
                    current.mappings.push(...newMappings);

                    const uri = this.mappingsUri;
                    if (uri) {
                        await this.writeStore(uri, current);
                        progress.report({ message: 'Import complete!' });
                        await new Promise(resolve => setTimeout(resolve, 500));

                        vscode.window.showInformationMessage(`Imported ${newMappings.length} new mappings`);
                    }
                } finally {
                    release();
                }
            } catch (error: any) {
                vscode.window.showErrorMessage(`Failed to import mappings: ${error.message}`);
            }
        });
    }

    private get mappingsUri(): vscode.Uri | undefined {
        const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
        if (!workspaceFolder) return undefined;
        return vscode.Uri.joinPath(
            workspaceFolder.uri,
            FILE_PATHS.BURPSENSE_DIR,
            FILE_PATHS.MAPPINGS_FILE
        );
    }

    private async writeStore(uri: vscode.Uri, store: MappingsStore): Promise<void> {
        const dirUri = vscode.Uri.joinPath(uri, '..');
        try {
            await vscode.workspace.fs.createDirectory(dirUri);
            const content = Buffer.from(JSON.stringify(store, null, 2), 'utf-8');
            await vscode.workspace.fs.writeFile(uri, content);
            this.hasLoggedMissingFile = false;
        } catch (error) {
            vscode.window.showErrorMessage(`BurpSense: Failed to save mappings - ${error}`);
        }
    }

    /**
     * Converts absolute path to workspace-relative path.
     * 
     * @param absolutePath - Full filesystem path
     * @returns Path relative to workspace root
     * @throws Error if path is outside workspace or invalid
     */
    private convertToRelativePath(absolutePath: string): string {
        if (!absolutePath || typeof absolutePath !== 'string') {
            throw new Error('Invalid file path provided');
        }

        const normalized = path.normalize(absolutePath);
        const canonicalPath = path.resolve(normalized);

        const workspace = vscode.workspace.getWorkspaceFolder(vscode.Uri.file(canonicalPath));
        if (!workspace) {
            throw new Error(`Not in workspace: ${canonicalPath}`);
        }

        const workspaceCanonical = path.resolve(workspace.uri.fsPath);

        const isInside = canonicalPath.startsWith(workspaceCanonical + path.sep) ||
            canonicalPath === workspaceCanonical;

        if (!isInside) {
            Logger.error(`Path traversal attempt blocked: ${canonicalPath}`, undefined, 'Security');
            throw new Error(`Security: Path escapes workspace: ${canonicalPath}`);
        }

        const relativePath = path.relative(workspaceCanonical, canonicalPath);

        if (relativePath.startsWith('..') || path.isAbsolute(relativePath)) {
            Logger.error(`Invalid relative path generated: ${relativePath}`, undefined, 'Security');
            throw new Error(`Security: Relative path invalid: ${relativePath}`);
        }

        if (relativePath.includes('\0')) {
            Logger.error(`Null byte detected in path: ${relativePath}`, undefined, 'Security');
            throw new Error('Security: Null byte in path');
        }

        if (process.platform === 'win32') {
            const alternativeSep = path.sep === '\\' ? '/' : '\\';
            if (relativePath.includes(alternativeSep)) {
                Logger.error(`Mixed separators in path: ${relativePath}`, undefined, 'Security');
                throw new Error('Security: Mixed path separators detected');
            }
        }

        return relativePath;
    }

    /**
     * Creates a default empty mappings store.
     * Uses workspace name or "unnamed-project" as project identifier.
     * 
     * @returns Empty mappings store with current workspace name
     */
    private getDefaultStore(): MappingsStore {
        return {
            version: "1.0",
            project: vscode.workspace.name || "unnamed-project",
            mappings: []
        };
    }
}