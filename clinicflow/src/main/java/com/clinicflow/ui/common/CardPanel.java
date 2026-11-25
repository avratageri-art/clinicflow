package com.clinicflow.ui.common;

import javax.swing.*;
import java.awt.*;

/**
 * Reusable rounded card container for dashboard metrics and section panels.
 */
public class CardPanel extends JPanel {

    private final int cornerRadius;
    private final Color backgroundColor;
    private final Color borderColor;

    public CardPanel() {
        this(12, UITheme.COLOR_CARD_BG, UITheme.COLOR_BORDER);
    }

    public CardPanel(int cornerRadius, Color backgroundColor, Color borderColor) {
        this.cornerRadius = cornerRadius;
        this.backgroundColor = backgroundColor;
        this.borderColor = borderColor;
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth() - 1;
        int height = getHeight() - 1;

        // Draw background
        g2.setColor(backgroundColor);
        g2.fillRoundRect(0, 0, width, height, cornerRadius, cornerRadius);

        // Draw border
        if (borderColor != null) {
            g2.setColor(borderColor);
            g2.drawRoundRect(0, 0, width, height, cornerRadius, cornerRadius);
        }

        g2.dispose();
    }
}
