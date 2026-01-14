# Verify Your Connection

Look at the **status bar** at the bottom-left of VS Code.

**Connected:**
- Shows: `BurpSense: X issues` (where X is the count)
- Color: Blue/green background
- Tooltip shows: Connection URL and issue count

**Disconnected:**
- Shows: `BurpSense: Disconnected`
- Color: Red background
- Auto-reconnect attempts automatically (every 5-60 seconds with exponential backoff)

**Troubleshooting:**
- Click the status bar item for quick actions menu
- Check that Burp Suite is running
- Verify the bridge server is started in Bridge Settings
- Ensure firewall allows the connection
- Check that IP and port match (default: 127.0.0.1:1337)

**Active Search Filter:**
When filtering issues in the Issues View:
- A search indicator appears in the status bar: `🔍 "your search term"`
- Click it to modify or clear the filter