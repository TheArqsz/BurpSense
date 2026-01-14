import * as vscode from 'vscode';
import { CACHE, COMMANDS, CONFIDENCE_LEVELS, CONFIG_KEYS, CONTEXT_KEYS, SEVERITY_LEVELS, STATUS_BAR, TIMING } from '../constants';
import { ConnectionManager } from '../services/ConnectionManager';
import { Logger } from '../services/Logger';
import { MappingManager } from '../services/MappingManager';
import { BurpIssue } from '../types';

/**
 * Cache entry with timestamp for expiration tracking
 */
interface CachedIssueGroup {
    data: IssueItem[];
    timestamp: number;
}

/**
 * Tree provider for the Burp issues view.
 * Implements lazy loading and caching for performance.
 */
export class IssueTreeProvider implements vscode.TreeDataProvider<IssueItem> {
    private mappingManager: MappingManager;

    private readonly _onDidChangeTreeData = new vscode.EventEmitter<IssueItem | undefined | void>();
    readonly onDidChangeTreeData = this._onDidChangeTreeData.event;

    private cachedIssues: BurpIssue[] = [];
    private cacheTimestamp: number = 0;
    private groupedDataCache: Map<string, CachedIssueGroup> = new Map();

    private lastFilterText: string = '';
    private filterText: string = '';
    private filterStatusBarItem?: vscode.StatusBarItem;
    private isLoading: boolean = false;
    private pendingRefresh?: NodeJS.Timeout;
    private refreshPromise?: Promise<void>;

    private expandedSeverities: Set<string> = new Set(SEVERITY_LEVELS);
    private expandedIssueGroups: Set<string> = new Set();
    private treeView?: vscode.TreeView<IssueItem>;

    private shouldRestoreState: boolean = true;
    private cacheCleanupInterval?: NodeJS.Timeout;

    constructor(private connectionManager: ConnectionManager, mappingManager: MappingManager) {
        this.mappingManager = mappingManager;
        setInterval(() => this.cleanupCaches(), CACHE.CLEANUP_INTERVAL_MS);
    }

    /**
     * Cleans up stale cache entries to prevent memory leaks.
     * Evicts old entries using LRU when size limits are exceeded.
     * 
     * Runs automatically every CACHE.CLEANUP_INTERVAL_MS.
     */
    private cleanupCaches(): void {
        const now = Date.now();

        const stale: string[] = [];
        this.groupedDataCache.forEach((value: CachedIssueGroup, key: string) => {
            if (now - value.timestamp > CACHE.GROUP_DATA_EXPIRY_MS) {
                stale.push(key);
            }
        });
        stale.forEach(key => this.groupedDataCache.delete(key));

        if (this.groupedDataCache.size > CACHE.MAX_GROUP_CACHE_SIZE) {
            const sorted = Array.from(this.groupedDataCache.entries())
                .sort((a, b) => a[1].timestamp - b[1].timestamp);
            const toRemove = sorted.slice(0, sorted.length - CACHE.MAX_GROUP_CACHE_SIZE);
            toRemove.forEach(([key]) => this.groupedDataCache.delete(key));

            Logger.info(
                `Evicted ${toRemove.length} stale entries from group cache`,
                'Cache'
            );
        }

        if (this.cachedIssues.length > CACHE.MAX_CACHED_ISSUES) {
            Logger.warn(`Truncating ${this.cachedIssues.length} issues to ${CACHE.MAX_CACHED_ISSUES}`, 'Cache');
            this.cachedIssues = this.cachedIssues.slice(0, CACHE.MAX_CACHED_ISSUES);
        }
    }

    /**
     * Configures status bar item for search filter display.
     * 
     * @param item - Status bar item to update
     */
    public setStatusBarItem(item: vscode.StatusBarItem): void {
        this.filterStatusBarItem = item;
        this.updateFilterDisplay();
    }

    /**
     * Registers tree view for expansion state tracking.
     * Sets up listeners for expand/collapse events.
     * 
     * @param treeView - Tree view instance
     */
    public setTreeView(treeView: vscode.TreeView<IssueItem>): void {
        this.treeView = treeView;

        this.treeView.onDidExpandElement(e => {
            if (e.element.contextValue === 'severityGroup') {
                const severity = typeof e.element.label === 'string'
                    ? e.element.label
                    : e.element.label?.label;
                if (severity) {
                    this.expandedSeverities.add(severity);
                }
            } else if (e.element.contextValue === 'issueGroup' && e.element.groupKey) {
                this.expandedIssueGroups.add(e.element.groupKey);
            }
        });

        this.treeView.onDidCollapseElement(e => {
            if (e.element.contextValue === 'severityGroup') {
                const severity = typeof e.element.label === 'string'
                    ? e.element.label
                    : e.element.label?.label;
                if (severity) {
                    this.expandedSeverities.delete(severity);
                }
            } else if (e.element.contextValue === 'issueGroup' && e.element.groupKey) {
                this.expandedIssueGroups.delete(e.element.groupKey);
            }
        });
    }

    /**
     * Triggers tree refresh with debouncing.
     * Clears cache and notifies VS Code to rebuild tree.
     */
    public refresh(): void {
        if (this.pendingRefresh) {
            clearTimeout(this.pendingRefresh);
        }

        this.pendingRefresh = setTimeout(() => {
            this.invalidateGroupCache();
            this._onDidChangeTreeData.fire();
            this.pendingRefresh = undefined;
        }, TIMING.REFRESH_DEBOUNCE_MS);
    }

    /**
     * Forces immediate refresh without debouncing.
     * Fetches latest issues from bridge and rebuilds tree.
     * Restores expansion state after refresh completes.
     */
    public async forceRefresh(): Promise<void> {
        if (this.refreshPromise) {
            return this.refreshPromise;
        }

        this.refreshPromise = (async () => {
            this.isLoading = true;
            this.invalidateGroupCache();
            this._onDidChangeTreeData.fire();

            try {
                const issues = await this.connectionManager.getAllIssues();
                this.cachedIssues = issues;
                this.cacheTimestamp = Date.now();
                this.connectionManager.updateStatus(true);
                Logger.info(`Refresh complete: ${this.cachedIssues.length} issues`, 'Cache');
            } catch (error) {
                Logger.error('Failed to force refresh', error, 'Cache');
            } finally {
                this.isLoading = false;
                this.refreshPromise = undefined;
                this._onDidChangeTreeData.fire();

                this.shouldRestoreState = true;
                setTimeout(() => this.restoreExpandedState(), TIMING.STATE_RESTORE_DELAY_MS);
            }
        })();

        return this.refreshPromise;
    }

    /**
     * Gets cached issues without triggering a network call.
     * Returns empty array if cache is empty or connection is down.
     * 
     * Use this for quick operations like mapping where slight staleness
     * is acceptable. For guaranteed fresh data, use forceRefresh() first.
     * 
     * @returns Cached issues (may be stale)
     */
    public getCachedIssues(): BurpIssue[] {
        return this.cachedIssues;
    }

    /**
     * Checks if cached issues are available and reasonably fresh.
     * 
     * @returns True if cache has data less than CACHE.TREE_TTL_MS old
     */
    public hasFreshCache(): boolean {
        const now = Date.now();
        const cacheAge = now - this.cacheTimestamp;
        return this.cachedIssues.length > 0 && cacheAge < CACHE.TREE_TTL_MS;
    }

    /**
     * Updates the cache with fresh issues.
     * Called by ConnectionManager after successful fetch.
     * Ensures cache is populated even if tree view isn't visible.
     */
    public updateCache(issues: BurpIssue[], fireUpdate: boolean = true): void {
        const existingIds = new Set(this.cachedIssues.map(i => i.id));
        const uniqueNew = issues.filter(i => !existingIds.has(i.id));

        this.cachedIssues = [...this.cachedIssues, ...uniqueNew];
        this.cacheTimestamp = Date.now();
        this.invalidateGroupCache();

        if (issues.length > 0) {
            Logger.info(
                `Cache updated with ${issues.length} issues (from ConnectionManager)`,
                'Cache'
            );
        }

        if (fireUpdate) {
            this._onDidChangeTreeData.fire();
        }
    }

    /**
     * Removes issues from the cache by ID.
     * 
     * Handles:
     * - Issues deleted in Burp Suite
     * - Issues filtered out by changing min severity/confidence
     * - Issues that went out of scope
     * - Issues that no longer match name regex
     * 
     * @param issueIds - Array of issue IDs to remove from cache
     */
    public removeIssues(issueIds: string[]): void {
        if (issueIds.length === 0) {
            return;
        }

        const beforeCount = this.cachedIssues.length;

        const idsToRemove = new Set(issueIds);

        this.cachedIssues = this.cachedIssues.filter(issue => !idsToRemove.has(issue.id));

        const removedCount = beforeCount - this.cachedIssues.length;

        if (removedCount > 0) {
            this.invalidateGroupCache();

            Logger.info(
                `Removed ${removedCount} issue${removedCount > 1 ? 's' : ''} from cache`,
                'Cache'
            );

            this._onDidChangeTreeData.fire();
        }
    }

    /**
     * Returns parent item for given tree item.
     * Required for tree reveal operations.
     * 
     * @param element - Child item
     * @returns Parent item or undefined if root
     */
    public getParent(element: IssueItem): IssueItem | undefined {
        if (element.contextValue === 'issueInstance' && element.rawData) {
            const issue = element.rawData;
            const severity = issue.severity;

            const allIssues = this.getFilteredIssues();
            const instances = allIssues.filter(i =>
                i.name === issue.name && i.severity === severity
            );

            if (instances.length > 0) {
                return IssueItem.createIssueGroup(severity, issue.name, instances);
            }
        }

        if (element.contextValue === 'issueGroup' && element.groupKey) {
            const severity = element.groupKey.split(':')[0];
            return IssueItem.createSeverityGroup(severity);
        }

        return undefined;
    }

    /**
     * Converts IssueItem to VS Code TreeItem.
     * Restores expansion state from cache.
     * 
     * @param element - Item to convert
     * @returns TreeItem configuration
     */
    public getTreeItem(element: IssueItem): vscode.TreeItem {
        if (element.contextValue === 'severityGroup') {
            const severity = typeof element.label === 'string'
                ? element.label
                : element.label?.label;

            if (severity && this.expandedSeverities.has(severity)) {
                element.collapsibleState = vscode.TreeItemCollapsibleState.Expanded;
            } else {
                element.collapsibleState = vscode.TreeItemCollapsibleState.Collapsed;
            }
        }

        if (element.contextValue === 'issueGroup' && element.groupKey) {
            if (this.expandedIssueGroups.has(element.groupKey)) {
                element.collapsibleState = vscode.TreeItemCollapsibleState.Expanded;
            } else {
                element.collapsibleState = vscode.TreeItemCollapsibleState.Collapsed;
            }
        }

        return element;
    }

    /**
     * Returns child items for given parent.
     * Root: severity groups
     * Severity: issue groups
     * Issue group: individual URLs
     * 
     * Implements lazy loading - only fetches data when expanded.
     * 
     * @param element - Parent item, or undefined for root
     * @returns Array of child items
     */
    public async getChildren(element?: IssueItem): Promise<IssueItem[]> {
        if (!this.connectionManager.isConnected) {
            return [IssueItem.createEmptyState(
                "Not Connected",
                "Connect to BurpSense Bridge to see issues",
                COMMANDS.CONNECT,
                "plug"
            )];
        }

        if (!element) {
            const now = Date.now();
            const cacheValid = (now - this.cacheTimestamp) < CACHE.TREE_TTL_MS;

            if (this.isLoading) {
                Logger.info('Fetch in progress, waiting...', 'Cache');
                return [IssueItem.createLoading()];
            }

            if (this.cachedIssues.length === 0 || !cacheValid) {
                if (!this.isLoading) {
                    this.isLoading = true;

                    Logger.info('Cache expired or empty, fetching issues...', 'Cache');

                    try {
                        const newIssues = await this.connectionManager.getAllIssues();
                        this.cacheTimestamp = Date.now();
                        if (newIssues.length > 0) {
                            this.updateCache(newIssues, false);
                            Logger.info(`Cache refreshed: ${this.cachedIssues.length} issues`, 'Cache');
                        } else if (this.cachedIssues.length === 0) {
                            this.cachedIssues = [];
                        }
                    } catch (error) {
                        console.error('BurpSense: Failed to fetch issues:', error);
                        return [IssueItem.createEmptyState(
                            "Fetch Failed",
                            `Error: ${error}`,
                            COMMANDS.REFRESH_ISSUES,
                            "error"
                        )];
                    } finally {
                        this.isLoading = false;
                    }
                }
            }

            if (this.isLoading) {
                return [IssueItem.createLoading()];
            }

            return this.getRootLevelItems();
        }

        if (element.contextValue === 'severityGroup') {
            const severity = typeof element.label === 'string'
                ? element.label
                : element.label?.label;

            if (severity) {
                return await this.getIssueGroupsForSeverityCached(severity);
            }
        }

        if (element.contextValue?.startsWith('issueGroup') && element.rawData) {
            return this.getIssueInstances(element.rawData);
        }

        return [];
    }

    /**
     * Sets search filter text.
     * Triggers immediate tree refresh.
     * 
     * @param text - Search query
     */
    public setFilter(text: string): void {
        this.filterText = text.toLowerCase();
        this._onDidChangeTreeData.fire();
        this.updateFilterContext();
    }

    /**
     * Clears active search filter.
     */
    public clearSearchFilter(): void {
        this.filterText = '';
        this._onDidChangeTreeData.fire();
        this.updateFilterContext();
    }

    /**
     * Checks if search filter is active.
     * 
     * @returns True if filter text is non-empty
     */
    public hasFilter(): boolean {
        return this.filterText.length > 0;
    }

    /**
     * Returns current filter text.
     * Used for restoring filter in UI.
     * 
     * @returns Current search text
     */
    public getFilterText(): string {
        return this.filterText;
    }

    /**
     * Restores tree expansion state after refresh.
     * Runs asynchronously to avoid blocking UI.
     */
    private async restoreExpandedState(): Promise<void> {
        if (!this.treeView || !this.shouldRestoreState) return;

        this.shouldRestoreState = false;

        try {
            const rootItems = await this.getChildren();

            for (const severity of this.expandedSeverities) {
                const groupItem = rootItems.find(item => {
                    const label = typeof item.label === 'string'
                        ? item.label
                        : item.label?.label;
                    return item.contextValue === 'severityGroup' && label === severity;
                });

                if (groupItem) {
                    await this.treeView.reveal(groupItem, {
                        select: false,
                        focus: false,
                        expand: true
                    });

                    const issueGroups = await this.getChildren(groupItem);
                    for (const issueGroup of issueGroups) {
                        if (issueGroup.contextValue === 'issueGroup' &&
                            issueGroup.groupKey &&
                            this.expandedIssueGroups.has(issueGroup.groupKey)) {
                            await this.treeView.reveal(issueGroup, {
                                select: false,
                                focus: false,
                                expand: true
                            });
                        }
                    }
                }
            }
        } catch (error) {
            console.error('BurpSense: Failed to restore expansion state:', error);
        }
    }

    /**
     * Gets severity group items for tree root.
     * Shows empty state if no issues match filters.
     * 
     * @returns Array of severity folders
     */
    private getRootLevelItems(): IssueItem[] {
        const filteredIssues = this.getFilteredIssues();

        if (filteredIssues.length === 0) {
            if (this.hasFilter()) {
                return [IssueItem.createEmptyState(
                    "No Matches",
                    `No issues match "${this.filterText}"`,
                    COMMANDS.CLEAR_SEARCH_FILTER,
                    "search-stop"
                )];
            }
            return [this.createContextualEmptyState()];
        }

        const severityGroups = new Map<string, BurpIssue[]>();
        const severityOrder = SEVERITY_LEVELS;

        severityOrder.forEach(severity => severityGroups.set(severity, []));

        filteredIssues.forEach(issue => {
            const existing = severityGroups.get(issue.severity) || [];
            existing.push(issue);
            severityGroups.set(issue.severity, existing);
        });

        return severityOrder
            .filter(severity => severityGroups.get(severity)!.length > 0)
            .map(severity => IssueItem.createSeverityGroup(severity));
    }

    /**
     * Gets cached issue groups for a severity level.
     * Cache key includes filter text to handle search changes.
     * 
     * @param severity - Severity level (HIGH, MEDIUM, etc.)
     * @returns Array of grouped issue items
     */
    private async getIssueGroupsForSeverityCached(severity: string): Promise<IssueItem[]> {
        if (this.lastFilterText !== this.filterText) {
            this.invalidateGroupCache();
            this.lastFilterText = this.filterText;
        }

        const cacheKey = `severity:${severity}:${this.filterText}`;
        const cached: CachedIssueGroup | undefined = this.groupedDataCache.get(cacheKey);
        const now = Date.now();

        if (cached && (now - cached.timestamp) < CACHE.GROUP_DATA_EXPIRY_MS) {
            return cached.data;
        }

        const result: IssueItem[] = await this.getIssueGroupsForSeverity(severity);

        const cacheEntry: CachedIssueGroup = {
            data: result,
            timestamp: now
        };

        this.groupedDataCache.set(cacheKey, cacheEntry);

        return result;
    }

    /**
     * Groups issues by name within a severity.
     * Sorts by instance count (descending) then name.
     * 
     * @param severity - Severity level
     * @returns Array of issue group items
     */
    private async getIssueGroupsForSeverity(severity: string): Promise<IssueItem[]> {
        const filteredIssues = this.getFilteredIssues();
        const issuesForSeverity = filteredIssues.filter(issue => issue.severity === severity);

        const grouped = new Map<string, BurpIssue[]>();
        issuesForSeverity.forEach(issue => {
            const existing = grouped.get(issue.name) || [];
            existing.push(issue);
            grouped.set(issue.name, existing);
        });

        const items: IssueItem[] = [];
        const allMappings = await this.mappingManager.loadMappings();
        const mappedIssueIds = new Set(allMappings.mappings.map(m => m.issueId));

        grouped.forEach((instances, name) => {
            const hasMappings = instances.some(issue => mappedIssueIds.has(issue.id));

            items.push(IssueItem.createIssueGroup(severity, name, instances, hasMappings));
        });

        items.sort((a, b) => {
            const countA = a.rawData?.length || 0;
            const countB = b.rawData?.length || 0;
            if (countA !== countB) {
                return countB - countA;
            }
            const nameA = typeof a.label === 'string' ? a.label : '';
            const nameB = typeof b.label === 'string' ? b.label : '';
            return nameA.localeCompare(nameB);
        });

        return items;
    }

    /**
     * Clears grouped data cache.
     */
    private invalidateGroupCache(): void {
        this.groupedDataCache.clear();
    }

    /**
     * Converts issue instances to tree items.
     * Each item represents one URL affected by the issue.
     * 
     * @param instances - Issue instances
     * @returns Tree items for each URL
     */
    private getIssueInstances(instances: BurpIssue[]): IssueItem[] {
        return instances.map(issue => IssueItem.createIssueInstance(issue));
    }

    /**
     * Filters cached issues by search text.
     * Searches name, ID, URL, severity and confidence.
     * 
     * @returns Filtered issues
     */
    private getFilteredIssues(): BurpIssue[] {
        if (!this.hasFilter()) {
            return this.cachedIssues;
        }

        return this.cachedIssues.filter(issue => {
            const searchableText = [
                issue.name,
                issue.id,
                issue.baseUrl,
                issue.severity,
                issue.confidence
            ].join(' ').toLowerCase();

            return searchableText.includes(this.filterText);
        });
    }

    /**
     * Creates appropriate empty state based on filter settings.
     * Shows different messages for "no matches" vs "no issues".
     * 
     * @returns Empty state tree item
     */
    private createContextualEmptyState(): IssueItem {
        const config = vscode.workspace.getConfiguration();
        const minSeverity = config.get<string>(CONFIG_KEYS.MIN_SEVERITY) || SEVERITY_LEVELS[SEVERITY_LEVELS.length - 1];
        const minConfidence = config.get<string>(CONFIG_KEYS.MIN_CONFIDENCE) || CONFIDENCE_LEVELS[CONFIDENCE_LEVELS.length - 1];
        const inScopeOnly = config.get<boolean>(CONFIG_KEYS.IN_SCOPE_ONLY) ?? true;

        const hasRestrictiveFilters = minSeverity !== SEVERITY_LEVELS[SEVERITY_LEVELS.length - 1] ||
            minConfidence !== CONFIDENCE_LEVELS[CONFIDENCE_LEVELS.length - 1] ||
            inScopeOnly;

        if (hasRestrictiveFilters) {
            return IssueItem.createEmptyState(
                "No issues match set filters",
                `Current filters: Severity ≥ ${minSeverity}, Confidence ≥ ${minConfidence}${inScopeOnly ? ', In-Scope Only' : ''}`,
                COMMANDS.SELECT_MIN_SEVERITY,
                "filter"
            );
        }

        return IssueItem.createEmptyState(
            "No issues found",
            "Run a scan in Burp Suite or check your site map",
            COMMANDS.REFRESH_ISSUES,
            "info"
        );
    }

    /**
     * Updates VS Code context key for filter state.
     * Controls visibility of "clear filter" button.
     */
    private updateFilterContext(): void {
        vscode.commands.executeCommand(
            'setContext',
            CONTEXT_KEYS.HAS_FILTER,
            this.filterText.length > 0
        );
        this.updateFilterDisplay();
    }

    /**
     * Updates status bar display for active filter.
     * Shows search indicator when filter is active.
     */
    private updateFilterDisplay(): void {
        if (!this.filterStatusBarItem) {
            return;
        }

        if (this.filterText) {
            this.filterStatusBarItem.text = STATUS_BAR.SEARCH_FORMAT.replace('%s', this.filterText);
            this.filterStatusBarItem.tooltip = `Active search filter: "${this.filterText}"\nClick to change or clear`;
            this.filterStatusBarItem.backgroundColor = new vscode.ThemeColor('statusBarItem.warningBackground');
            this.filterStatusBarItem.show();
        } else {
            this.filterStatusBarItem.hide();
        }
    }

    /**
     * Cleans up resources.
     * Stops timers and disposes event emitter.
     */
    public dispose(): void {
        if (this.pendingRefresh) {
            clearTimeout(this.pendingRefresh);
            this.pendingRefresh = undefined;
        }

        if (this.cacheCleanupInterval) {
            clearInterval(this.cacheCleanupInterval);
            this.cacheCleanupInterval = undefined;
        }

        this.groupedDataCache.clear();
        this.cachedIssues = [];

        this._onDidChangeTreeData.dispose();
    }
}

/**
 * Tree item for issues view
 */
export class IssueItem extends vscode.TreeItem {
    public readonly isGroup: boolean;
    public readonly rawData?: any;
    public readonly groupKey?: string;

    private constructor(
        label: string,
        collapsibleState: vscode.TreeItemCollapsibleState,
        isGroup: boolean,
        rawData?: any,
        groupKey?: string
    ) {
        super(label, collapsibleState);
        this.isGroup = isGroup;
        this.rawData = rawData;
        this.groupKey = groupKey;
    }

    public static createSeverityGroup(severity: string): IssueItem {
        const item = new IssueItem(severity.trim(), vscode.TreeItemCollapsibleState.Collapsed, true);
        item.iconPath = new vscode.ThemeIcon('folder');
        item.contextValue = 'severityGroup';
        return item;
    }

    public static createIssueGroup(
        severity: string,
        name: string,
        instances: BurpIssue[],
        hasMappings: boolean = false
    ): IssueItem {
        const count = instances.length;
        const groupKey = `${severity}:${name}`;

        const item = new IssueItem(
            name.trim(),
            vscode.TreeItemCollapsibleState.Collapsed,
            true,
            instances,
            groupKey
        );

        item.description = count > 1 ? `${count} instances` : '1 instance';
        item.iconPath = IssueItem.getSeverityIcon(instances[0].severity);

        const urls = instances.slice(0, 5).map(i => i.baseUrl.trim()).join('\n');
        const more = count > 5 ? `\n...and ${count - 5} more` : '';
        item.tooltip = `${name.trim()}\n\n${urls}${more}`;

        item.contextValue = 'issueGroup';

        if (hasMappings) {
            item.contextValue = 'issueGroup hasMappings';
        } else {
            item.contextValue = 'issueGroup';
        }

        return item;
    }

    public static createIssueInstance(issue: BurpIssue): IssueItem {
        const shortId = issue.id.substring(0, 8);

        const item = new IssueItem(
            issue.baseUrl.trim(),
            vscode.TreeItemCollapsibleState.None,
            false,
            issue
        );

        item.description = `[${shortId}]`;
        item.tooltip = `${issue.name}\n${issue.baseUrl}\nConfidence: ${issue.confidence}\nID: ${issue.id}`;
        item.iconPath = new vscode.ThemeIcon('link');
        item.contextValue = 'issueInstance';

        item.command = {
            command: COMMANDS.VIEW_ISSUE_DETAILS,
            title: 'View Details',
            arguments: [issue]
        };

        return item;
    }

    public static createEmptyState(
        title: string,
        description: string,
        actionCommand: string,
        icon: string = "info"
    ): IssueItem {
        const item = new IssueItem(title, vscode.TreeItemCollapsibleState.None, false);
        item.description = description.trim();
        item.iconPath = new vscode.ThemeIcon(icon);
        item.contextValue = 'emptyState';
        item.command = {
            command: actionCommand,
            title: 'Take Action',
            arguments: []
        };
        return item;
    }

    public static createLoading(): IssueItem {
        const item = new IssueItem("Loading issues...", vscode.TreeItemCollapsibleState.None, false);
        item.iconPath = new vscode.ThemeIcon('loading~spin');
        item.contextValue = 'loading';
        return item;
    }

    private static getSeverityIcon(severity: string): vscode.ThemeIcon {
        switch (severity) {
            case 'HIGH':
                return new vscode.ThemeIcon('error', new vscode.ThemeColor('errorForeground'));
            case 'MEDIUM':
                return new vscode.ThemeIcon('warning', new vscode.ThemeColor('problemsWarningIcon.foreground'));
            case 'LOW':
                return new vscode.ThemeIcon('info', new vscode.ThemeColor('charts.blue'));
            default:
                return new vscode.ThemeIcon('info');
        }
    }
}