package com.arqsz.burpsense.testutil;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.JFrame;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.persistence.Persistence;
import burp.api.montoya.persistence.Preferences;
import burp.api.montoya.project.Project;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.sitemap.SiteMap;
import burp.api.montoya.ui.UserInterface;
import burp.api.montoya.ui.swing.SwingUtils;

/**
 * Factory for creating mock MontoyaApi instances for testing
 */
public class MockMontoyaApiFactory {

    /**
     * Creates a basic mock MontoyaApi with common default behaviors
     */
    public static MontoyaApi createBasicMock() {
        MontoyaApi api = mock(MontoyaApi.class);

        Logging logging = mock(Logging.class);
        doNothing().when(logging).logToOutput(anyString());
        doNothing().when(logging).logToError(anyString());
        when(api.logging()).thenReturn(logging);

        Persistence persistence = mock(Persistence.class);
        Preferences preferences = mock(Preferences.class);
        Map<String, String> prefStore = new ConcurrentHashMap<>();
        Map<String, Integer> intPrefStore = new ConcurrentHashMap<>();

        when(api.persistence()).thenReturn(persistence);
        when(persistence.preferences()).thenReturn(preferences);

        doAnswer(inv -> {
            prefStore.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(preferences).setString(anyString(), anyString());
        when(preferences.getString(anyString())).thenAnswer(inv -> prefStore.get(inv.getArgument(0)));

        doAnswer(inv -> {
            intPrefStore.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(preferences).setInteger(anyString(), anyInt());
        when(preferences.getInteger(anyString())).thenAnswer(inv -> intPrefStore.get(inv.getArgument(0)));

        SiteMap siteMap = mock(SiteMap.class);
        when(siteMap.issues()).thenReturn(Collections.emptyList());
        when(api.siteMap()).thenReturn(siteMap);

        Project mockProject = mock(Project.class);
        when(mockProject.id()).thenReturn("test-project-id");
        when(mockProject.name()).thenReturn("test-project-name");
        when(api.project()).thenReturn(mockProject);

        UserInterface ui = mock(UserInterface.class);
        SwingUtils swingUtils = mock(SwingUtils.class);
        JFrame mockFrame = mock(JFrame.class);
        when(swingUtils.suiteFrame()).thenReturn(mockFrame);
        when(ui.swingUtils()).thenReturn(swingUtils);
        when(api.userInterface()).thenReturn(ui);

        return api;
    }

    /**
     * Creates a mock MontoyaApi with custom issues
     */
    public static MontoyaApi createWithIssues(List<AuditIssue> issues) {
        MontoyaApi api = createBasicMock();
        SiteMap siteMap = api.siteMap();
        when(siteMap.issues()).thenReturn(issues);
        return api;
    }

    /**
     * Creates a mock MontoyaApi with custom preferences
     */
    public static MontoyaApi createWithPreferences(Preferences preferences) {
        MontoyaApi api = createBasicMock();
        Persistence persistence = api.persistence();
        when(persistence.preferences()).thenReturn(preferences);
        return api;
    }
}
