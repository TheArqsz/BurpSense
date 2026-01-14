package com.arqsz.burpsense.ui.components;

import java.awt.BorderLayout;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

import com.arqsz.burpsense.constants.UIConstants;

/**
 * Builder for the status panel component
 */
public class StatusPanelBuilder {

    private final JLabel statusLabel;
    private final JLabel connectionLabel;

    public StatusPanelBuilder() {
        this.statusLabel = new JLabel();
        this.connectionLabel = new JLabel();

        statusLabel.setFont(UIConstants.FONT_TITLE);
        connectionLabel.setFont(UIConstants.FONT_SMALL);
        connectionLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
    }

    /**
     * Builds the status panel
     * 
     * @return The configured JPanel
     */
    public JPanel build() {
        JPanel panel = PanelFactory.createCard();
        panel.setLayout(new BorderLayout(UIConstants.GAP_MEDIUM, UIConstants.GAP_SMALL));

        JPanel statusContent = new JPanel();
        statusContent.setLayout(new BoxLayout(statusContent, BoxLayout.Y_AXIS));
        statusContent.add(statusLabel);
        statusContent.add(Box.createVerticalStrut(2));
        statusContent.add(connectionLabel);

        panel.add(statusContent, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Updates the status display for online state
     * 
     * @param ip   The IP address
     * @param port The port number
     */
    public void updateOnline(String ip, int port) {
        statusLabel.setText(UIConstants.STATUS_ONLINE);
        statusLabel.setForeground(UIConstants.COLOR_SUCCESS);
        connectionLabel.setText(String.format(UIConstants.STATUS_LISTENING_FORMAT, ip, port));
    }

    /**
     * Updates the status display for offline state
     */
    public void updateOffline() {
        statusLabel.setText(UIConstants.STATUS_OFFLINE);
        statusLabel.setForeground(UIConstants.COLOR_INACTIVE);
        connectionLabel.setText(UIConstants.STATUS_NOT_RUNNING);
    }
}