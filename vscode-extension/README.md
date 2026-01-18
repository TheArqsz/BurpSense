<h1 align="center">BurpSense</h1>
<div align="center">
  
[![Build and Publish Release](https://github.com/TheArqsz/BurpSense/actions/workflows/release.yml/badge.svg)](https://github.com/TheArqsz/BurpSense/actions/workflows/release.yml)


![Visual Studio Marketplace Version](https://img.shields.io/visual-studio-marketplace/v/arqsz.burpsense?include_prereleases&style=flat-square&label=Visual%20Studio%20Marketplace&color=blue)
![Open VSX Version](https://img.shields.io/open-vsx/v/arqsz/burpsense?style=flat-square&label=Open%20VSX%20Marketplace)

</div>

BurpSense bridges the gap between security testing in Burp Suite and your development environment in VS Code. The core idea is simple: instead of constantly switching between tools to cross-reference vulnerabilities with source code, you can map Burp's findings directly to the lines where issues occur. This gives you inline diagnostics, full advisory details and a persistent record of what needs attention.

![alt text](assets/main_view.png)

## What this solves?

If you've done web application security testing, you've probably experienced the friction of correlating Burp Suite findings with actual source code. You find an SQL injection, note the URL and parameter, then hunt through your codebase to figure out where that endpoint lives. Then you need to remember which file and line to fix, or worse, communicate this to someone else on your team who wasn't sitting next to you during testing.

Burp Suite does have a built-in API integration feature, but if you've tried using it, you know it's... let's say "minimalist".

![Burp API](assets/burp_off_api.png)

The extension eliminates most of this friction. The Burp bridge exposes scan results through a clean local API and the VS Code extension consumes them in real-time. You get a live view of all findings and you can annotate specific lines of code with issues. These annotations persist in your workspace, so you can pick up where you left off without losing context.

## How it works?

The project has two components:

The **Burp bridge** is a Java extension that runs inside Burp Suite. It starts an HTTP server on `localhost` (default port `1337`) and exposes your scan results through a REST API. It also maintains WebSocket connections to push updates when new issues appear or existing ones get removed. The bridge applies your filters (severity, confidence, scope) server-side, so only relevant issues get sent to clients.

The **VS Code extension** connects to this bridge and displays issues in a dedicated tree view. You can browse, search, and filter findings, then map them to specific lines in your source files. Mapped issues appear as diagnostics in the Problems panel, and clicking on one opens a detailed advisory with full HTTP request/response data. When you refactor code and lines move around, the extension attempts to track those changes and adjust mappings automatically.

Communication happens over HTTP for queries and WebSocket for real-time updates. The protocol is differential - after the initial sync, the bridge only sends what's changed (new issues, removed issues) rather than the entire dataset each time.

## Installation and setup

### Bridge extension (Burp Suite side)

![the Bridge](assets/bridge.png)

Start by building the bridge:

```bash
cd burp-bridge
mvn clean package
```

This produces `target/burpsense-bridge-*.jar`. Load it in Burp Suite through the Extensions tab (click "Add" and select the JAR). After loading, you'll see a new "BurpSense Bridge Settings" tab appear.

Generate an API token using the "Generate New Key" button and copy it somewhere safe. Then click "Start Server" to begin listening. By default, the server binds to `127.0.0.1:1337`, but you can change this in the settings panel if needed.

> The bridge requires Burp Suite with the new Montoya API support.

### VS Code extension

You can install from [VSCode Marketplace](https://marketplace.visualstudio.com/items?itemName=arqsz.burpsense), [Open VSX Registry](https://open-vsx.org/extension/arqsz/burpsense), a packaged VSIX or run from source:

**From VSIX:**

```bash
cd vscode-extension
npm install
npm run compile
npx vsce package
```

This creates `burpsense-*.vsix`. In VS Code, go to the Extensions view (`Ctrl+Shift+X`), click the "..." menu, and select "Install from VSIX".

**From source (for development):**

```bash
cd vscode-extension
npm install
npm run compile
```

Then press `F5` in VS Code to launch the Extension Development Host with the extension loaded and debugger attached.

### Connecting the two

After installing both components, run `BurpSense: Set API Token` from the command palette in VS Code and paste the token you generated in Burp. If everything is working, you should see `BurpSense: Connected [X issues]` in the status bar at the bottom of your editor. If it says "Disconnected", click the status bar for troubleshooting options.

## Using the extension

### Browsing issues

The BurpSense view appears in the activity bar (the shield icon on the left side). Opening it shows a tree of issues organized by severity. Each issue displays its name, confidence level, and affected URL. The tree uses lazy loading, so it stays responsive even when you have hundreds of findings.

You can filter issues in several ways:

- Text search (searches names, URLs and issue IDs)
- Minimum severity (high, medium, low, information)
- Minimum confidence (certain, firm, tentative)
- Scope filter (show only in-scope issues)

The toolbar icons let you adjust these filters on the fly. There's also a "quick filter" menu that applies common presets like "high severity only" or "in-scope high and medium".

### Mapping issues to code

To map an issue to a line of code:

1. Open the file and position your cursor on the relevant line
2. Right-click and select `BurpSense: Map Burp Issue to this Line`

![context menu](assets/context_menu.png)

3. Choose the issue from the dropdown

![select issues](assets/issue_select.png)

The mapping gets saved to `.burpsense/mappings.json` in your workspace. This file can be committed to version control so team members see the same annotations.

Mapped issues appear in two places: the Problems panel (with appropriate severity icons) and directly in the editor as squiggly underlines. Click any diagnostic to open a detailed advisory showing the full issue description, remediation advice, and HTTP traffic if available.

![Problems tab](assets/problems_tab.png)

![advisory](assets/advisory.png)

### Drift detection

![Drift](assets/drift.png)

When you refactor code, lines move around. BurpSense tries to keep mappings accurate by checking whether the text you originally mapped still exists nearby. When you edit a file with mappings, the extension:

1. Checks if the exact text still appears on the mapped line
2. If not, searches within +/- 20 lines for similar content (>80% match)
3. Calculates similarity using character-level comparison with position weighting
4. Updates the line number if it finds a good match

This works well for typical refactoring like adding functions or moving code blocks. It won't track cases where you completely rewrite the vulnerable code, but that's intentional - if the code changed that much, the original issue may no longer apply and you should verify it manually.

The extension can notify you when it adjusts mappings, though you can disable these notifications in settings if they're distracting.

### Removing mappings

Remove a single mapping by right-clicking the line and selecting `BurpSense: Remove Mapping from this Line`. To remove all mappings for a particular issue, hover over it in the tree view and click the trashbin icon. For bulk removal, use `BurpSense: Remove multiple Mappings` from the command palette.

### Sharing mappings with your team

The mappings file (`.burpsense/mappings.json`) is workspace-specific and can be committed to your repository. This means when a teammate clones the project and connects their own VS Code to a shared Burp Project, they'll see the same annotations you created. They'll need their own bridge API token, but the mappings themselves are just file paths and line numbers with issue IDs.

You can also export mappings to a standalone JSON file and import them in a different workspace if needed with dedicated commands.

## Configuration

Settings live in VS Code preferences under the "BurpSense" section:

```json
{
  "burpsense.bridgeIp": "127.0.0.1",
  "burpsense.bridgePort": 1337,
  "burpsense.inScopeOnly": true,
  "burpsense.minSeverity": "INFORMATION",
  "burpsense.minConfidence": "TENTATIVE",
  "burpsense.showDriftNotifications": true,
  "burpsense.confirmMappingDeletion": true,
  "burpsense.autoCleanOrphanedMappings": false
}
```

The IP and port should match where your bridge is listening. If you're running Burp on a different machine, adjust accordingly. The extension will reconnect automatically when these settings change.

Filter settings (severity, confidence, scope) apply server-side, which means the bridge only sends issues matching your criteria. Changing filters triggers a refetch, so there might be a brief delay while the new filtered set loads.

Drift notifications can be disabled if you find them noisy. The extension will still adjust mappings, just without notifying you.

Auto-clean orphaned mappings, when enabled, removes mappings for files that get deleted. This is disabled by default because you might temporarily remove files during refactoring and want the mappings to persist.

## Technical details

### Synchronization protocol

The extension uses a hybrid push/pull model. On initial load, it fetches all issues via `GET /issues`. Subsequent requests to `POST /issues` include the IDs of issues the client already knows about, and the bridge responds with:

```json
{
  "newIssues": [...],
  "removedIds": [...]
}
```

This differential sync significantly reduces network traffic during active scanning sessions where issues frequently appear and disappear.

WebSocket connections handle real-time updates. The bridge monitors its issue list every 2 seconds and broadcasts a refresh message when the count changes. Clients respond by fetching an incremental update.

### Caching strategy

Issues are cached locally with a 30 second TTL. After expiration, the tree view fetches fresh data the next time you expand a node. This avoids hammering the bridge with requests while keeping the display reasonably current.

The cache has two levels:

- Issue-level cache (raw scan data)
- Grouped data cache (tree nodes organized by severity/name)

When an incremental update arrives, both caches are invalidated, but the tree doesn't refetch unless something actually changed. This keeps the UI responsive even during heavy scanning activity.

### Performance characteristics

The initial load with several hundred issues can take a few seconds while the tree builds its internal structure. After that, navigation is fast because the tree uses lazy loading - child nodes aren't created until you expand their parent.

The extension processes incremental updates synchronously, which means if you have a large removal event (like changing filters to exclude 500 issues), there might be a noticeable pause. This is a trade-off for keeping the sync logic simple.

## Known quirks and limitations

### Manual disconnect behavior

If you manually disconnect using the status bar, the extension won't fetch data for any operation, including opening issue details. This is intentional - when you're disconnected, you're working with stale cache only. Reconnecting re-enables all fetches.

### Cache invalidation timing

When the cache expires (after 30 seconds), the extension doesn't immediately refetch. It waits until you next interact with the tree view. This is by design to avoid background fetches when you're not actively looking at the extension.

### Large issue sets

With 1000+ issues, the tree can feel sluggish. This is partly because VS Code's tree view API isn't optimized for that many items, and partly because the grouping logic has to sort and organize everything. Consider using stricter filters to reduce the working set.

### WebSocket reconnection gap

If the Burp bridge restarts, the WebSocket takes a few seconds to reconnect. During this window, you won't receive real-time updates, though the extension continues working with cached data. Once the connection re-establishes, you'll get an incremental sync of anything that changed.

### Mapping file format

The mappings file is plain JSON and gets rewritten entirely on every change. This means if two people edit mappings simultaneously, the last write wins. Git can handle this in most cases, but be aware that mappings don't merge intelligently across branches.

## Troubleshooting

### Status bar shows "Not Connected"

Work through these in order:

1. Is Burp Suite running?
2. Is the bridge extension loaded? (Check Extensions tab in Burp)
3. Is the server started? (Check Bridge Settings tab)
4. Does your API token match between Burp and VS Code?
5. Is something blocking set port? (Firewall, another service)

The status bar is clickable and opens a quick actions menu with diagnostics. Check the VS Code output panel for connection logs.

### Issues aren't showing up

First, verify the issues actually exist in Burp's sitemap. Then check your filter settings - you might have the minimum severity set too high, or scope filtering enabled when the issues aren't in scope. Try clicking the refresh button in the tree view toolbar. If that doesn't work, check the output panel for errors.

### Mappings not appearing as diagnostics

Make sure the file path in the mappings matches your workspace structure exactly. The extension uses paths relative to the workspace root, so if your workspace root isn't what you expect, mappings might not resolve. Check `.burpsense/mappings.json` to see what paths are stored, and verify they match your actual file locations.

### Drift detection moving mappings incorrectly

The similarity algorithm is conservative (80% match threshold), but false positives can still happen if you have very similar code repeated in nearby lines. If drift detection is causing problems, you can turn off notifications in settings. The extension will still adjust mappings, but you can check the Problems panel to verify they're still pointing to the right code.

## Development and contributing

To build from source, you need Maven for the bridge and Node.js for the extension.

### Bridge

```bash
cd burp-bridge
mvn clean package
```

Output is in `target/burpsense-bridge-*.jar`. Load this in Burp's Extensions tab.

### Extension

```bash
cd vscode-extension
npm install
npm run compile
```

For active development, use `npm run watch` to recompile automatically when you change files. Press F5 in VS Code to launch the Extension Development Host with debugger attached.

Issues and pull requests are welcome. For major changes, please open an issue first to discuss the approach. When reporting bugs, include VS Code and Burp Suite versions, extension version, and logs from both components (VS Code output panel and Burp's extension output tab).

## Project structure

```
burpsense/
├── burp-bridge/
│   └── src/main/java/com/arqsz/burpsense/
│       ├── api/              # HTTP endpoints and handlers
│       ├── service/          # Issue filtering and management
│       ├── config/           # Settings persistence
│       └── ui/               # Bridge settings panel
│
└── vscode-extension/
    └── src/
        ├── commands/         # Command palette handlers
        ├── providers/        # Tree view, diagnostics, code actions
        ├── services/         # Connection, caching, mapping
        ├── ui/               # Webview panels for advisories
        └── extension.ts      # Entry point and activation
```

## License

[MIT](LICENSE.md)

## Acknowledgments

Built using Burp Suite's Montoya API, Undertow for HTTP/WebSocket serving and node-fetch/ws for client networking.
