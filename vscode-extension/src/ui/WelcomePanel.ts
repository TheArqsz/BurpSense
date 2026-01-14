import * as vscode from 'vscode';
import { COMMANDS, CONFIG_SECTION, EXTENSION_ID, WALKTHROUGH } from '../constants';

/**
 * Welcome panel shown on first launch.
 * Guides users through setup steps.
 */
export class WelcomePanel {

    /**
     * Displays welcome panel.
     * Handles button clicks via message passing.
     * 
     * @param context - Extension context for subscriptions
     */
    public static show(context: vscode.ExtensionContext): void {
        const panel = vscode.window.createWebviewPanel(
            'burpsenseWelcome',
            'Welcome to BurpSense',
            vscode.ViewColumn.One,
            {
                enableScripts: true,
                retainContextWhenHidden: true
            }
        );

        panel.webview.html = this.getHtmlContent();

        panel.webview.onDidReceiveMessage(
            async message => {
                switch (message.command) {
                    case 'setToken':
                        await vscode.commands.executeCommand(COMMANDS.SET_TOKEN);
                        break;
                    case 'openSettings':
                        await vscode.commands.executeCommand('workbench.action.openSettings', CONFIG_SECTION);
                        break;
                    case 'checkConnection':
                        await vscode.commands.executeCommand(COMMANDS.CHECK_CONNECTION);
                        break;
                    case 'openWalkthrough':
                        await vscode.commands.executeCommand('workbench.action.openWalkthrough', `${EXTENSION_ID}#${WALKTHROUGH.ID}`);
                        break;
                    case 'dismiss':
                        panel.dispose();
                        break;
                }
            },
            undefined,
            context.subscriptions
        );
    }

    /**
     * Generates welcome panel HTML.
     * Includes setup instructions and action buttons.
     * 
     * @returns HTML content with inline styles
     */
    private static getHtmlContent(): string {
        return `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline';">
    <title>Welcome to BurpSense</title>
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
            padding: 40px;
            max-width: 800px;
            margin: 0 auto;
        }
        
        .hero {
            text-align: center;
            margin-bottom: 40px;
        }
        
        .hero h1 {
            font-size: 2.5em;
            font-weight: 600;
            margin-bottom: 12px;
            color: var(--vscode-foreground);
        }
        
        .hero p {
            font-size: 1.2em;
            color: var(--vscode-descriptionForeground);
        }
        
        .features {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
            gap: 20px;
            margin-bottom: 40px;
        }
        
        .feature {
            padding: 20px;
            background: var(--vscode-sideBar-background);
            border: 1px solid var(--vscode-panel-border);
            border-radius: 6px;
        }
        
        .feature h3 {
            font-size: 1.1em;
            margin-bottom: 8px;
            color: var(--vscode-foreground);
        }
        
        .feature p {
            color: var(--vscode-descriptionForeground);
            font-size: 0.95em;
            line-height: 1.5;
        }
        
        .setup {
            background: var(--vscode-textCodeBlock-background);
            border: 1px solid var(--vscode-panel-border);
            border-radius: 6px;
            padding: 24px;
            margin-bottom: 30px;
        }
        
        .setup h2 {
            font-size: 1.5em;
            margin-bottom: 20px;
            color: var(--vscode-foreground);
        }
        
        .steps {
            list-style: none;
        }
        
        .step {
            display: flex;
            gap: 16px;
            margin-bottom: 20px;
            align-items: flex-start;
        }
        
        .step-number {
            flex-shrink: 0;
            width: 32px;
            height: 32px;
            background: var(--vscode-button-background);
            color: var(--vscode-button-foreground);
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: 600;
        }
        
        .step-content h3 {
            font-size: 1em;
            margin-bottom: 6px;
            color: var(--vscode-foreground);
        }
        
        .step-content p {
            color: var(--vscode-descriptionForeground);
            font-size: 0.95em;
            margin-bottom: 8px;
        }
        
        .button {
            display: inline-block;
            padding: 8px 16px;
            background: var(--vscode-button-background);
            color: var(--vscode-button-foreground);
            border: none;
            border-radius: 4px;
            cursor: pointer;
            font-family: var(--vscode-font-family);
            font-size: var(--vscode-font-size);
            text-decoration: none;
            transition: background 0.2s;
        }
        
        .button:hover {
            background: var(--vscode-button-hoverBackground);
        }
        
        .button-secondary {
            background: var(--vscode-button-secondaryBackground);
            color: var(--vscode-button-secondaryForeground);
        }
        
        .button-secondary:hover {
            background: var(--vscode-button-secondaryHoverBackground);
        }
        
        .actions {
            display: flex;
            gap: 12px;
            justify-content: center;
            margin-top: 30px;
        }
        
        .tip {
            background: var(--vscode-editor-infoForeground);
            color: var(--vscode-foreground);
            border-left: 4px solid var(--vscode-editorInfo-foreground);
            padding: 12px 16px;
            border-radius: 4px;
            margin-top: 20px;
            font-size: 0.95em;
        }
    </style>
</head>
<body>
    <div class="hero">
        <h1>Welcome to BurpSense</h1>
        <p>Bring Burp Suite security findings directly into VS Code</p>
    </div>
    
    <div class="features">
        <div class="feature">
            <h3>Live Issue Tracking</h3>
            <p>See Burp Suite findings in real-time as you code</p>
        </div>
        <div class="feature">
            <h3>Code Mapping</h3>
            <p>Link security issues to specific lines of code</p>
        </div>
        <div class="feature">
            <h3>Smart Diagnostics</h3>
            <p>Issues appear in Problems panel with remediation guidance</p>
        </div>
    </div>
    
    <div class="setup">
        <h2>Quick Setup</h2>
        <ol class="steps">
            <li class="step">
                <div class="step-number">1</div>
                <div class="step-content">
                    <h3>Load BurpSense Bridge in Burp Suite</h3>
                    <p>Open Burp Suite, go to Extensions and load the BurpSense Bridge extension (JAR file).</p>
                </div>
            </li>
            
            <li class="step">
                <div class="step-number">2</div>
                <div class="step-content">
                    <h3>Start the Bridge Server</h3>
                    <p>In Burp Suite, navigate to the "BurpSense Bridge Settings" tab and click "Start Server".</p>
                </div>
            </li>
            
            <li class="step">
                <div class="step-number">3</div>
                <div class="step-content">
                    <h3>Generate an API Key</h3>
                    <p>In the Bridge Settings tab, click "Generate New Key" and copy the token.</p>
                    <button class="button" onclick="handleSetToken()">Set API Token</button>
                </div>
            </li>
            
            <li class="step">
                <div class="step-number">4</div>
                <div class="step-content">
                    <h3>Verify Connection</h3>
                    <p>Check the status bar at the bottom of VS Code. You should see "BurpSense: Connected".</p>
                    <button class="button button-secondary" onclick="handleCheckConnection()">Check Connection</button>
                </div>
            </li>
        </ol>
        
        <div class="tip">
            💡 <strong>Tip:</strong> Use the status bar to quickly access common actions like refreshing issues or adjusting filters.
        </div>
    </div>
    
    <div class="actions">
        <button class="button" onclick="handleOpenWalkthrough()">Open Interactive Guide</button>
        <button class="button button-secondary" onclick="handleOpenSettings()">Configure Settings</button>
        <button class="button button-secondary" onclick="handleDismiss()">Get Started</button>
    </div>
    
    <script>
        const vscode = acquireVsCodeApi();
        
        function handleSetToken() {
            vscode.postMessage({ command: 'setToken' });
        }
        
        function handleCheckConnection() {
            vscode.postMessage({ command: 'checkConnection' });
        }
        
        function handleOpenWalkthrough() {
            vscode.postMessage({ command: 'openWalkthrough' });
        }
        
        function handleOpenSettings() {
            vscode.postMessage({ command: 'openSettings' });
        }
        
        function handleDismiss() {
            vscode.postMessage({ command: 'dismiss' });
        }
    </script>
</body>
</html>`;
    }
}