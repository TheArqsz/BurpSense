import * as vscode from 'vscode';
import { CommandGroupBuilder, CommandRegistry } from './commands/CommandRegistry';
import { ConnectionCommands } from './commands/ConnectionCommands';
import { FilterCommands } from './commands/FilterCommands';
import { IssueCommands } from './commands/IssueCommands';
import { MappingCommands } from './commands/MappingCommands';
import { COMMANDS, CONFIG_KEYS, CONTEXT_KEYS, STORAGE_KEYS, TIMING, TREE_VIEW } from './constants';
import { BurpCodeActionProvider } from './providers/CodeActionProvider';
import { IssueTreeProvider } from './providers/IssueTreeProvider';
import { ConnectionManager } from './services/ConnectionManager';
import { DiagnosticProvider } from './services/DiagnosticProvider';
import { Logger } from './services/Logger';
import { MappingManager } from './services/MappingManager';
import { SmartSuggestionService } from './services/SmartSuggestionService';
import { WelcomePanel } from './ui/WelcomePanel';

/**
 * Extension activation entry point.
 * Called when extension is loaded by VS Code.
 * 
 * @param context - Extension context for subscriptions and storage
 */
export async function activate(context: vscode.ExtensionContext): Promise<void> {
    Logger.initialize();
    Logger.info('BurpSense extension activating...', 'Lifecycle');
    const connectionManager = new ConnectionManager(context);
    const mappingManager = new MappingManager();
    const smartSuggestionService = new SmartSuggestionService();
    const diagnosticProvider = new DiagnosticProvider(context, connectionManager, mappingManager);

    const issueTreeProvider = new IssueTreeProvider(connectionManager, mappingManager);

    connectionManager.setIssueTreeProvider(issueTreeProvider);
    connectionManager.setIssuesUpdatedCallback(async () => {
        issueTreeProvider.refresh();
        await diagnosticProvider.refreshDiagnostics();
    });

    const filterStatusBarItem = vscode.window.createStatusBarItem(
        vscode.StatusBarAlignment.Right,
        99
    );
    filterStatusBarItem.command = COMMANDS.SEARCH_ISSUES;
    context.subscriptions.push(filterStatusBarItem);

    issueTreeProvider.setStatusBarItem(filterStatusBarItem);

    const treeView = vscode.window.createTreeView(TREE_VIEW.ID, {
        treeDataProvider: issueTreeProvider,
        showCollapseAll: true,
        canSelectMany: false
    });
    issueTreeProvider.setTreeView(treeView);

    await connectionManager.checkConnection();

    const connectionCommands = new ConnectionCommands(context, connectionManager);
    const mappingCommands = new MappingCommands(
        context,
        connectionManager,
        mappingManager,
        diagnosticProvider,
        smartSuggestionService,
        issueTreeProvider
    );
    const issueCommands = new IssueCommands(context, connectionManager, issueTreeProvider);
    const filterCommands = new FilterCommands(connectionManager, issueTreeProvider);

    const commandRegistry = new CommandRegistry();
    const commandBuilder = new CommandGroupBuilder();

    const inScopeOnly = vscode.workspace.getConfiguration().get(CONFIG_KEYS.IN_SCOPE_ONLY, true);
    vscode.commands.executeCommand('setContext', CONFIG_KEYS.IN_SCOPE_ONLY, inScopeOnly);

    commandBuilder.group('Connection')
        .add(COMMANDS.CONNECT, () => connectionCommands.connect())
        .add(COMMANDS.DISCONNECT, () => connectionCommands.disconnect())
        .add(COMMANDS.SET_TOKEN, () => connectionCommands.setToken())
        .add(COMMANDS.CHECK_CONNECTION, () => connectionCommands.checkConnection())
        .add(COMMANDS.RETRY_CONNECTION, () => connectionCommands.retryConnection())
        .add(COMMANDS.STATUS_MENU, () => connectionCommands.statusMenu());

    commandBuilder.group('Mappings')
        .add(COMMANDS.MAP_ISSUE_TO_FILE, () => mappingCommands.mapIssueToFile())
        .add(COMMANDS.REMOVE_MAPPING, (arg) => mappingCommands.removeMapping(arg))
        .add(COMMANDS.REMOVE_MAPPING_FROM_TREE, (item) => mappingCommands.removeMappingFromTree(item))
        .add(COMMANDS.BULK_REMOVE_MAPPINGS, () => mappingCommands.bulkRemoveMappings())
        .add(COMMANDS.EXPORT_MAPPINGS, () => mappingCommands.exportMappings())
        .add(COMMANDS.IMPORT_MAPPINGS, () => mappingCommands.importMappings());

    commandBuilder.group('Issues')
        .add(COMMANDS.REFRESH_ISSUES, () => issueCommands.refreshIssues())
        .add(COMMANDS.VIEW_ISSUE_DETAILS, (issue) => issueCommands.viewIssueDetails(issue))
        .add(COMMANDS.OPEN_DIAGNOSTIC_ADVISORY, (issueId) => issueCommands.openDiagnosticAdvisory(issueId));

    commandBuilder.group('Filters')
        .add(COMMANDS.TOGGLE_SCOPE_ON, () => filterCommands.toggleScope())
        .add(COMMANDS.TOGGLE_SCOPE_OFF, () => filterCommands.toggleScope())
        .add(COMMANDS.SELECT_MIN_SEVERITY, () => filterCommands.selectMinSeverity())
        .add(COMMANDS.SELECT_MIN_CONFIDENCE, () => filterCommands.selectMinConfidence())
        .add(COMMANDS.FILTER_PREVIEW, () => filterCommands.filterPreview())
        .add(COMMANDS.SEARCH_ISSUES, () => filterCommands.searchIssues())
        .add(COMMANDS.CLEAR_SEARCH_FILTER, () => filterCommands.clearSearchFilter());

    commandBuilder.group('System')
        .add(COMMANDS.SHOW_LOGS, () => Logger.show());

    commandBuilder.registerAll(commandRegistry);

    registerEventHandlers(context, connectionManager, mappingManager, diagnosticProvider, issueTreeProvider);

    const codeActionProvider = vscode.languages.registerCodeActionsProvider(
        { scheme: 'file' },
        new BurpCodeActionProvider(),
        { providedCodeActionKinds: BurpCodeActionProvider.providedCodeActionKinds }
    );

    const hasSeenWelcome = context.globalState.get(STORAGE_KEYS.HAS_SEEN_WELCOME);
    if (!hasSeenWelcome) {
        WelcomePanel.show(context);
        context.globalState.update(STORAGE_KEYS.HAS_SEEN_WELCOME, true);
    }

    vscode.commands.executeCommand('setContext', CONTEXT_KEYS.HAS_FILTER, false);

    context.subscriptions.push(
        connectionManager,
        issueTreeProvider,
        treeView,
        codeActionProvider,
        ...commandRegistry.getDisposables(),
        { dispose: () => Logger.info('Extension deactivated', 'Lifecycle') }
    );

    diagnosticProvider.refreshDiagnostics();
}

/**
 * Registers all workspace event handlers.
 * Handles file deletions, config changes and document saves.
 * 
 * @param context - Extension context
 * @param connectionManager - Connection manager instance
 * @param mappingManager - Mapping manager instance
 * @param diagnosticProvider - Diagnostic provider instance
 * @param issueTreeProvider - Tree provider instance
 */
function registerEventHandlers(
    context: vscode.ExtensionContext,
    connectionManager: ConnectionManager,
    mappingManager: MappingManager,
    diagnosticProvider: DiagnosticProvider,
    issueTreeProvider: IssueTreeProvider
): void {
    const fileWatcher = vscode.workspace.createFileSystemWatcher('**/*');
    fileWatcher.onDidDelete(async (uri) => {
        await handleFileDeleted(uri, mappingManager, diagnosticProvider);
    });
    context.subscriptions.push(fileWatcher);

    const configChangeHandler = vscode.workspace.onDidChangeConfiguration(async e => {
        if (e.affectsConfiguration(CONFIG_KEYS.IN_SCOPE_ONLY) ||
            e.affectsConfiguration(CONFIG_KEYS.MIN_SEVERITY) ||
            e.affectsConfiguration(CONFIG_KEYS.MIN_CONFIDENCE)) {

            if (e.affectsConfiguration(CONFIG_KEYS.IN_SCOPE_ONLY)) {
                const newState = vscode.workspace.getConfiguration().get(CONFIG_KEYS.IN_SCOPE_ONLY, true);
                await vscode.commands.executeCommand('setContext', 'burpsense.inScopeOnly', newState);
            }

            issueTreeProvider.refresh();
        }
    });
    context.subscriptions.push(configChangeHandler);

    const workspaceChangeHandler = vscode.workspace.onDidChangeWorkspaceFolders(() => {
        if (connectionManager.isConnected) {
            connectionManager.checkConnection();
        }
    });
    context.subscriptions.push(workspaceChangeHandler);

    let saveDebounceTimer: NodeJS.Timeout | undefined;
    const documentSaveHandler = vscode.workspace.onDidSaveTextDocument((_document) => {
        if (saveDebounceTimer) {
            clearTimeout(saveDebounceTimer);
        }

        saveDebounceTimer = setTimeout(() => {
            diagnosticProvider.refreshDiagnostics();
            saveDebounceTimer = undefined;
        }, TIMING.SAVE_DEBOUNCE_MS);
    });
    context.subscriptions.push(documentSaveHandler);

    const documentOpenHandler = vscode.workspace.onDidOpenTextDocument(async (document) => {
        const store = await mappingManager.loadMappings();
        const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
        if (!workspaceFolder) return;

        const relativePath = vscode.workspace.asRelativePath(document.uri);
        const hasMappings = store.mappings.some(m => m.filePath === relativePath);

        if (hasMappings) {
            await diagnosticProvider.refreshDiagnostics();
        }
    });
    context.subscriptions.push(documentOpenHandler);

    context.subscriptions.push({
        dispose: () => {
            if (saveDebounceTimer) {
                clearTimeout(saveDebounceTimer);
            }
        }
    });
}

/**
 * Handles file deletion and orphaned mappings cleanup.
 * Prompts user to remove associated mappings or auto-removes if configured.
 * 
 * @param uri - URI of deleted file
 * @param mappingManager - Mapping manager
 * @param diagnosticProvider - Diagnostic provider
 */
async function handleFileDeleted(
    uri: vscode.Uri,
    mappingManager: MappingManager,
    diagnosticProvider: DiagnosticProvider
): Promise<void> {
    const store = await mappingManager.loadMappings();
    const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
    if (!workspaceFolder) return;

    const relativePath = vscode.workspace.asRelativePath(uri);
    const orphanedMappings = store.mappings.filter(m => m.filePath === relativePath);

    if (orphanedMappings.length > 0) {
        const config = vscode.workspace.getConfiguration();
        const autoClean = config.get<boolean>(CONFIG_KEYS.AUTO_CLEAN_ORPHANED_MAPPINGS, false);

        if (autoClean) {
            const ids = orphanedMappings.map(m => m.issueId);
            await mappingManager.removeMappings(ids);
            await diagnosticProvider.refreshDiagnostics();
            vscode.window.showInformationMessage(
                `Removed ${ids.length} mapping(s) from deleted file: ${relativePath}`
            );
        } else {
            const fileName = relativePath.split('/').pop() || relativePath;
            const action = await vscode.window.showWarningMessage(
                `File "${fileName}" was deleted. Remove ${orphanedMappings.length} associated mapping(s)?`,
                { modal: true },
                'Remove Mappings',
                'Keep Mappings',
                'Don\'t Ask Again'
            );

            if (action === 'Remove Mappings') {
                const ids = orphanedMappings.map(m => m.issueId);
                await mappingManager.removeMappings(ids);
                await diagnosticProvider.refreshDiagnostics();
                vscode.window.showInformationMessage(`Removed ${ids.length} mapping(s)`);
            } else if (action === 'Don\'t Ask Again') {
                await config.update(
                    CONFIG_KEYS.AUTO_CLEAN_ORPHANED_MAPPINGS,
                    true,
                    vscode.ConfigurationTarget.Global
                );
                vscode.window.showInformationMessage(
                    'Orphaned mappings will be automatically removed in the future. ' +
                    'You can change this in Settings > BurpSense > Auto Clean Orphaned Mappings'
                );
            }
        }
    }
}

/**
 * Extension deactivation cleanup.
 * Called when extension is unloaded.
 */
export function deactivate(): void {
    try {
        Logger.info('Cleaning up Logger...', 'Lifecycle');
        Logger.dispose();

        vscode.commands.executeCommand('setContext', CONTEXT_KEYS.HAS_FILTER, false);
        vscode.commands.executeCommand('setContext', CONFIG_KEYS.IN_SCOPE_ONLY, false);
    } catch (error) {
        console.error('Error during deactivation:', error);
    }
}