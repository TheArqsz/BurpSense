import * as vscode from 'vscode';
import { WebSocket } from 'ws';
import { BUTTONS, COMMANDS, CONFIDENCE_LEVELS, CONFIG_KEYS, CONFIG_SECTION, CONNECTION, CONTEXT_KEYS, MESSAGES, SEVERITY_LEVELS, STATUS_BAR } from '../constants';
import { IssueTreeProvider } from '../providers/IssueTreeProvider';
import { BurpIssue, IncrementalIssuesResponse } from '../types';
import { Logger } from './Logger';

/**
 * Manages connection to the BurpSense Bridge server
 * Implements exponential backoff and circuit breaker.
 */
export class ConnectionManager {
    private ws?: WebSocket;
    private knownIssueIds: Set<string> = new Set();

    private readonly statusBarItem: vscode.StatusBarItem;
    private _isConnected: boolean = false;
    private _manuallyDisconnected: boolean = false;

    private pollInterval?: NodeJS.Timeout;
    private _issueCount: number = 0;
    private onIssuesUpdated?: () => void;

    private retryCount: number = 0;
    private circuitBreakerOpen: boolean = false;
    private lastFailureTime?: number;

    private issueTreeProvider?: IssueTreeProvider;

    constructor(private context: vscode.ExtensionContext) {
        this.statusBarItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 100);
        this.statusBarItem.command = COMMANDS.STATUS_MENU;
        this.context.subscriptions.push(this.statusBarItem);

        this.updateStatus(false);
        this.startPolling();
    }

    /**
     * Current connection state.
     * Updated after each health check.
     */
    public get isConnected(): boolean {
        return this._isConnected;
    }

    /**
     * Current manual connection state.
     */
    public get isManuallyConnected(): boolean {
        return this._manuallyDisconnected;
    }

    /**
     * Number of issues fetched in last successful request.
     * Used for status bar display.
     */
    public get issueCount(): number {
        return this._issueCount;
    }

    /**
     * Cleans up connection resources.
     * Stops polling and hides status bar.
     */
    public dispose(): void {
        this.stopPolling();

        if (this.ws) {
            try {
                this.ws.removeAllListeners();
                if (this.ws.readyState === WebSocket.OPEN ||
                    this.ws.readyState === WebSocket.CONNECTING) {
                    this.ws.close();
                }
                this.ws = undefined;
                Logger.info('WebSocket closed', 'Lifecycle');
            } catch (error) {
                Logger.error('Error closing WebSocket', error, 'Lifecycle');
            }
        }

        try {
            this.statusBarItem.dispose();
        } catch (error) {
            Logger.error('Error disposing status bar', error, 'Lifecycle');
        }

        this.knownIssueIds.clear();
        this._isConnected = false;
        Logger.info('ConnectionManager disposed', 'Lifecycle');
    }

    /**
     * Sets the issue tree provider for direct cache updates.
     * Called during extension initialization.
     */
    public setIssueTreeProvider(provider: IssueTreeProvider): void {
        this.issueTreeProvider = provider;
    }

    private initWebSocket(token: string) {
        if (this.ws) return;

        const config = vscode.workspace.getConfiguration();
        const ip = config.get(CONFIG_KEYS.BRIDGE_IP);
        const port = config.get(CONFIG_KEYS.BRIDGE_PORT);
        const url = `ws://${ip}:${port}/ws`;

        this.ws = new WebSocket(url, {
            headers: { 'Authorization': `Bearer ${token}` }
        });

        this.ws.on('message', (data) => {
            if (data.toString() === 'refresh') {
                Logger.info('Refresh signal received', 'Sync');
                this.refreshIssuesFromWebSocket();
            }
        });

        this.ws.on('close', () => {
            this.ws = undefined;
            setTimeout(() => this.checkConnection(), CONNECTION.WS_RECONNECT_DELAY_MS);
        });
    }

    /**
     * Tests connection to bridge and fetches issue count.
     * Updates status bar and triggers callback if connected.
     * Implements circuit breaker pattern.
     */
    public async checkConnection(): Promise<void> {
        if (this.circuitBreakerOpen) {
            const now = Date.now();
            const timeSinceLastFailure = now - (this.lastFailureTime || 0);

            if (timeSinceLastFailure < CONNECTION.MAX_RETRY_DELAY_MS) {
                Logger.info('Circuit breaker open, skipping connection attempt', 'Connection');
                return;
            }

            Logger.info('Attempting to close circuit breaker after cooldown period', 'Connection');
            this.circuitBreakerOpen = false;
            this.retryCount = 0;
        }

        const token = await this.context.secrets.get('bridgeToken');

        if (!token) {
            this.openCircuitBreaker();
            this.showNoTokenError();
            return;
        }

        this.statusBarItem.text = STATUS_BAR.CONNECTING;
        this.statusBarItem.backgroundColor = new vscode.ThemeColor('statusBarItem.warningBackground');

        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), CONNECTION.TIMEOUT_MS);

        try {
            const response = await fetch(this.getHealthUrl(), {
                method: 'GET',
                headers: { 'Authorization': `Bearer ${token}` },
                signal: controller.signal as AbortSignal
            });

            if (response.ok) {
                if (!this.ws) { this.initWebSocket(token); }

                this.lastFailureTime = undefined;
                this.retryCount = 0;
                this.circuitBreakerOpen = false;

                if (this.knownIssueIds.size === 0) {
                    const issues = await this.getAllIssues();
                    if (this.issueTreeProvider) {
                        this.issueTreeProvider.updateCache(issues, false);
                    }
                    this._issueCount = issues.length;
                } else if (this.issueTreeProvider) {
                    this._issueCount = this.issueTreeProvider.getCachedIssues().length || this.knownIssueIds.size;
                }

                this.updateStatus(true);

                if (this.onIssuesUpdated) {
                    this.onIssuesUpdated();
                }
            } else if (response.status === 401) {
                this.updateStatus(false);
                this.openCircuitBreaker();
                this.showAuthError();
            } else {
                this.updateStatus(false);
                this.handleFailedConnection();
                this.showServerError(response.status);
            }
        } catch (error: any) {
            this.updateStatus(false);
            this.handleFailedConnection();
            this.handleConnectionError(error);
        } finally {
            clearTimeout(timeout);
        }
    }

    /**
     * Manually connects to bridge.
     * Clears manual disconnect flag and starts polling.
     */
    public connect(): void {
        if (!this._manuallyDisconnected && this._isConnected) {
            Logger.info('Already connected', 'Connection');
            return;
        }

        this._manuallyDisconnected = false;
        this.retryCount = 0;
        this.circuitBreakerOpen = false;

        Logger.info('User initiated connection', 'Connection');

        this.startPolling();
        this.checkConnection();
    }

    /**
     * Manually disconnects from bridge.
     * Stops polling and sets manual disconnect flag.
     */
    public disconnect(): void {
        if (this._manuallyDisconnected) {
            Logger.info('Already disconnected', 'Connection');
            return;
        }

        this._manuallyDisconnected = true;
        this.stopPolling();
        this.updateStatus(false);

        Logger.info('User manually disconnected', 'Connection');

        vscode.window.showInformationMessage(
            'Disconnected from BurpSense Bridge. Click status bar to reconnect.'
        );
    }

    /**
     * Fetches issues from the BurpSense Bridge server.
     * 
     * Applies current filter settings (severity, confidence, scope) from workspace
     * configuration and returns only issues that match the active filters.
     * 
     * @returns Promise resolving to an array of BurpIssue objects. Returns empty
     *          array if no token is configured, connection fails, or no issues match filters.
     * 
     * @example
     * ```typescript
     * const issues = await connectionManager.getIssues();
     * console.log(`Found ${issues.length} issues matching filters`);
     * ```
     */
    public async getIssues(): Promise<BurpIssue[]> {
        if (this._manuallyDisconnected) {
            Logger.info('Cannot fetch issue - manually disconnected', 'Connection');
            return [];
        }

        const token = await this.context.secrets.get('bridgeToken');
        if (!token) {
            Logger.error('Attempted to fetch issues without token', undefined, 'Connection');
            return [];
        }
        const config = vscode.workspace.getConfiguration();
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), CONNECTION.TIMEOUT_MS);

        try {
            const params: Record<string, string | boolean> = {
                minSeverity: config.get<string>(CONFIG_KEYS.MIN_SEVERITY) || SEVERITY_LEVELS[SEVERITY_LEVELS.length - 1],
                minConfidence: config.get<string>(CONFIG_KEYS.MIN_CONFIDENCE) || CONFIDENCE_LEVELS[CONFIDENCE_LEVELS.length - 1],
                inScope: config.get<boolean>(CONFIG_KEYS.IN_SCOPE_ONLY) ?? true
            };

            const url = this.buildApiUrl('issues', params);

            const response = await fetch(url, {
                method: 'POST',
                headers: { 'Authorization': `Bearer ${token}` },
                signal: controller.signal as AbortSignal,
                body: JSON.stringify({
                    knownIds: Array.from(this.knownIssueIds)
                })
            });

            if (response.ok) {
                const data = await response.json() as IncrementalIssuesResponse;

                data.newIssues.forEach(i => this.knownIssueIds.add(i.id));
                data.removedIds.forEach(id => this.knownIssueIds.delete(id));

                if (data.newIssues.length > 0 || data.removedIds.length > 0) {
                    Logger.info(
                        `Incremental update: +${data.newIssues.length} new, -${data.removedIds.length} removed`,
                        'Sync'
                    );
                }

                if (this.issueTreeProvider && data.removedIds.length > 0) {
                    this.issueTreeProvider.removeIssues(data.removedIds);
                }

                return data.newIssues;
            } else {
                Logger.error(`Failed to fetch issues: HTTP ${response.status}`, undefined, 'Connection');
                if (response.status >= 500) {
                    vscode.window.showWarningMessage('BurpSense: Bridge server error. Issues may be stale.');
                }
            }
        } catch (error: any) {
            if (error.name === 'AbortError') {
                Logger.error('Issue fetch timed out', undefined, 'Connection');
                vscode.window.showWarningMessage('BurpSense: Request to the bridge timed out');
            } else {
                Logger.error("Failed to fetch issues", error, 'Connection');
                if (error instanceof Error && error.message.includes('ECONNREFUSED')) {
                    Logger.info('Connection refused when fetching issues', 'Connection');
                }
            }
        } finally {
            clearTimeout(timeout);
        }
        return [];
    }

    /**
     * Fetches all issues, ignoring incremental update tracking.
     * Used for full tree refreshes.
     */
    public async getAllIssues(): Promise<BurpIssue[]> {
        if (this._manuallyDisconnected) {
            Logger.info('Cannot fetch all issues - manually disconnected', 'Connection');
            return [];
        }

        const token = await this.context.secrets.get('bridgeToken');
        if (!token) {
            Logger.error('Attempted to fetch issues without token', undefined, 'Connection');
            return [];
        }

        const config = vscode.workspace.getConfiguration();
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), CONNECTION.TIMEOUT_MS);

        try {
            const params: Record<string, string | boolean> = {
                minSeverity: config.get<string>(CONFIG_KEYS.MIN_SEVERITY) || SEVERITY_LEVELS[SEVERITY_LEVELS.length - 1],
                minConfidence: config.get<string>(CONFIG_KEYS.MIN_CONFIDENCE) || CONFIDENCE_LEVELS[CONFIDENCE_LEVELS.length - 1],
                inScope: config.get<boolean>(CONFIG_KEYS.IN_SCOPE_ONLY) ?? true
            };

            const url = this.buildApiUrl('issues', params);

            const response = await fetch(url, {
                method: 'GET',
                headers: { 'Authorization': `Bearer ${token}` },
                signal: controller.signal as AbortSignal,
            });

            if (response.ok) {
                const data = await response.json() as IncrementalIssuesResponse;
                const allIssues = data.newIssues;
                this.knownIssueIds.clear();
                allIssues.forEach(i => this.knownIssueIds.add(i.id));
                this._issueCount = allIssues.length;
                Logger.info(`Fetched all ${allIssues.length} issues (full refresh)`, 'Connection');
                return allIssues;
            } else {
                Logger.error(`Failed to fetch issues: HTTP ${response.status}`, undefined, 'Connection');
            }
        } catch (error: any) {
            Logger.error("Failed to fetch all issues", error, 'Connection');
        } finally {
            clearTimeout(timeout);
        }
        return [];
    }

    /**
 * Fetches a single issue by ID from the bridge.
 * Uses RESTful path parameter: GET /issues/{id}
 * 
 * Much more efficient than fetching all issues when you only need one.
 * 
 * @param issueId - The issue ID to fetch
 * @returns Promise resolving to the issue, or null if not found
 */
    public async getIssueById(issueId: string): Promise<BurpIssue | null> {
        if (this._manuallyDisconnected) {
            Logger.info('Cannot fetch issue - manually disconnected', 'Connection');
            return null;
        }

        const token = await this.context.secrets.get('bridgeToken');
        if (!token) {
            Logger.error('Attempted to fetch issue without token', undefined, 'Connection');
            return null;
        }

        const config = vscode.workspace.getConfiguration();
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), CONNECTION.TIMEOUT_MS);

        try {
            const ip = config.get(CONFIG_KEYS.BRIDGE_IP);
            const port = config.get(CONFIG_KEYS.BRIDGE_PORT);
            const baseUrl = `http://${ip}:${port}`;
            const url = `${baseUrl}/issues/${encodeURIComponent(issueId)}`;

            const response = await fetch(url, {
                method: 'GET',
                headers: { 'Authorization': `Bearer ${token}` },
                signal: controller.signal as AbortSignal
            });

            if (response.ok) {
                const issue = await response.json() as BurpIssue;
                Logger.info(`Fetched single issue: ${issueId.substring(0, 8)}...`, 'Connection');
                return issue;
            } else if (response.status === 404) {
                Logger.warn(`Issue not found: ${issueId.substring(0, 8)}...`, 'Connection');
                return null;
            } else {
                Logger.error(`Failed to fetch issue: HTTP ${response.status}`, undefined, 'Connection');
                return null;
            }
        } catch (error: any) {
            if (error.name === 'AbortError') {
                Logger.error('Single issue fetch timed out', undefined, 'Connection');
            } else {
                Logger.error('Failed to fetch single issue', error, 'Connection');
            }
            return null;
        } finally {
            clearTimeout(timeout);
        }
    }

    /**
     * Registers callback for when issues are refreshed.
     * 
     * @param callback - Function to call after successful issue fetch
     */
    public setIssuesUpdatedCallback(callback: () => void): void {
        this.onIssuesUpdated = callback;
    }

    /**
     * Resets connection state and restarts polling.
     * Clears circuit breaker and retry count.
     */
    public resetConnection(): void {
        this.retryCount = 0;
        this.circuitBreakerOpen = false;
        this.startPolling();
        this.checkConnection();
    }

    /**
     * Increments retry count and opens circuit breaker if threshold exceeded.
     */
    private handleFailedConnection(): void {
        this.retryCount++;
        this.lastFailureTime = Date.now();

        if (this.retryCount >= CONNECTION.MAX_RETRY_COUNT) {
            this.openCircuitBreaker();
            this.showMaxRetriesError();
        }
    }

    /**
     * Opens circuit breaker to stop hammering failed connection.
     * Automatically closes after cooldown period.
     */
    private openCircuitBreaker(): void {
        this.circuitBreakerOpen = true;
        this.updateStatus(false);

        if (this.pollInterval) {
            clearInterval(this.pollInterval);
            this.pollInterval = undefined;
        }

        this.scheduleNextPoll();
        Logger.warn(`Circuit breaker open, retrying every ${CONNECTION.MAX_RETRY_DELAY_MS / 1000}s`, 'Connection');
    }

    private async showMaxRetriesError(): Promise<void> {
        const action = await vscode.window.showErrorMessage(
            MESSAGES.MAX_RETRIES,
            {
                modal: false,
                detail:
                    `Connection failed ${CONNECTION.MAX_RETRY_COUNT} times.\n\n` +
                    'Automatic reconnection has been disabled to save resources.\n\n' +
                    'Please:\n' +
                    '1. Verify Burp Suite is running\n' +
                    '2. Check the bridge server is started\n' +
                    '3. Confirm network settings are correct\n\n' +
                    'Click "Retry Now" to attempt reconnection.'
            },
            BUTTONS.RETRY_NOW,
            BUTTONS.OPEN_SETTINGS
        );

        if (action === BUTTONS.RETRY_NOW) {
            this.resetConnection();
        } else if (action === BUTTONS.OPEN_SETTINGS) {
            await vscode.commands.executeCommand('workbench.action.openSettings', CONFIG_SECTION);
        }
    }

    /**
     * Builds URL for health check endpoint.
     * 
     * @returns Full URL to /health endpoint
     */
    private getHealthUrl(): string {
        return this.buildApiUrl('health', {});
    }

    /**
     * Builds URL with query parameters from config.
     * 
     * @param endpoint - API endpoint (without leading slash)
     * @param params - Query parameters to append
     * @returns Full URL with query string
     */
    private buildApiUrl(endpoint: string, params: Record<string, string | boolean>): string {
        const config = vscode.workspace.getConfiguration();
        const ip = config.get<string>(CONFIG_KEYS.BRIDGE_IP) || CONNECTION.DEFAULT_IP;
        const port = config.get<number>(CONFIG_KEYS.BRIDGE_PORT) || CONNECTION.DEFAULT_PORT;

        const url = new URL(`http://${ip}:${port}/${endpoint}`);
        Object.entries(params).forEach(([key, value]) => {
            url.searchParams.append(key, String(value));
        });

        return url.toString();
    }

    /**
     * Starts automatic connection polling.
     * Uses exponential backoff when disconnected.
     */
    private startPolling(): void {
        this.stopPolling();
        this.scheduleNextPoll();
    }

    /**
     * Schedules next poll based on connection state.
     * Interval increases with retry count (exponential backoff).
     */
    private scheduleNextPoll(): void {
        if (this.pollInterval) {
            return;
        }

        if (this._manuallyDisconnected) {
            Logger.info('Skipping poll - manually disconnected', 'Connection');
            return;
        }

        const interval = this.getPollingInterval();
        this.pollInterval = setTimeout(async () => {
            this.pollInterval = undefined;

            if (this._manuallyDisconnected) {
                return;
            }

            try {
                await this.checkConnection();
            } catch (error) {
                Logger.error('Error during polling check', error, 'Connection');
            } finally {
                this.scheduleNextPoll();
            }
        }, interval);
    }

    /**
     * Gets polling interval based on connection state.
     * Implements exponential backoff when disconnected.
     * 
     * @returns Milliseconds until next poll
     */
    private getPollingInterval(): number {
        if (this._isConnected) {
            this.retryCount = 0;
            return CONNECTION.POLLING_INTERVAL_MS;
        }

        if (this.circuitBreakerOpen) {
            return CONNECTION.MAX_RETRY_DELAY_MS;
        }

        const delay = Math.min(
            CONNECTION.BASE_RETRY_DELAY_MS * Math.pow(2, this.retryCount),
            CONNECTION.MAX_RETRY_DELAY_MS
        );

        return delay;
    }

    /**
     * Stops automatic polling.
     */
    private stopPolling(): void {
        if (this.pollInterval) {
            clearInterval(this.pollInterval);
            this.pollInterval = undefined;
        }
    }

    /**
     * Updates status bar display and context key.
     * 
     * @param connected - Current connection state
     */
    public updateStatus(connected: boolean): void {
        this._isConnected = connected;
        vscode.commands.executeCommand('setContext', CONTEXT_KEYS.CONNECTED, connected);

        if (connected) {
            const statusText = STATUS_BAR.CONNECTED_FORMAT.replace('%d', String(this._issueCount));
            this.statusBarItem.text = statusText;
            this.statusBarItem.tooltip = STATUS_BAR.TOOLTIP_FORMAT
                .replace('%s', this.buildApiUrl('', {}))
                .replace('%d', String(this._issueCount));
            this.statusBarItem.backgroundColor = new vscode.ThemeColor('statusBarItem.remoteBackground');
        } else if (this._manuallyDisconnected) {
            this.statusBarItem.text = STATUS_BAR.OFFLINE;
            this.statusBarItem.tooltip = 'Manually disconnected. Click to reconnect.';
            this.statusBarItem.backgroundColor = new vscode.ThemeColor('statusBarItem.warningBackground');
        } else {
            this.statusBarItem.text = STATUS_BAR.DISCONNECTED;
            this.statusBarItem.tooltip = STATUS_BAR.TOOLTIP_DISCONNECTED;
            this.statusBarItem.backgroundColor = new vscode.ThemeColor('statusBarItem.errorBackground');
        }

        this.statusBarItem.show();
    }

    private handleConnectionError(error: any): void {
        const healthUrl = this.getHealthUrl();

        if (error.name === 'AbortError') {
            this.showTimeoutError(healthUrl);
        } else if (error.code === 'ECONNREFUSED') {
            this.showConnectionRefusedError(healthUrl);
        } else if (error.code === 'ENOTFOUND') {
            this.showInvalidHostError(healthUrl);
        } else {
            this.showGenericError(error.message);
        }
    }

    private async showConnectionError(
        message: string,
        options: {
            detail?: string;
            buttons: Array<{
                label: string;
                action: () => Thenable<void> | Promise<void> | void;
            }>;
        }
    ): Promise<void> {
        const buttonLabels = options.buttons.map(b => b.label);

        const action = options.detail
            ? await vscode.window.showErrorMessage(
                message,
                { modal: false, detail: options.detail },
                ...buttonLabels
            )
            : await vscode.window.showErrorMessage(message, ...buttonLabels);

        const selectedButton = options.buttons.find(b => b.label === action);
        if (selectedButton) {
            await selectedButton.action();
        }
    }

    private async showNoTokenError(): Promise<void> {
        await this.showConnectionError(MESSAGES.NO_TOKEN, {
            buttons: [
                {
                    label: BUTTONS.SET_TOKEN,
                    action: () => vscode.commands.executeCommand(COMMANDS.SET_TOKEN)
                },
                {
                    label: BUTTONS.HELP,
                    action: () => this.showTroubleshootingGuide()
                }
            ]
        });
    }

    private async showAuthError(): Promise<void> {
        await this.showConnectionError(MESSAGES.AUTH_FAILED, {
            buttons: [
                {
                    label: BUTTONS.UPDATE_TOKEN,
                    action: () => vscode.commands.executeCommand(COMMANDS.SET_TOKEN)
                },
                {
                    label: BUTTONS.HELP,
                    action: () => this.showTroubleshootingGuide()
                }
            ]
        });
    }

    private async showServerError(status: number): Promise<void> {
        await this.showConnectionError(
            MESSAGES.BRIDGE_SERVER_ERROR.replace('%d', String(status)),
            {
                buttons: [
                    {
                        label: BUTTONS.CHECK_SETTINGS,
                        action: () => vscode.commands.executeCommand('workbench.action.openSettings', CONFIG_SECTION)
                    },
                    {
                        label: BUTTONS.STAY_OFFLINE,
                        action: () => this.disconnect()
                    },
                    {
                        label: BUTTONS.RETRY,
                        action: () => this.checkConnection()
                    }
                ]
            }
        );
    }

    private async showTimeoutError(url: string): Promise<void> {
        await this.showConnectionError(
            `${MESSAGES.CONNECTION_TIMEOUT} to ${url}`,
            {
                buttons: [
                    {
                        label: BUTTONS.CHECK_SETTINGS,
                        action: () => vscode.commands.executeCommand('workbench.action.openSettings', CONFIG_SECTION)
                    },
                    {
                        label: BUTTONS.STAY_OFFLINE,
                        action: () => this.disconnect()
                    },
                    {
                        label: BUTTONS.RETRY,
                        action: () => this.checkConnection()
                    }
                ]
            }
        );
    }

    private async showConnectionRefusedError(url: string): Promise<void> {
        await this.showConnectionError(MESSAGES.CONNECTION_REFUSED, {
            detail:
                `Connection refused on ${url}\n\n` +
                'Make sure:\n' +
                '• Burp Suite is running\n' +
                '• BurpSense Bridge extension is loaded\n' +
                '• Server is started in Bridge Settings tab\n' +
                '• Firewall allows the connection',
            buttons: [
                {
                    label: BUTTONS.OPEN_SETTINGS,
                    action: () => vscode.commands.executeCommand('workbench.action.openSettings', CONFIG_SECTION)
                },
                {
                    label: BUTTONS.HELP,
                    action: () => this.showTroubleshootingGuide()
                }
            ]
        });
    }

    private async showInvalidHostError(url: string): Promise<void> {
        await this.showConnectionError(MESSAGES.INVALID_HOST.replace('%s', url), {
            buttons: [
                {
                    label: BUTTONS.FIX_SETTINGS,
                    action: () => vscode.commands.executeCommand('workbench.action.openSettings', CONFIG_KEYS.BRIDGE_IP)
                },
                {
                    label: BUTTONS.HELP,
                    action: () => this.showTroubleshootingGuide()
                }
            ]
        });
    }

    private async showGenericError(message: string): Promise<void> {
        await this.showConnectionError(MESSAGES.GENERIC_CONNECTION_ERROR.replace('%s', message), {
            buttons: [
                {
                    label: BUTTONS.RETRY,
                    action: () => this.checkConnection()
                },
                {
                    label: BUTTONS.HELP,
                    action: () => this.showTroubleshootingGuide()
                }
            ]
        });
    }

    private showTroubleshootingGuide(): void {
        vscode.window.showInformationMessage(
            'BurpSense Troubleshooting',
            {
                modal: true,
                detail:
                    '1. Check Burp Suite is running\n' +
                    '2. Verify BurpSense Bridge extension is loaded in Burp\n' +
                    '3. Ensure server is started in Bridge Settings\n' +
                    `4. Confirm IP and port match (default: ${CONNECTION.DEFAULT_IP}:${CONNECTION.DEFAULT_PORT})\n` +
                    '5. Check firewall settings\n' +
                    '6. Verify API token is correct\n\n' +
                    'Still having issues? Check the VS Code Output panel (BurpSense)'
            },
            BUTTONS.OPEN_SETTINGS
        ).then(action => {
            if (action === BUTTONS.OPEN_SETTINGS) {
                vscode.commands.executeCommand('workbench.action.openSettings', CONFIG_SECTION);
            }
        });
    }

    /**
     * Refreshes issues in response to WebSocket signal.
     */
    private async refreshIssuesFromWebSocket(): Promise<void> {
        try {
            const issues = await this.getIssues();

            if (this.issueTreeProvider) {
                this.issueTreeProvider.updateCache(issues);
            }

            this._issueCount = this.knownIssueIds.size;
            this.updateStatus(true);

            if (this.onIssuesUpdated) {
                this.onIssuesUpdated();
            }
        } catch (error) {
            Logger.error('WebSocket refresh failed', error, 'Connection');
        }
    }
}