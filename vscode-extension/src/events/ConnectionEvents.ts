import * as vscode from 'vscode';
import { BurpIssue } from '../types';

/**
 * Event types emitted by ConnectionManager
 */
export enum ConnectionEventType {
    /**
     * Emitted when connection to bridge is established
     * Payload: { issueCount: number }
     */
    Connected = 'connected',

    /**
     * Emitted when connection to bridge is lost
     * Payload: { reason: string }
     */
    Disconnected = 'disconnected',

    /**
     * Emitted when new issues are fetched from bridge
     * Payload: { issues: BurpIssue[] }
     */
    IssuesReceived = 'issuesReceived',

    /**
     * Emitted when issues are removed from bridge
     * Payload: { issueIds: string[] }
     */
    IssuesRemoved = 'issuesRemoved',

    /**
     * Emitted when incremental sync completes
     * Payload: { newCount: number, removedCount: number }
     */
    SyncCompleted = 'syncCompleted',

    /**
     * Emitted when connection error occurs
     * Payload: { error: Error }
     */
    ConnectionError = 'connectionError'
}

/**
 * Base interface for all connection events
 */
export interface ConnectionEvent {
    type: ConnectionEventType;
    timestamp: number;
}

/**
 * Connected event payload
 */
export interface ConnectedEvent extends ConnectionEvent {
    type: ConnectionEventType.Connected;
    data: {
        issueCount: number;
    };
}

/**
 * Disconnected event payload
 */
export interface DisconnectedEvent extends ConnectionEvent {
    type: ConnectionEventType.Disconnected;
    data: {
        reason: string;
    };
}

/**
 * Issues received event payload
 */
export interface IssuesReceivedEvent extends ConnectionEvent {
    type: ConnectionEventType.IssuesReceived;
    data: {
        issues: BurpIssue[];
        fireUpdate?: boolean;
    };
}

/**
 * Issues removed event payload
 */
export interface IssuesRemovedEvent extends ConnectionEvent {
    type: ConnectionEventType.IssuesRemoved;
    data: {
        issueIds: string[];
    };
}

/**
 * Sync completed event payload
 */
export interface SyncCompletedEvent extends ConnectionEvent {
    type: ConnectionEventType.SyncCompleted;
    data: {
        newCount: number;
        removedCount: number;
    };
}

/**
 * Connection error event payload
 */
export interface ConnectionErrorEvent extends ConnectionEvent {
    type: ConnectionEventType.ConnectionError;
    data: {
        error: Error;
    };
}

/**
 * Union type of all possible events
 */
export type AnyConnectionEvent =
    | ConnectedEvent
    | DisconnectedEvent
    | IssuesReceivedEvent
    | IssuesRemovedEvent
    | SyncCompletedEvent
    | ConnectionErrorEvent;

/**
 * Event emitter for connection events
 */
export class ConnectionEventEmitter {
    private readonly _onEvent = new vscode.EventEmitter<AnyConnectionEvent>();

    /**
     * Event that fires when any connection event occurs
     */
    public readonly onEvent = this._onEvent.event;

    /**
     * Emits a connection event
     */
    public emit(event: AnyConnectionEvent): void {
        this._onEvent.fire(event);
    }

    /**
     * Helper to emit Connected event
     */
    public emitConnected(issueCount: number): void {
        this.emit({
            type: ConnectionEventType.Connected,
            timestamp: Date.now(),
            data: { issueCount }
        });
    }

    /**
     * Helper to emit Disconnected event
     */
    public emitDisconnected(reason: string): void {
        this.emit({
            type: ConnectionEventType.Disconnected,
            timestamp: Date.now(),
            data: { reason }
        });
    }

    /**
     * Helper to emit IssuesReceived event
     */
    public emitIssuesReceived(issues: BurpIssue[], fireUpdate: boolean = true): void {
        this.emit({
            type: ConnectionEventType.IssuesReceived,
            timestamp: Date.now(),
            data: { issues, fireUpdate }
        });
    }

    /**
     * Helper to emit IssuesRemoved event
     */
    public emitIssuesRemoved(issueIds: string[]): void {
        this.emit({
            type: ConnectionEventType.IssuesRemoved,
            timestamp: Date.now(),
            data: { issueIds }
        });
    }

    /**
     * Helper to emit SyncCompleted event
     */
    public emitSyncCompleted(newCount: number, removedCount: number): void {
        this.emit({
            type: ConnectionEventType.SyncCompleted,
            timestamp: Date.now(),
            data: { newCount, removedCount }
        });
    }

    /**
     * Helper to emit ConnectionError event
     */
    public emitConnectionError(error: Error): void {
        this.emit({
            type: ConnectionEventType.ConnectionError,
            timestamp: Date.now(),
            data: { error }
        });
    }

    /**
     * Dispose of event emitter
     */
    public dispose(): void {
        this._onEvent.dispose();
    }
}
