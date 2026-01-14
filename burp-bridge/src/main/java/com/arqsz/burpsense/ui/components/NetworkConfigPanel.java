package com.arqsz.burpsense.ui.components;

import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.arqsz.burpsense.config.BridgeSettings;
import com.arqsz.burpsense.constants.UIConstants;
import com.arqsz.burpsense.ui.validation.PortValidator;

/**
 * Panel for network configuration (IP, port, CORS)
 */
public class NetworkConfigPanel {

    private final JComboBox<String> ipDropdown;
    private final JTextField portField;
    private final JTextField corsField;
    private final JLabel portValidationLabel;
    private final JButton actionButton;
    private final JButton stopButton;

    private final BridgeSettings settings;
    private Runnable onStartRestart;
    private Runnable onStop;

    public NetworkConfigPanel(BridgeSettings settings) {
        this.settings = settings;
        this.ipDropdown = new JComboBox<>();
        this.portField = new JTextField(UIConstants.PORT_FIELD_COLUMNS);
        this.corsField = new JTextField(UIConstants.CORS_FIELD_COLUMNS);
        this.portValidationLabel = new JLabel(" ");
        this.actionButton = new JButton();
        this.stopButton = new JButton();

        initialize();
    }

    private void initialize() {
        DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) ipDropdown.getModel();
        model.addElement("0.0.0.0");
        model.addElement("127.0.0.1");
        ipDropdown.setFont(UIConstants.FONT_BODY);
        ipDropdown.setSelectedItem(settings.getIp());
        ipDropdown.setToolTipText(
                "Select which network interface to listen on. Use 0.0.0.0 to accept connections from any IP.");

        populateAvailableIps(model);

        portField.setFont(UIConstants.FONT_MONOSPACE);
        portField.setText(String.valueOf(settings.getPort()));
        portField.setToolTipText("Port number (1-65535). Ports below 1024 may require administrator privileges.");
        portField.setBorder(
                BorderFactory.createLineBorder(UIManager.getColor("TextField.border"), UIConstants.BORDER_MEDIUM));
        setupPortValidation();

        corsField.setFont(UIConstants.FONT_MONOSPACE_SMALL);
        corsField.setText(String.join(",", settings.getAllowedOrigins()));
        corsField.setToolTipText(
                "Only these origins can access the API via CORS. Separate multiple origins with commas.");

        portValidationLabel.setFont(UIConstants.FONT_SMALL);

        actionButton.setFont(UIConstants.FONT_BODY_BOLD);
        actionButton.setFocusPainted(false);

        stopButton.setText(UIConstants.BUTTON_STOP_SERVER);
        stopButton.setFont(UIConstants.FONT_BODY_BOLD);
        stopButton.setForeground(UIConstants.COLOR_ERROR);
        stopButton.setFocusPainted(false);
        stopButton.setToolTipText("Stop the bridge server");
    }

    /**
     * Builds the network configuration panel
     * 
     * @return The configured JPanel
     */
    public JPanel build() {
        JPanel panel = PanelFactory.createCard();
        panel.setBorder(PanelFactory.createTitledBorder("Network Configuration"));
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(
                UIConstants.PADDING_TINY,
                UIConstants.PADDING_TINY,
                UIConstants.PADDING_TINY,
                UIConstants.PADDING_TINY);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        JLabel ipLabel = new JLabel(UIConstants.LABEL_BIND_ADDRESS);
        ipLabel.setFont(UIConstants.FONT_BODY);
        panel.add(ipLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(ipDropdown, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        JLabel portLabel = new JLabel(UIConstants.LABEL_PORT);
        portLabel.setFont(UIConstants.FONT_BODY);
        panel.add(portLabel, gbc);

        gbc.gridx = 3;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(portField, gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(portValidationLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 4;
        JLabel helpLabel = new JLabel("Use 0.0.0.0 to accept connections from any IP, or 127.0.0.1 for localhost only");
        helpLabel.setFont(UIConstants.FONT_SMALL);
        helpLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        panel.add(helpLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        JLabel corsLabel = new JLabel(UIConstants.LABEL_ALLOWED_ORIGINS);
        corsLabel.setFont(UIConstants.FONT_BODY);
        panel.add(corsLabel, gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(corsField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 4;
        gbc.weightx = 0;
        JLabel corsHelpLabel = new JLabel(
                "Default: http://localhost, http://127.0.0.1 . Use * to allow all origins (not recommended).");
        corsHelpLabel.setFont(UIConstants.FONT_SMALL);
        corsHelpLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        panel.add(corsHelpLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 4;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttonPanel.add(actionButton);
        buttonPanel.add(stopButton);
        panel.add(buttonPanel, gbc);

        return panel;
    }

    /**
     * Updates button states based on server status
     * 
     * @param isRunning Whether the server is running
     */
    public void updateServerStatus(boolean isRunning) {
        if (isRunning) {
            actionButton.setText(UIConstants.BUTTON_RESTART_SERVER);
            actionButton.setToolTipText("Restart the server with new settings");
            stopButton.setEnabled(true);
        } else {
            actionButton.setText(UIConstants.BUTTON_START_SERVER);
            actionButton.setToolTipText("Start the bridge server");
            stopButton.setEnabled(false);
        }
    }

    /**
     * Gets the selected IP address
     */
    public String getSelectedIp() {
        return (String) ipDropdown.getSelectedItem();
    }

    /**
     * Gets the entered port number
     */
    public int getPort() throws NumberFormatException {
        return Integer.parseInt(portField.getText().trim());
    }

    /**
     * Gets the CORS origins text
     */
    public String getCorsText() {
        return corsField.getText().trim();
    }

    /**
     * Sets callbacks for button actions
     */
    public void setCallbacks(Runnable onStartRestart, Runnable onStop) {
        this.onStartRestart = onStartRestart;
        this.onStop = onStop;

        actionButton.addActionListener(e -> {
            if (onStartRestart != null) {
                onStartRestart.run();
            }
        });

        stopButton.addActionListener(e -> {
            if (onStop != null) {
                onStop.run();
            }
        });
    }

    /**
     * Enables or disables the action button
     */
    public void setActionButtonEnabled(boolean enabled) {
        actionButton.setEnabled(enabled);
    }

    /**
     * Sets the action button text
     */
    public void setActionButtonText(String text) {
        actionButton.setText(text);
    }

    private void setupPortValidation() {
        portField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                validatePort();
            }

            public void removeUpdate(DocumentEvent e) {
                validatePort();
            }

            public void changedUpdate(DocumentEvent e) {
                validatePort();
            }
        });
    }

    private void validatePort() {
        String text = portField.getText().trim();
        PortValidator.ValidationResult result = PortValidator.validate(text);

        portValidationLabel.setText(result.getMessage());

        switch (result.getLevel()) {
            case ERROR:
                portValidationLabel.setForeground(UIConstants.COLOR_ERROR);
                portField.setBorder(BorderFactory.createLineBorder(UIConstants.COLOR_ERROR, UIConstants.BORDER_MEDIUM));
                actionButton.setEnabled(false);
                break;
            case WARNING:
                portValidationLabel.setForeground(UIConstants.COLOR_WARNING);
                portField.setBorder(BorderFactory.createLineBorder(UIConstants.COLOR_WARNING, UIConstants.BORDER_THIN));
                actionButton.setEnabled(true);
                break;
            case VALID:
                portValidationLabel.setText(" ");
                portField.setBorder(BorderFactory.createLineBorder(
                        UIManager.getColor("Component.borderColor"), UIConstants.BORDER_MEDIUM));
                actionButton.setEnabled(true);
                break;
        }
    }

    private void populateAvailableIps(DefaultComboBoxModel<String> model) {
        CompletableFuture.runAsync(() -> {
            try {
                var interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
                for (var ni : interfaces) {
                    if (!ni.isUp() || ni.isLoopback()) {
                        continue;
                    }

                    Collections.list(ni.getInetAddresses()).stream()
                            .filter(addr -> addr instanceof Inet4Address)
                            .forEach(addr -> {
                                String ip = addr.getHostAddress();
                                SwingUtilities.invokeLater(() -> {
                                    if (model.getIndexOf(ip) == -1) {
                                        model.addElement(ip);
                                    }
                                });
                            });
                }
            } catch (Exception e) {
            }
        });
    }
}