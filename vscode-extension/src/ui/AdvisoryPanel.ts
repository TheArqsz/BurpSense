import * as vscode from 'vscode';
import { BurpIssue } from '../types';

/**
 * Webview panel for displaying issue details.
 * Shows issue description, remediation, and HTTP request/response.
 * Singleton pattern - only one panel can be open at a time.
 */
export class AdvisoryPanel {
    public static currentPanel: AdvisoryPanel | undefined;
    private readonly _panel: vscode.WebviewPanel;
    private _disposables: vscode.Disposable[] = [];
    private _currentIssue: BurpIssue;

    private constructor(panel: vscode.WebviewPanel, extensionUri: vscode.Uri, issue: BurpIssue) {
        this._panel = panel;
        this._currentIssue = issue;
        this._panel.onDidDispose(() => this.dispose(), null, this._disposables);

        this._panel.webview.html = this.generateHtml(issue);
    }

    /**
     * Shows advisory panel for an issue.
     * Reuses existing panel if open, otherwise creates new one.
     * 
     * @param issue - Issue to display
     * @param extensionUri - Extension root URI for resources
     */
    public static show(issue: BurpIssue, extensionUri: vscode.Uri) {
        const column = vscode.window.activeTextEditor?.viewColumn;

        if (AdvisoryPanel.currentPanel) {
            AdvisoryPanel.currentPanel._panel.reveal(column);
            AdvisoryPanel.currentPanel.update(issue, extensionUri);
            return;
        }

        const panel = vscode.window.createWebviewPanel(
            'burpAdvisory',
            `Advisory: ${issue.name}`,
            column || vscode.ViewColumn.One,
            {
                enableScripts: true,
                retainContextWhenHidden: true,
                localResourceRoots: [extensionUri]
            }
        );

        AdvisoryPanel.currentPanel = new AdvisoryPanel(panel, extensionUri, issue);
    }

    /**
     * Updates panel with different issue.
     * 
     * @param issue - New issue to display
     * @param extensionUri - Extension root URI
     */
    public update(issue: BurpIssue, extensionUri: vscode.Uri) {
        this._currentIssue = issue;
        this._panel.title = `Advisory: ${issue.name}`;

        this._panel.webview.html = this.generateHtml(issue);
    }

    /**
     * Cleans up panel resources.
     */
    public dispose() {
        AdvisoryPanel.currentPanel = undefined;
        this._panel.dispose();
        while (this._disposables.length) {
            const disposable = this._disposables.pop();
            if (disposable) disposable.dispose();
        }
    }

    /**
     * Generates HTML content for webview.
     * Includes tabs for details, request and response.
     * 
     * @param issue - Issue data
     * @returns HTML string with inline styles and scripts
     */
    private generateHtml(issue: BurpIssue): string {
        const request = this.decodeBase64(issue.request, "No request data available.");
        const response = this.decodeBase64(issue.response, "No response data available.");

        return `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline';">
    <title>Advisory: ${this.escapeHtml(issue.name)}</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: var(--vscode-font-family);
            font-size: var(--vscode-font-size);
            color: var(--vscode-foreground);
            background-color: var(--vscode-editor-background);
            padding: 20px;
            line-height: 1.6;
        }
        
        .header {
            border-bottom: 1px solid var(--vscode-panel-border);
            padding-bottom: 16px;
            margin-bottom: 20px;
        }
        
        .header h1 {
            font-size: 1.5em;
            font-weight: 600;
            margin-bottom: 8px;
        }
        
        .meta {
            display: flex;
            gap: 16px;
            align-items: center;
            font-size: 0.95em;
        }
        
        .severity {
            font-weight: 600;
        }
        
        .severity-HIGH { color: var(--vscode-errorForeground); }
        .severity-MEDIUM { color: var(--vscode-charts-yellow); }
        .severity-LOW { color: var(--vscode-charts-blue); }
        .severity-INFORMATION { color: var(--vscode-foreground); }
        
        .badge {
            background: var(--vscode-badge-background);
            color: var(--vscode-badge-foreground);
            padding: 2px 8px;
            border-radius: 3px;
            font-size: 0.9em;
        }
        
        .url {
            color: var(--vscode-textLink-foreground);
            text-decoration: none;
            font-family: var(--vscode-editor-font-family);
        }
        
        .tabs {
            display: flex;
            gap: 4px;
            border-bottom: 1px solid var(--vscode-panel-border);
            margin-top: 20px;
            margin-bottom: 20px;
        }
        
        .tab {
            padding: 8px 16px;
            background: transparent;
            border: none;
            color: var(--vscode-foreground);
            cursor: pointer;
            font-family: var(--vscode-font-family);
            font-size: var(--vscode-font-size);
            border-bottom: 2px solid transparent;
            opacity: 0.7;
            transition: all 0.2s;
        }
        
        .tab:hover {
            opacity: 1;
            background: var(--vscode-list-hoverBackground);
        }
        
        .tab.active {
            opacity: 1;
            border-bottom-color: var(--vscode-focusBorder);
        }
        
        .tab-content {
            display: none;
        }
        
        .tab-content.active {
            display: block;
        }
        
        .section {
            margin-bottom: 24px;
        }
        
        .section h2 {
            font-size: 1.2em;
            font-weight: 600;
            margin-bottom: 8px;
            color: var(--vscode-foreground);
        }
        
        .section-content {
            color: var(--vscode-descriptionForeground);
            line-height: 1.6;
        }
        
        pre {
            background: var(--vscode-textCodeBlock-background);
            color: var(--vscode-editor-foreground);
            padding: 16px;
            border-radius: 4px;
            overflow-x: auto;
            font-family: var(--vscode-editor-font-family);
            font-size: 0.9em;
            line-height: 1.5;
            border: 1px solid var(--vscode-panel-border);
            max-height: 500px;
            overflow-y: auto;
        }
        
        code {
            font-family: var(--vscode-editor-font-family);
        }
    </style>
</head>
<body>
    <div class="header">
        <h1>[${this.escapeHtml(issue.id)}] ${this.escapeHtml(issue.name)}</h1>
        <div class="meta">
            <span class="severity severity-${issue.severity}">${issue.severity}</span>
            <span class="badge">Confidence: ${issue.confidence}</span>
            <a href="${this.escapeHtml(issue.baseUrl)}" class="url">${this.escapeHtml(issue.baseUrl)}</a>
        </div>
    </div>
    
    <div class="tabs">
        <button class="tab active" data-tab="details">Details</button>
        <button class="tab" data-tab="request">HTTP Request</button>
        <button class="tab" data-tab="response">HTTP Response</button>
    </div>
    
    <div id="details" class="tab-content active">
        <div class="section">
            <h2>Issue Detail</h2>
            <div class="section-content">${issue.detail || "No details provided."}</div>
        </div>
        
        <div class="section">
            <h2>Remediation</h2>
            <div class="section-content">${issue.remediation || "No remediation guidance available."}</div>
        </div>
        
        ${issue.background ? `
        <div class="section">
            <h2>Background</h2>
            <div class="section-content">${issue.background}</div>
        </div>` : ''}
    </div>
    
    <div id="request" class="tab-content">
        <pre><code>${this.escapeHtml(request)}</code></pre>
    </div>
    
    <div id="response" class="tab-content">
        <pre><code>${this.escapeHtml(response)}</code></pre>
    </div>
    
    <script>
        (function() {
            const vscode = acquireVsCodeApi();
            let state = vscode.getState() || { scrollPos: 0, activeTab: 'details' };
            
            function initializeTabs() {
                const tabs = document.querySelectorAll('.tab');
                const contents = document.querySelectorAll('.tab-content');
                
                tabs.forEach(tab => {
                    tab.addEventListener('click', () => {
                        const targetTab = tab.dataset.tab;
                        
                        tabs.forEach(t => t.classList.remove('active'));
                        contents.forEach(c => c.classList.remove('active'));
                        
                        tab.classList.add('active');
                        document.getElementById(targetTab).classList.add('active');
                        
                        state.activeTab = targetTab;
                        vscode.setState(state);
                    });
                });
            }
            
            initializeTabs();
            
            window.scrollTo(0, state.scrollPos);
            
            if (state.activeTab !== 'details') {
                const activeTab = document.querySelector(\`[data-tab="\${state.activeTab}"]\`);
                if (activeTab) activeTab.click();
            }
            
            window.addEventListener('scroll', () => {
                state.scrollPos = window.scrollY;
                vscode.setState(state);
            });
        })();
    </script>
</body>
</html>`;
    }

    /**
     * Decodes base64 HTTP data.
     * Returns fallback text if decoding fails.
     * 
     * @param base64Data - Base64 encoded string
     * @param fallback - Text to return on error
     * @returns Decoded string or fallback
     */
    private decodeBase64(base64Data: string | undefined, fallback: string): string {
        if (!base64Data) return fallback;
        try {
            return Buffer.from(base64Data, 'base64').toString();
        } catch {
            return fallback;
        }
    }

    /**
     * Escapes HTML special characters.
     * Prevents XSS in user-provided content.
     * 
     * @param text - Text to escape
     * @returns HTML-safe string
     */
    private escapeHtml(text: string): string {
        if (!text) return '';
        return text
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
    }
}