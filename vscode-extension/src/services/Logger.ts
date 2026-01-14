import * as vscode from 'vscode';
import { EXTENSION_NAME } from '../constants';

/**
 * Centralized logging to VS Code output channel.
 * Prefixes messages with timestamp and category.
 */
export class Logger {
    private static channel: vscode.OutputChannel;

    /**
     * Creates output channel.
     * Call once during extension activation.
     */
    public static initialize(): void {
        if (!this.channel) {
            this.channel = vscode.window.createOutputChannel(EXTENSION_NAME);
        }
    }

    /**
     * Logs informational message.
     * 
     * @param message - Message text
     * @param category - Subsystem (Connection, Mapping, etc.)
     */
    public static info(message: string, category: string = 'General'): void {
        this.log('INFO', message, category);
    }

    /**
     * Logs warning message.
     * 
     * @param message - Warning text
     * @param category - Subsystem
     */
    public static warn(message: string, category: string = 'General'): void {
        this.log('WARN', message, category);
    }

    /**
     * Logs error with stack trace if available.
     * 
     * @param message - Error description
     * @param error - Error object (optional)
     * @param category - Subsystem
     */
    public static error(message: string, error?: any, category: string = 'General'): void {
        const detail = error instanceof Error ? error.stack : String(error);
        this.log('ERROR', `${message}${error ? ` - ${detail}` : ''}`, category);
    }

    /**
     * Opens output channel in UI.
     */
    public static show(): void {
        if (this.channel) {
            this.channel.show();
        }
    }

    /**
     * Cleans up output channel.
     */
    public static dispose(): void {
        if (this.channel) {
            this.channel.dispose();
        }
    }

    private static log(level: string, message: string, category: string): void {
        if (!this.channel) {
            console.error('Logger not initialized');
            return;
        }
        const timestamp = new Date().toISOString();
        this.channel.appendLine(`[${timestamp}] [${level}] [${category}] ${message}`);
    }
}