import * as vscode from 'vscode';
import { COMMANDS, CONFIG_SECTION, EXTENSION_ID, MESSAGES, WALKTHROUGH } from '../constants';
import { ConnectionManager } from '../services/ConnectionManager';
import { WelcomePanel } from '../ui/WelcomePanel';

/**
 * Handlers for connection-related commands
 */
export class ConnectionCommands {
    constructor(
        private readonly context: vscode.ExtensionContext,
        private readonly connectionManager: ConnectionManager
    ) { }

    /**
     * Command: Set API Token
     */
    public async setToken(): Promise<void> {
        const token = await vscode.window.showInputBox({
            prompt: "Enter BurpSense Bridge API Token",
            password: true,
            ignoreFocusOut: true
        });

        if (token) {
            await this.context.secrets.store('bridgeToken', token);
            await vscode.window.withProgress({
                location: vscode.ProgressLocation.Notification,
                title: "BurpSense",
                cancellable: false
            }, async (progress) => {
                progress.report({ message: "Verifying connection..." });
                this.connectionManager.resetConnection();
            });
        }
    }

    /**
     * Command: Check Connection
     */
    public async checkConnection(): Promise<void> {
        await vscode.window.withProgress({
            location: vscode.ProgressLocation.Notification,
            title: MESSAGES.CHECKING_CONNECTION,
            cancellable: false
        }, async () => {
            await this.connectionManager.checkConnection();
        });
    }

    /**
     * Command: Connect to Bridge
     */
    public async connect(): Promise<void> {
        this.connectionManager.connect();
    }

    /**
     * Command: Disconnect from Bridge
     */
    public async disconnect(): Promise<void> {
        this.connectionManager.disconnect();
    }

    /**
     * Command: Retry Connection
     */
    public async retryConnection(): Promise<void> {
        this.connectionManager.resetConnection();
        vscode.window.showInformationMessage('Retrying BurpSense connection...');
    }

    /**
     * Command: Status Menu (Quick Actions)
     */
    public async statusMenu(): Promise<void> {
        const isManuallyDisconnected = this.connectionManager.isManuallyConnected;
        const isConnected = this.connectionManager.isConnected;

        let actions: any[] = [];

        if (isManuallyDisconnected) {
            actions = [
                { label: '$(plug) Connect to Bridge', action: 'connect' },
                { label: '$(gear) Open Settings', action: 'settings' },
                { label: '$(book) Open Guide', action: 'guide' },
                { label: '$(question) Help', action: 'help' }
            ];
        } else if (isConnected) {
            actions = [
                { label: '$(refresh) Refresh Issues', action: 'refresh' },
                { label: '$(debug-disconnect) Disconnect', action: 'disconnect' },
                { label: '$(filter) Adjust Filters', action: 'filters' },
                { label: '$(output) Show Logs', action: 'logs' },
                { label: '$(gear) Open Settings', action: 'settings' }
            ];
        } else {
            actions = [
                { label: '$(sync) Retry Connection', action: 'retry' },
                { label: '$(debug-disconnect) Stop Reconnecting (Work offline)', action: 'disconnect' },
                { label: '$(output) Show Logs', action: 'logs' },
                { label: '$(gear) Check Settings', action: 'settings' },
                { label: '$(question) Help', action: 'help' }
            ];
        }

        const selected = await vscode.window.showQuickPick(actions, {
            placeHolder: 'BurpSense Quick Actions'
        });

        if (selected) {
            switch (selected.action) {
                case 'connect':
                    await this.connect();
                    break;
                case 'disconnect':
                    await this.disconnect();
                    break;
                case 'retry':
                    await this.retryConnection();
                    break;
                case 'refresh':
                    await vscode.commands.executeCommand(COMMANDS.REFRESH_ISSUES);
                    break;
                case 'filters':
                    await vscode.commands.executeCommand(COMMANDS.FILTER_PREVIEW);
                    break;
                case 'logs':
                    await vscode.commands.executeCommand(COMMANDS.SHOW_LOGS);
                    break;
                case 'settings':
                    await vscode.commands.executeCommand('workbench.action.openSettings', CONFIG_SECTION);
                    break;
                case 'guide':
                    await vscode.commands.executeCommand('workbench.action.openWalkthrough', `${EXTENSION_ID}#${WALKTHROUGH.ID}`);
                    break;
                case 'help':
                    WelcomePanel.show(this.context);
                    break;
            }
        }
    }
}