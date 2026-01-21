<h1 align="center">BurpSense</h1>
<div align="center">
Burp Suite Integration for VS Code
</div>

---

Bridge the gap between security testing in Burp Suite and your development environment. Map Burp findings directly to source code lines with inline diagnostics, full advisories, and persistent annotations.

![BurpSense Main View](https://raw.githubusercontent.com/TheArqsz/BurpSense/main/assets/main_view.png)

## Features

### **Live Issue Browser**

Browse Burp Suite scan results directly in VS Code. Issues are organized by severity with lazy loading for responsive performance even with hundreds of findings.

### **Code Mapping**

Map security issues to specific lines of code. Right-click any line and select "Map Burp Issue to this Line" to create persistent annotations.

![Context Menu](https://raw.githubusercontent.com/TheArqsz/BurpSense/main/assets/context_menu.png)

### **Smart Suggestions**

The extension analyzes code context and suggests relevant issues. SQL keywords? Get SQL injection issues first. File operations? See path traversal findings prioritized.

### **Problems Integration**

Mapped issues appear as diagnostics in VS Code's Problems panel with appropriate severity indicators. Click any diagnostic to view full details.

![Problems Tab](https://raw.githubusercontent.com/TheArqsz/BurpSense/main/assets/problems_tab.png)

### **Detailed Advisories**

View complete issue details including description, remediation advice and full HTTP request/response data.

![Advisory Panel](https://raw.githubusercontent.com/TheArqsz/BurpSense/main/assets/advisory.png)

### **Drift Detection**

When you refactor code, BurpSense automatically tracks line movements and adjusts mappings. No manual updates needed for typical code reorganization.

![Drift Detection](https://raw.githubusercontent.com/TheArqsz/BurpSense/main/assets/drift.png)

### **Real-time Sync**

WebSocket-based live updates keep your issue list current as Burp discovers new vulnerabilities. Differential sync minimizes network overhead.

### **Powerful Filtering**

- Search by issue name, URL or ID
- Filter by minimum severity (High/Medium/Low/Information)
- Filter by confidence level (Certain/Firm/Tentative)
- Show only in-scope issues
- Quick filter presets for common scenarios

### **Team Collaboration**

Mappings are stored in `.burpsense/mappings.json` and can be committed to version control. Share security findings with your entire team.

## Requirements

**Before using this extension, you need:**

1. **Burp Suite** with Montoya API support (2025.12 or later)
2. **BurpSense Bridge** - A Burp extension that exposes scan results via REST API
   - Download from [GitHub Releases](https://github.com/TheArqsz/BurpSense/releases)
   - Or build from source (requires Java 21)

## Quick Start

### 1. Install the Bridge in Burp Suite

1. Download `burpsense-bridge-*.jar` from [releases](https://github.com/TheArqsz/BurpSense/releases)
2. In Burp Suite, go to **Extensions** > **Add**
3. Select the downloaded JAR file
4. Navigate to the **BurpSense Bridge Settings** tab

![Bridge Settings](https://raw.githubusercontent.com/TheArqsz/BurpSense/main/assets/bridge.png)

### 2. Start the Bridge Server

1. In the Bridge Settings tab, click **"Generate New Key"**
2. Copy the API token
3. Click **"Start Server"** (default: `127.0.0.1:1337`)

### 3. Connect VS Code

1. Open Command Palette (`Ctrl+Shift+P` or `Cmd+Shift+P`)
2. Run `BurpSense: Set API Token`
3. Paste the token from step 2

Check the status bar at the bottom - you should see `BurpSense: Connected [X issues]`

### 4. Start Mapping

1. Open any source file
2. Position cursor on a vulnerable line
3. Right-click > `BurpSense: Map Burp Issue to this Line`
4. Select the relevant issue from the dropdown

The issue now appears in the Problems panel and has a squiggly underline in the editor!

## Extension Settings

Access via File > Preferences > Settings > BurpSense:

- `burpsense.bridgeIp`: Bridge server IP address (default: `127.0.0.1`)
- `burpsense.bridgePort`: Bridge server port (default: `1337`)
- `burpsense.inScopeOnly`: Show only in-scope issues (default: `true`)
- `burpsense.minSeverity`: Minimum severity filter (default: `INFORMATION`)
- `burpsense.minConfidence`: Minimum confidence filter (default: `TENTATIVE`)
- `burpsense.showDriftNotifications`: Notify when mappings auto-adjust (default: `true`)
- `burpsense.confirmMappingDeletion`: Confirm before removing mappings (default: `true`)
- `burpsense.autoCleanOrphanedMappings`: Auto-remove mappings when files deleted (default: `false`)
- `burpsense.logLevel`: Logging verbosity (default: `info`)

## Commands

Access via Command Palette (`Ctrl+Shift+P` or `Cmd+Shift+P`):

**Connection:**
- `BurpSense: Set API Token` - Configure bridge authentication
- `BurpSense: Connect to Bridge` - Manually connect
- `BurpSense: Disconnect from Bridge` - Manually disconnect
- `BurpSense: Check Connection` - Test bridge connectivity

**Mapping:**
- `BurpSense: Map Burp Issue to this Line` - Create mapping at cursor
- `BurpSense: Remove Mapping from this Line` - Delete mapping at cursor
- `BurpSense: Remove multiple Mappings` - Bulk mapping removal
- `BurpSense: Export Mappings` - Save to external JSON file
- `BurpSense: Import Mappings` - Load from external JSON file

**Viewing:**
- `BurpSense: Refresh Issues` - Force refresh from bridge
- `BurpSense: Search Issues` - Text search across all issues
- `BurpSense: Quick Filter Preset` - Apply common filter combinations
- `BurpSense: Show Logs` - Open output panel for troubleshooting

## Troubleshooting

### "Not Connected" in status bar

1. Verify Burp Suite is running
2. Check bridge extension is loaded in Burp (Extensions tab)
3. Ensure server is started (Bridge Settings tab)
4. Verify API token matches
5. Check for port conflicts (default is `1337`)

Click the status bar for quick diagnostics and troubleshooting options.

### Issues not showing

- Verify issues exist in Burp's Target > Site map
- Check filter settings (severity, confidence, scope)
- Try refreshing (toolbar refresh button)
- Check Output panel: `BurpSense: Show Logs`

### Mappings not appearing

- Ensure file paths are relative to workspace root
- Check `.burpsense/mappings.json` for correct paths
- Verify workspace is opened correctly (not just loose files)

## Security & Privacy

- All communication should happen over localhost (127.0.0.1)
- API tokens are stored in VS Code's secure secret storage
- No data is sent to external servers
- Mappings contain only file paths, line numbers, and issue IDs

## More Information

- [GitHub Repository](https://github.com/TheArqsz/BurpSense)
- [Full Documentation](https://github.com/TheArqsz/BurpSense#readme)
- [Report Issues](https://github.com/TheArqsz/BurpSense/issues)
- [Changelog](https://github.com/TheArqsz/BurpSense/blob/main/CHANGELOG.md)

## License

MIT - See [LICENSE](https://github.com/TheArqsz/BurpSense/blob/main/LICENSE.md)
