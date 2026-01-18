/**
 * Extension configuration and constants.
 */

export const EXTENSION_ID = 'arqsz.burpsense';
export const EXTENSION_NAME = 'BurpSense';
export const CONFIG_SECTION = 'burpsense';

/** Command identifiers for VS Code command palette and programmatic execution */
export const COMMANDS = {
    SET_TOKEN: `${CONFIG_SECTION}.setToken`,
    CHECK_CONNECTION: `${CONFIG_SECTION}.checkConnection`,
    RETRY_CONNECTION: `${CONFIG_SECTION}.retryConnection`,
    STATUS_MENU: `${CONFIG_SECTION}.statusMenu`,
    CONNECT: `${CONFIG_SECTION}.connect`,
    DISCONNECT: `${CONFIG_SECTION}.disconnect`,

    MAP_ISSUE_TO_FILE: `${CONFIG_SECTION}.mapIssueToFile`,
    REMOVE_MAPPING: `${CONFIG_SECTION}.removeMapping`,
    REMOVE_MAPPING_FROM_TREE: `${CONFIG_SECTION}.removeMappingFromTree`,
    BULK_REMOVE_MAPPINGS: `${CONFIG_SECTION}.bulkRemoveMappings`,
    EXPORT_MAPPINGS: `${CONFIG_SECTION}.exportMappings`,
    IMPORT_MAPPINGS: `${CONFIG_SECTION}.importMappings`,

    REFRESH_ISSUES: `${CONFIG_SECTION}.refreshIssues`,
    VIEW_ISSUE_DETAILS: `${CONFIG_SECTION}.viewIssueDetails`,
    OPEN_DIAGNOSTIC_ADVISORY: `${CONFIG_SECTION}.openDiagnosticAdvisory`,

    TOGGLE_SCOPE: `${CONFIG_SECTION}.toggleScope`,
    TOGGLE_SCOPE_ON: `${CONFIG_SECTION}.toggleScopeOn`,
    TOGGLE_SCOPE_OFF: `${CONFIG_SECTION}.toggleScopeOff`,
    SELECT_MIN_SEVERITY: `${CONFIG_SECTION}.selectMinSeverity`,
    SELECT_MIN_CONFIDENCE: `${CONFIG_SECTION}.selectMinConfidence`,
    FILTER_PREVIEW: `${CONFIG_SECTION}.filterPreview`,
    SEARCH_ISSUES: `${CONFIG_SECTION}.searchIssues`,
    CLEAR_SEARCH_FILTER: `${CONFIG_SECTION}.clearSearchFilter`,
    SHOW_LOGS: `${CONFIG_SECTION}.showLogs`,
} as const;

/** Keys for workspace configuration. Access via `vscode.workspace.getConfiguration()` */
export const CONFIG_KEYS = {
    BRIDGE_IP: `${CONFIG_SECTION}.bridgeIp`,
    BRIDGE_PORT: `${CONFIG_SECTION}.bridgePort`,
    IN_SCOPE_ONLY: `${CONFIG_SECTION}.inScopeOnly`,
    MIN_SEVERITY: `${CONFIG_SECTION}.minSeverity`,
    MIN_CONFIDENCE: `${CONFIG_SECTION}.minConfidence`,
    SHOW_DRIFT_NOTIFICATIONS: `${CONFIG_SECTION}.showDriftNotifications`,
    CONFIRM_MAPPING_DELETION: `${CONFIG_SECTION}.confirmMappingDeletion`,
    AUTO_CLEAN_ORPHANED_MAPPINGS: `${CONFIG_SECTION}.autoCleanOrphanedMappings`,
} as const;

/**
 * Bridge server connection settings.
 * Polling uses exponential backoff when disconnected (5s, 10s, 20s... up to 5min).
 */
export const CONNECTION = {
    DEFAULT_IP: '127.0.0.1',
    DEFAULT_PORT: 1337,
    TIMEOUT_MS: 3000,
    POLLING_INTERVAL_MS: 60000,
    BASE_RETRY_DELAY_MS: 10000,
    MAX_RETRY_DELAY_MS: 300000,
    MAX_RETRY_COUNT: 10,
    WS_RECONNECT_DELAY_MS: 5000,
} as const;

export const SEVERITY_LEVELS = ['HIGH', 'MEDIUM', 'LOW', 'INFORMATION'] as const;
export const CONFIDENCE_LEVELS = ['CERTAIN', 'FIRM', 'TENTATIVE'] as const;

/** Numeric weights for severity filtering */
export const SEVERITY_WEIGHTS: Record<string, number> = {
    HIGH: 4,
    MEDIUM: 3,
    LOW: 2,
    INFORMATION: 1,
};

/**
 * Cache configuration.
 */
export const CACHE = {
    TREE_TTL_MS: 30000,
    GROUP_DATA_EXPIRY_MS: 30000,
    MAX_CACHED_ISSUES: 5000,
    MAX_GROUP_CACHE_SIZE: 200,
    CLEANUP_INTERVAL_MS: 60000,
} as const;

/** Timing for debouncing UI updates */
export const TIMING = {
    SAVE_DEBOUNCE_MS: 500,
    REFRESH_DEBOUNCE_MS: 100,
    STATE_RESTORE_DELAY_MS: 100,
} as const;

/** Size limits to prevent unbounded growth in stored data */
export const LIMITS = {
    MAX_MATCH_TEXT_LENGTH: 100,
    MAX_CONTEXT_LENGTH: 100,
    MAX_SUGGESTED_ISSUES: 5,
    MAX_RECENT_MAPPINGS: 10,
    SHORT_CONTENT_LINES: 100,
    BULK_OPERATION_THRESHOLD: 10,
} as const;

export const DISPLAY = {
    MAX_ISSUE_NAME_LENGTH: 40,
    TRUNCATED_NAME_LENGTH: 37,
    SHORT_ID_LENGTH: 6,
} as const;

/** User-facing messages */
export const MESSAGES = {
    NO_TOKEN: 'BurpSense: No API token configured',
    AUTH_FAILED: 'BurpSense: Authentication failed - Invalid API token',
    CONNECTION_TIMEOUT: 'BurpSense: Connection timeout',
    CONNECTION_REFUSED: 'BurpSense: Cannot connect to bridge',
    MAX_RETRIES: 'BurpSense: Failed to connect after multiple attempts',

    NO_ACTIVE_EDITOR: 'No active editor. Open a file first.',
    NO_ISSUES: 'No live Burp issues found. Is the bridge connected?',

    NO_MAPPING_ON_LINE: 'No BurpSense mapping found on this line.',
    MAPPING_CREATED: 'Mapped Burp issue with ID %s to line %d',
    MAPPING_REMOVED: 'Removed BurpSense mapping: %s',

    FILE_DELETED: 'File "%s" was deleted. Remove %d associated mapping(s)?',
    ORPHANED_MAPPINGS_REMOVED: 'Removed %d mapping(s)',

    CHECKING_CONNECTION: 'Checking BurpSense connection...',
    REFRESHING_ISSUES: 'Refreshing Burp issues...',
    CREATING_MAPPING: 'Creating Burp mapping...',
    REMOVING_MAPPINGS: 'Removing %d mappings...',
    EXPORTING_MAPPINGS: 'Exporting %d mappings...',
    IMPORTING_MAPPINGS: 'Reading file...',

    INVALID_ISSUE_ITEM: 'Invalid issue item',
    NO_MAPPINGS_FOR_ISSUE: 'No mappings found for this issue.',
    ALL_MAPPINGS_REMOVED: 'Removed all %d mappings for "%s"',
    MULTIPLE_MAPPINGS_REMOVED: 'Removed %d mapping(s) for "%s"',

    CONFIRM_REMOVE_MAPPING: 'Remove mapping for "%s"?\n\nLocation: %s',
    CONFIRM_REMOVE_ALL_MAPPINGS: 'Remove all %d mappings for "%s"?',
    CONFIRM_REMOVE_SELECTED_MAPPINGS: 'Remove %d mapping(s) for "%s"?\n\n%s',

    MANAGE_MAPPINGS_TITLE: 'Manage Mappings: %s',
    MANAGE_MAPPINGS_PLACEHOLDER: 'This issue has %d mappings. What would you like to do?',
    SELECT_MAPPINGS_TO_REMOVE: 'Select mappings to remove (%d total)',
} as const;

export const UI_LABELS = {
    REMOVE_ALL_OPTION: '$(trash) Remove All %d Mappings',
    REMOVE_ALL_DESCRIPTION: 'Delete all locations',
    REMOVE_ALL_DETAIL: 'Remove all mappings for "%s"',

    CHOOSE_SPECIFIC_OPTION: '$(list-selection) Choose Specific Mappings',
    CHOOSE_SPECIFIC_DESCRIPTION: 'Select which to remove',
    CHOOSE_SPECIFIC_DETAIL: 'Pick individual mappings to delete',

    CANCEL_OPTION: '$(close) Cancel',
    CANCEL_DETAIL: 'Keep all mappings',

    FILE_ICON: '$(file-code)',
    NO_PREVIEW: 'No preview available',
} as const;

/** Button labels for dialogs */
export const BUTTONS = {
    SET_TOKEN: 'Set Token',
    UPDATE_TOKEN: 'Update Token',
    RETRY: 'Retry',
    HELP: 'Help',
    CHECK_SETTINGS: 'Check Settings',
    FIX_SETTINGS: 'Fix Settings',
    OPEN_SETTINGS: 'Open Settings',

    REMOVE: 'Remove',
    REMOVE_MAPPINGS: 'Remove Mappings',
    KEEP_MAPPINGS: 'Keep Mappings',
    DONT_ASK_AGAIN: 'Don\'t Ask Again',
    CANCEL: 'Cancel',

    VIEW_DETAILS: 'View Details',
    RETRY_NOW: 'Retry Now',
    STAY_OFFLINE: 'Stay Offline'
} as const;

export const DIAGNOSTIC_SOURCE = 'BurpSense';

export const TREE_VIEW = {
    ID: `${CONFIG_SECTION}.issuesView`,
    TITLE: 'Live Burp Issues',
} as const;

export const STATUS_BAR = {
    CONNECTED_FORMAT: 'BurpSense: %d issues',
    DISCONNECTED: 'BurpSense: Disconnected',
    CONNECTING: '$(sync~spin) BurpSense: Connecting...',
    SEARCH_FORMAT: '$(search) "%s"',
    TOOLTIP_FORMAT: 'Connected to %s\nIssues: %d\n\nClick for quick actions',
    TOOLTIP_DISCONNECTED: 'Not connected - Click to troubleshoot',
} as const;

export const WALKTHROUGH = {
    ID: `${CONFIG_SECTION}.setup`,
    TITLE: 'Get Started with BurpSense',
} as const;

/** Context keys for when clauses in package.json */
export const CONTEXT_KEYS = {
    CONNECTED: `${CONFIG_SECTION}.connected`,
    HAS_FILTER: `${CONFIG_SECTION}.hasFilter`,
} as const;

/** Keys for extension global state storage */
export const STORAGE_KEYS = {
    HAS_SEEN_WELCOME: `${CONFIG_SECTION}.hasSeenWelcome`,
    RECENT_MAPPINGS: 'recentMappings',
} as const;

export const FILE_PATHS = {
    BURPSENSE_DIR: '.burpsense',
    MAPPINGS_FILE: 'mappings.json',
    EXPORT_DEFAULT_NAME: `${CONFIG_SECTION}.mappings.json`,
} as const;

/**
 * String similarity configuration for drift detection.
 * Uses Levenshtein distance with optimizations for long strings.
 * 
 * Tuning notes:
 * - Increase MINIMUM_THRESHOLD to be stricter (fewer false matches)
 * - Increase SAMPLE_LENGTH for better accuracy on long strings (slower)
 * - Decrease MAX_STRING_LENGTH_FOR_LEVENSHTEIN to use sampling sooner (faster)
 */
export const SIMILARITY = {
    /** Minimum similarity ratio (0-1) to consider strings as matching */
    MINIMUM_THRESHOLD: 0.70,

    /** Reject if length difference exceeds this ratio */
    LENGTH_DIFFERENCE_THRESHOLD: 0.3,

    /** Switch to sampling for strings longer than this */
    MAX_STRING_LENGTH_FOR_LEVENSHTEIN: 200,

    /** Sample size when comparing long strings */
    SAMPLE_LENGTH: 50,
} as const;

/**
 * Line drift detection settings.
 * Three-phase search: nearby, context, proximity..
 */
export const DRIFT = {
    /** Lines to search above/below original position */
    SEARCH_RADIUS_LINES: 5,

    /** Max lines for basic scan*/
    MAX_FULL_SCAN_LINES: 1000,

    /** Max iterations in large files */
    MAX_FULL_SCAN_ITERATIONS: 500,

    /**
     * File size threshold for strategy selection:
     * - <=2000 lines: Search every line
     * - >2000 lines: Use sampling
     * 
     */
    LARGE_FILE_THRESHOLD: 2000,
} as const;

/**
 * Patterns for smart issue suggestions.
 * When user maps an issue, these patterns suggest likely matches
 * based on code context.
 */
export const SMART_SUGGESTIONS = [
    {
        keywords: ['sql', 'query', 'select', 'insert', 'update', 'delete', 'where'],
        issuePatterns: ['sql', 'injection'],
    },
    {
        keywords: ['file', 'path', 'upload', 'download', 'include'],
        issuePatterns: ['path', 'traversal', 'file', 'upload'],
    },
    {
        keywords: ['html', 'script', 'render', 'template', 'dom'],
        issuePatterns: ['xss', 'cross-site', 'scripting'],
    },
    {
        keywords: ['auth', 'login', 'session', 'token', 'cookie', 'jwt'],
        issuePatterns: ['auth', 'session', 'csrf', 'token'],
    },
    {
        keywords: ['api', 'rest', 'endpoint', 'request', 'response'],
        issuePatterns: ['api', 'rest', 'endpoint'],
    },
    {
        keywords: ['password', 'secret', 'key', 'credential'],
        issuePatterns: ['password', 'credential', 'secret', 'exposure'],
    },
] as const;