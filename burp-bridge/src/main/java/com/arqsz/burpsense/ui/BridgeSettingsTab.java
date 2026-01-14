package com.arqsz.burpsense.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.HierarchyEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

import com.arqsz.burpsense.api.BridgeServer;
import com.arqsz.burpsense.config.ApiKey;
import com.arqsz.burpsense.config.BridgeSettings;
import com.arqsz.burpsense.constants.ServerConstants;
import com.arqsz.burpsense.constants.UIConstants;
import com.arqsz.burpsense.ui.components.NetworkConfigPanel;
import com.arqsz.burpsense.ui.components.StatusPanelBuilder;

import burp.api.montoya.MontoyaApi;

/**
 * UI tab for bridge settings
 */
public class BridgeSettingsTab extends JPanel {

    private final MontoyaApi api;
    private final BridgeSettings settings;
    private final BridgeServer bridgeServer;
    private final Runnable restartCallback;

    private final StatusPanelBuilder statusPanelBuilder;
    private final NetworkConfigPanel networkConfigPanel;
    private final JPanel keysContainer;

    public BridgeSettingsTab(
            MontoyaApi api,
            BridgeSettings settings,
            BridgeServer bridgeServer,
            Runnable restartCallback) {

        this.api = api;
        this.settings = settings;
        this.bridgeServer = bridgeServer;
        this.restartCallback = restartCallback;

        this.statusPanelBuilder = new StatusPanelBuilder();
        this.networkConfigPanel = new NetworkConfigPanel(settings);
        this.keysContainer = new JPanel();

        initializeLayout();
        setupListeners();

        updateStatus();
        refreshKeys();
    }

    private void initializeLayout() {
        setLayout(new BorderLayout(0, UIConstants.GAP_LARGE));
        setBorder(new EmptyBorder(
                UIConstants.PADDING_LARGE,
                UIConstants.PADDING_LARGE,
                UIConstants.PADDING_LARGE,
                UIConstants.PADDING_LARGE));

        JPanel mainContent = new JPanel();
        mainContent.setLayout(new BoxLayout(mainContent, BoxLayout.Y_AXIS));

        mainContent.add(statusPanelBuilder.build());
        mainContent.add(Box.createVerticalStrut(UIConstants.GAP_LARGE));

        mainContent.add(networkConfigPanel.build());
        mainContent.add(Box.createVerticalStrut(UIConstants.GAP_LARGE));

        mainContent.add(createKeysPanel());

        add(mainContent, BorderLayout.NORTH);
    }

    private void setupListeners() {
        networkConfigPanel.setCallbacks(
                this::handleStartRestart,
                this::handleStop);

        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) {
                    updateStatus();
                    refreshKeys();
                }
            }
        });
    }

    private JPanel createKeysPanel() {
        JPanel panel = new JPanel(new BorderLayout(UIConstants.GAP_MEDIUM, UIConstants.GAP_MEDIUM));
        panel.setBackground(UIManager.getColor("Panel.background"));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground"), UIConstants.BORDER_THIN),
                new EmptyBorder(
                        UIConstants.PADDING_MEDIUM,
                        UIConstants.PADDING_MEDIUM,
                        UIConstants.PADDING_MEDIUM,
                        UIConstants.PADDING_MEDIUM)));

        JPanel header = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel(UIConstants.LABEL_API_KEYS);
        titleLabel.setFont(UIConstants.FONT_TITLE);
        header.add(titleLabel, BorderLayout.WEST);

        JButton generateButton = new JButton(UIConstants.BUTTON_GENERATE_KEY);
        generateButton.setFont(UIConstants.FONT_BODY);
        generateButton.setFocusPainted(false);
        generateButton.setToolTipText("Create a new API key");
        generateButton.addActionListener(e -> showGenerateDialog());
        header.add(generateButton, BorderLayout.EAST);

        panel.add(header, BorderLayout.NORTH);

        keysContainer.setLayout(new BoxLayout(keysContainer, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(keysContainer);
        scrollPane.setBorder(null);
        scrollPane.setPreferredSize(new Dimension(0, UIConstants.SCROLLPANE_HEIGHT));

        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Updates the status display
     */
    public void updateStatus() {
        if (bridgeServer.isRunning()) {
            statusPanelBuilder.updateOnline(settings.getIp(), settings.getPort());
            networkConfigPanel.updateServerStatus(true);
        } else {
            statusPanelBuilder.updateOffline();
            networkConfigPanel.updateServerStatus(false);
        }
    }

    /**
     * Refreshes the API keys display
     */
    private void refreshKeys() {
        keysContainer.removeAll();

        var keys = settings.getApiKeys();

        if (keys.isEmpty()) {
            keysContainer.add(createEmptyState());
        } else {
            for (int i = 0; i < keys.size(); i++) {
                keysContainer.add(createKeyCard(keys.get(i), i));
                if (i < keys.size() - 1) {
                    keysContainer.add(Box.createVerticalStrut(0));
                }
            }
        }

        keysContainer.revalidate();
        keysContainer.repaint();
    }

    private JPanel createKeyCard(ApiKey key, int index) {
        JPanel card = new JPanel(new BorderLayout(UIConstants.GAP_MEDIUM, UIConstants.GAP_SMALL));
        card.setBackground(UIManager.getColor("Panel.background"));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")),
                new EmptyBorder(
                        UIConstants.PADDING_SMALL,
                        UIConstants.PADDING_SMALL,
                        UIConstants.PADDING_SMALL,
                        UIConstants.PADDING_SMALL)));

        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));

        JLabel nameLabel = new JLabel(key.name());
        nameLabel.setFont(UIConstants.FONT_BODY_BOLD);

        String details = "Created: " + key.createdDate();
        if (key.lastUsed() != null) {
            details += "  /  Last used: " + key.lastUsed();
        }
        JLabel detailsLabel = new JLabel(details);
        detailsLabel.setFont(UIConstants.FONT_SMALL);
        detailsLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        leftPanel.add(nameLabel);
        leftPanel.add(Box.createVerticalStrut(3));
        leftPanel.add(detailsLabel);

        JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, UIConstants.GAP_SMALL, 0));

        JButton renameButton = new JButton(UIConstants.BUTTON_RENAME);
        renameButton.setFont(UIConstants.FONT_SMALL);
        renameButton.setFocusPainted(false);
        renameButton.setToolTipText("Rename this API key");
        renameButton.addActionListener(e -> handleRename(key, index));

        JButton revokeButton = new JButton(UIConstants.BUTTON_REVOKE);
        revokeButton.setFont(UIConstants.FONT_SMALL_BOLD);
        revokeButton.setForeground(UIConstants.COLOR_ERROR);
        revokeButton.setFocusPainted(false);
        revokeButton.setToolTipText("Revoke this API key (cannot be undone)");
        revokeButton.addActionListener(e -> handleRevoke(key, index));

        actionsPanel.add(renameButton);
        actionsPanel.add(revokeButton);

        card.add(leftPanel, BorderLayout.CENTER);
        card.add(actionsPanel, BorderLayout.EAST);

        return card;
    }

    private JPanel createEmptyState() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(40, 20, 40, 20));

        JLabel titleLabel = new JLabel("No API keys configured");
        titleLabel.setFont(UIConstants.FONT_TITLE);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel messageLabel = new JLabel(
                "Generate your first API key to allow VS Code extensions and other clients to connect.");
        messageLabel.setFont(UIConstants.FONT_BODY);
        messageLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        messageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton generateButton = new JButton(UIConstants.BUTTON_GENERATE_KEY);
        generateButton.setFont(UIConstants.FONT_BODY_BOLD);
        generateButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        generateButton.setFocusPainted(false);
        generateButton.addActionListener(e -> showGenerateDialog());

        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(UIConstants.GAP_SMALL));
        panel.add(messageLabel);
        panel.add(Box.createVerticalStrut(15));
        panel.add(generateButton);

        return panel;
    }

    private void handleStartRestart() {
        try {
            String newIp = networkConfigPanel.getSelectedIp();
            int newPort = networkConfigPanel.getPort();

            String corsText = networkConfigPanel.getCorsText();
            List<String> origins = parseCorsOrigins(corsText);
            settings.setAllowedOrigins(origins);

            networkConfigPanel.setActionButtonEnabled(false);
            networkConfigPanel.setActionButtonText("Starting...");

            CompletableFuture.runAsync(() -> {
                try {
                    String oldIp = settings.getIp();
                    int oldPort = settings.getPort();

                    settings.setIp(newIp);
                    settings.setPort(newPort);

                    try {
                        restartCallback.run();
                    } catch (Exception startError) {
                        settings.setIp(oldIp);
                        settings.setPort(oldPort);

                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(
                                    this,
                                    "Failed to start server: " + startError.getMessage() +
                                            "\n\nSettings have been reverted to: " + oldIp + ":" + oldPort,
                                    "Server Error",
                                    JOptionPane.ERROR_MESSAGE);
                        });
                        throw startError;
                    }
                } catch (Exception e) {
                    api.logging().logToError("Server start failed: " + e.getMessage());
                }
            }).thenRun(() -> {
                SwingUtilities.invokeLater(() -> {
                    networkConfigPanel.setActionButtonEnabled(true);
                    updateStatus();
                });
            });

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Port must be a valid number",
                    "Invalid Port",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleStop() {
        bridgeServer.stop();
        api.logging().logToOutput("Bridge manually stopped via Settings UI");
        updateStatus();
    }

    private void showGenerateDialog() {
        String name = JOptionPane.showInputDialog(
                this,
                "Enter a name for this API key (e.g. 'VS Code', 'CI/CD'):",
                "Generate API Key",
                JOptionPane.PLAIN_MESSAGE);

        if (name == null || name.trim().isEmpty()) {
            return;
        }

        ApiKey newKey = ApiKey.create(name.trim());
        settings.addKey(newKey);
        refreshKeys();

        showTokenDialog(name.trim(), newKey.token());
    }

    private void showTokenDialog(String name, String token) {
        JPanel panel = new JPanel(new BorderLayout(UIConstants.GAP_MEDIUM, UIConstants.GAP_MEDIUM));
        panel.setBorder(new EmptyBorder(
                UIConstants.GAP_MEDIUM,
                UIConstants.GAP_MEDIUM,
                UIConstants.GAP_MEDIUM,
                UIConstants.GAP_MEDIUM));

        JLabel warningLabel = new JLabel(
                "IMPORTANT: Copy this token now! You will not be able to see it again after closing this dialog.");
        warningLabel.setForeground(UIConstants.COLOR_WARNING);
        panel.add(warningLabel, BorderLayout.NORTH);

        JPanel tokenPanel = new JPanel(new BorderLayout(UIConstants.GAP_SMALL, 0));

        JTextField tokenField = new JTextField(token);
        tokenField.setEditable(false);
        tokenField.setFont(UIConstants.FONT_MONOSPACE);

        JButton copyButton = new JButton(UIConstants.BUTTON_COPY_TO_CLIPBOARD);
        copyButton.setFocusPainted(false);
        copyButton.addActionListener(e -> {
            Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(token), null);
            copyButton.setText("Copied!");
            copyButton.setEnabled(false);
        });

        tokenPanel.add(tokenField, BorderLayout.CENTER);
        tokenPanel.add(copyButton, BorderLayout.EAST);

        panel.add(tokenPanel, BorderLayout.CENTER);

        JOptionPane.showMessageDialog(
                this,
                panel,
                "API Key Generated for '" + name + "'",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void handleRename(ApiKey key, int index) {
        String newName = JOptionPane.showInputDialog(
                this,
                "Enter new name for this API key:",
                key.name());

        if (newName != null && !newName.trim().isEmpty() && !newName.equals(key.name())) {
            settings.removeKey(index);
            settings.addKey(new ApiKey(newName.trim(), key.token(), key.createdDate(), key.lastUsed()));
            refreshKeys();
        }
    }

    private void handleRevoke(ApiKey key, int index) {
        int result = JOptionPane.showConfirmDialog(
                this,
                String.format(
                        "Are you sure you want to revoke the API key '%s'?\n\n" +
                                "This will immediately disconnect all clients using this key.\n" +
                                "This action cannot be undone.",
                        key.name()),
                "Revoke API Key",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (result == JOptionPane.YES_OPTION) {
            settings.removeKey(index);
            refreshKeys();
            api.logging().logToOutput("API key '" + key.name() + "' has been revoked");
        }
    }

    private List<String> parseCorsOrigins(String corsText) {
        if (corsText.isEmpty() || corsText.equals("*")) {
            return List.of(
                    ServerConstants.DEFAULT_ALLOWED_ORIGIN_LOCALHOST,
                    ServerConstants.DEFAULT_ALLOWED_ORIGIN_127);
        }
        return Arrays.stream(corsText.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}