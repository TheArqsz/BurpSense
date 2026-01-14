/**
 * Type definitions for the BurpSense extension
 */

export interface BurpIssue {
    id: string;
    name: string;
    severity: 'HIGH' | 'MEDIUM' | 'LOW' | 'INFORMATION';
    confidence: 'CERTAIN' | 'FIRM' | 'TENTATIVE';
    baseUrl: string;
    detail: string;
    remediation: string;
    background?: string;
    service: {
        host: string;
        port: number;
        protocol: 'http' | 'https';
    };
    responseMarkers?: Array<{
        start: number;
        end: number;
    }>;
    request?: string;
    response?: string;
}

export type MappingStatus = 'confirmed' | 'false_positive' | 'remediated';

export interface BurpMapping {
    issueId: string;
    issueName?: string;
    filePath: string;
    line: number;
    matchText?: string;
    contextBefore?: string;
    contextAfter?: string;
    status: MappingStatus;
    note?: string;
}

export interface MappingsStore {
    version: string;
    project: string;
    mappings: BurpMapping[];
}

export type TreeItemType = 'severityGroup' | 'issueGroup' | 'issueInstance' | 'emptyState' | 'loading';

export interface IssueTreeItemData {
    type: TreeItemType;
    severity?: string;
    name?: string;
    instances?: BurpIssue[];
    issue?: BurpIssue;
    groupKey?: string;
}

export interface BurpSenseConfiguration {
    bridgeIp: string;
    bridgePort: number;
    inScopeOnly: boolean;
    minSeverity: string;
    minConfidence: string;
    showDriftNotifications: boolean;
    confirmMappingDeletion: boolean;
    autoCleanOrphanedMappings?: boolean;
}

export interface IssueFilters {
    minSeverity: string;
    minConfidence: string;
    inScopeOnly: boolean;
    searchText?: string;
}

export interface FilterPreset {
    label: string;
    detail: string;
    filters: {
        severity: string;
        confidence: string;
        inScope: boolean;
    };
}

export interface DriftDetail {
    file: string;
    from: number;
    to: number;
}

export interface MappingProcessResult {
    diagnostic?: any;
    shouldUpdate: boolean;
    newLine?: number;
}

export interface LineDriftResult {
    found: boolean;
    line?: number;
}

export interface SmartSuggestionMapping {
    keywords: readonly string[];
    issuePatterns: readonly string[];
}

/**
 * Response from incremental issues endpoint.
 * 
 * The server tracks which issues the client knows about and returns:
 * - newIssues: Issues added since last request (client doesn't have these yet)
 * - removedIds: Issues that were deleted or no longer match filters
 * 
 */
export interface IncrementalIssuesResponse {
    newIssues: BurpIssue[];
    removedIds: string[];
}