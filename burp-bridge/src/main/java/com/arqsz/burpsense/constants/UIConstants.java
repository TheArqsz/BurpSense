package com.arqsz.burpsense.constants;

import java.awt.Color;
import java.awt.Font;

/**
 * Constants for UI configuration
 */
public final class UIConstants {

    public static final Color COLOR_SUCCESS = new Color(0, 150, 0);
    public static final Color COLOR_ERROR = new Color(244, 67, 54);
    public static final Color COLOR_WARNING = new Color(255, 165, 0);
    public static final Color COLOR_INACTIVE = new Color(128, 128, 128);

    public static final Font FONT_TITLE = new Font("SansSerif", Font.BOLD, 14);
    public static final Font FONT_BODY = new Font("SansSerif", Font.PLAIN, 12);
    public static final Font FONT_BODY_BOLD = new Font("SansSerif", Font.BOLD, 12);
    public static final Font FONT_SMALL = new Font("SansSerif", Font.PLAIN, 11);
    public static final Font FONT_SMALL_BOLD = new Font("SansSerif", Font.BOLD, 11);
    public static final Font FONT_MONOSPACE = new Font("Monospaced", Font.PLAIN, 12);
    public static final Font FONT_MONOSPACE_SMALL = new Font("Monospaced", Font.PLAIN, 11);

    public static final int PADDING_LARGE = 20;
    public static final int PADDING_MEDIUM = 16;
    public static final int PADDING_SMALL = 12;
    public static final int PADDING_TINY = 5;

    public static final int GAP_LARGE = 12;
    public static final int GAP_MEDIUM = 10;
    public static final int GAP_SMALL = 5;

    public static final int PORT_FIELD_COLUMNS = 10;
    public static final int CORS_FIELD_COLUMNS = 40;
    public static final int SCROLLPANE_HEIGHT = 250;

    public static final int BORDER_THIN = 1;
    public static final int BORDER_MEDIUM = 2;

    public static final String STATUS_ONLINE = "Status: Online";
    public static final String STATUS_OFFLINE = "Status: Offline";
    public static final String STATUS_LISTENING_FORMAT = "Listening on %s:%d";
    public static final String STATUS_NOT_RUNNING = "Server is not running";

    public static final String BUTTON_START_SERVER = "Start Server";
    public static final String BUTTON_RESTART_SERVER = "Restart Server";
    public static final String BUTTON_STOP_SERVER = "Stop Server";
    public static final String BUTTON_GENERATE_KEY = "Generate New Key";
    public static final String BUTTON_RENAME = "Rename";
    public static final String BUTTON_REVOKE = "Revoke";
    public static final String BUTTON_COPY_TO_CLIPBOARD = "Copy to Clipboard";

    public static final String LABEL_BIND_ADDRESS = "Bind Address:";
    public static final String LABEL_PORT = "Port:";
    public static final String LABEL_ALLOWED_ORIGINS = "Allowed Origins:";
    public static final String LABEL_API_KEYS = "API Keys";

    public static final String TAB_NAME = "BurpSense Bridge Settings";

    public static final String EXTENSION_NAME = "BurpSense Bridge";

    public static final int PORT_MIN = 1;
    public static final int PORT_MAX = 65535;
    public static final int PORT_PRIVILEGED_THRESHOLD = 1024;

    private UIConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}