package com.arqsz.burpsense;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.arqsz.burpsense.api.BridgeServer;
import com.arqsz.burpsense.config.BridgeSettings;
import com.arqsz.burpsense.constants.UIConstants;
import com.arqsz.burpsense.ui.BridgeSettingsTab;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

/**
 * Main entry point for the BurpSense Bridge extension
 */
public class BurpSenseBridge implements BurpExtension {

    private BridgeServer bridgeServer;
    private BridgeSettings settings;
    private MontoyaApi api;
    private ScheduledExecutorService monitoringExecutor;

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        api.extension().setName(UIConstants.EXTENSION_NAME);

        this.settings = new BridgeSettings(api.persistence().preferences(), api);
        this.bridgeServer = new BridgeServer(api, settings);

        restartServer();

        BridgeSettingsTab settingsTab = new BridgeSettingsTab(
                api,
                settings,
                bridgeServer,
                this::restartServer);
        api.userInterface().registerSuiteTab(UIConstants.TAB_NAME, settingsTab);

        startMonitoring();

        api.extension().registerUnloadingHandler(() -> {
            api.logging().logToOutput("Unloading BurpSense Bridge...");

            if (monitoringExecutor != null) {
                monitoringExecutor.shutdown();
                try {
                    if (!monitoringExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                        monitoringExecutor.shutdownNow();
                    }
                    api.logging().logToOutput("Monitoring thread stopped.");
                } catch (InterruptedException e) {
                    monitoringExecutor.shutdownNow();
                }
            }

            if (bridgeServer != null) {
                bridgeServer.stop();
                api.logging().logToOutput("BurpSense Bridge server shut down successfully.");
            }
        });
    }

    /**
     * Restarts the bridge server with current settings
     */
    private void restartServer() {
        try {
            if (monitoringExecutor != null) {
                monitoringExecutor.shutdown();
            }

            bridgeServer.stop();
            bridgeServer.start();

            startMonitoring();
        } catch (Exception e) {
            api.logging().logToError("Bridge Start Failed: " + e.getMessage());
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    api.userInterface().swingUtils().suiteFrame(),
                    String.format("Could not start server on port %d.\nError: %s",
                            settings.getPort(), e.getMessage()),
                    "Bridge Error",
                    JOptionPane.ERROR_MESSAGE));
        }
    }

    /**
     * Starts the monitoring thread that checks for issue changes
     * and broadcasts WebSocket updates to clients
     */
    private void startMonitoring() {
        if (monitoringExecutor != null) {
            monitoringExecutor.shutdown();
        }

        monitoringExecutor = Executors.newSingleThreadScheduledExecutor();
        monitoringExecutor.scheduleAtFixedRate(
                () -> {
                    try {
                        if (bridgeServer != null) {
                            bridgeServer.checkForIssueChanges();
                        }
                    } catch (Exception e) {
                        api.logging().logToError("Error in monitoring thread: " + e.getMessage());
                    }
                },
                3,
                2,
                TimeUnit.SECONDS);

        api.logging().logToOutput("Issue monitoring thread started (checks every 2 seconds)");
    }
}