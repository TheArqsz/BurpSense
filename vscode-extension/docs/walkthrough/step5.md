# Map Your First Issue

**Steps:**
1. Run a scan in Burp Suite or browse your site map to generate issues
2. In VS Code, open a file from your project
3. Click on the line of code you want to map
4. Right-click and select **"BurpSense: Map Burp Issue to this Line"**
5. Choose an issue from the list

**Result:**
- Issue appears in the **Problems panel** (Ctrl+Shift+M / Cmd+Shift+M)
- Yellow/red squiggle appears under the mapped line
- Hover to see issue details and Quick Fix options
- Click lightbulb (💡) or press Ctrl+. for quick actions:
  - **View Burp Remediation Details** - Opens full advisory panel
  - **Remove Burp Mapping** - Removes the mapping

**Smart Suggestions:**
BurpSense automatically suggests relevant issues based on your code:
- SQL-related code -> SQL injection issues
- File operations -> Path traversal issues
- HTML rendering -> XSS issues
- Authentication code -> Auth/session issues

**Recently Mapped:**
Your most recent mappings appear at the top for quick re-mapping.

**Code Changes:**
BurpSense automatically tracks your mappings when code changes:
- **Minor edits** (e.g. `error` -> `errors`): Mapping stays in place, text updates
- **Line movements**: Mapping follows the code to new location
- **Major changes**: Shows "Mapping lost" warning with original text
- **Notification**: First time per session, you'll see a summary of auto-adjustments