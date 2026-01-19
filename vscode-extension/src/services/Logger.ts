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
        let detail = '';

        if (error) {
            if (error.name === 'AbortError') {
                detail = 'Request timed out';
            } else if (error.code === 'ECONNREFUSED') {
                detail = 'Connection refused (bridge not running?)';
            } else if (error.code === 'ENOTFOUND') {
                detail = 'Host not found (check IP/hostname)';
            } else if (error instanceof Error) {
                const stack = this.sanitizeStackTrace(error.stack || '');
                detail = `${error.name}: ${error.message}\n${stack}`;
            } else {
                detail = String(error);
            }
        }

        this.log('ERROR', `${message}${detail ? `\n  ${detail}` : ''}`, category);
    }

    /**
     * Removes internal Node.js and VS Code frames from stack traces.
     * Shows only the first 3 application-relevant frames.
     */
    private static sanitizeStackTrace(stack: string): string {
        const lines = stack.split('\n');
        const relevantFrames = lines
            .slice(1)
            .filter(line => {
                if (line.includes('node:internal/')) return false;
                if (line.includes('node_modules')) return false;
                if (line.includes('.vscode-server/bin/')) return false;
                if (line.includes('extensionHostProcess.js')) return false;
                return line.trim().startsWith('at ');
            })
            .slice(0, 3);

        return relevantFrames.length > 0
            ? relevantFrames.join('\n  ')
            : '';
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