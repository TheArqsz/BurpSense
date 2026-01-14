package com.arqsz.burpsense.ui.components;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import com.arqsz.burpsense.constants.UIConstants;

/**
 * Factory for creating styled UI panels
 */
public class PanelFactory {

    /**
     * Creates a card-style panel with standard styling
     * 
     * @return A styled JPanel
     */
    public static JPanel createCard() {
        JPanel panel = new JPanel();
        panel.setBackground(UIManager.getColor("Panel.background"));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground"), UIConstants.BORDER_THIN),
                new EmptyBorder(
                        UIConstants.PADDING_MEDIUM,
                        UIConstants.PADDING_MEDIUM,
                        UIConstants.PADDING_MEDIUM,
                        UIConstants.PADDING_MEDIUM)));
        return panel;
    }

    /**
     * Creates a titled border with standard styling
     * 
     * @param title The border title
     * @return A styled TitledBorder
     */
    public static TitledBorder createTitledBorder(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createEmptyBorder(),
                title);
        border.setTitleFont(UIConstants.FONT_TITLE);
        border.setTitleColor(UIManager.getColor("Label.foreground"));
        return border;
    }

    private PanelFactory() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}