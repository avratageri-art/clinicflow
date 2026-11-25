package com.clinicflow.ui.common;

import javax.swing.*;
import java.awt.*;

/**
 * Lightweight, pure-Java2D attendance chart component.
 * Visualizes genuine application appointment outcomes (Attended, No-Show, In-Queue, Scheduled)
 * with proportional bars and metrics. Zero external chart dependencies.
 */
public class AttendanceChartComponent extends JComponent {

    private int attended = 0;
    private int noShow = 0;
    private int inQueue = 0;
    private int scheduled = 0;

    public AttendanceChartComponent() {
        setPreferredSize(new Dimension(320, 110));
    }

    public void updateData(int attended, int noShow, int inQueue, int scheduled) {
        this.attended = Math.max(0, attended);
        this.noShow = Math.max(0, noShow);
        this.inQueue = Math.max(0, inQueue);
        this.scheduled = Math.max(0, scheduled);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int total = attended + noShow + inQueue + scheduled;
        int width = getWidth();
        int height = getHeight();

        if (total == 0) {
            g2.setColor(UITheme.COLOR_TEXT_MUTED);
            g2.setFont(UITheme.FONT_BODY);
            g2.drawString("No appointment outcome data recorded yet.", 15, height / 2);
            g2.dispose();
            return;
        }

        // Draw segmented progress bar
        int barY = 15;
        int barHeight = 24;
        int barWidth = width - 30;

        int attendedW = (int) Math.round(((double) attended / total) * barWidth);
        int noShowW = (int) Math.round(((double) noShow / total) * barWidth);
        int inQueueW = (int) Math.round(((double) inQueue / total) * barWidth);
        int scheduledW = barWidth - (attendedW + noShowW + inQueueW);

        int curX = 15;

        // Attended (Emerald)
        if (attendedW > 0) {
            g2.setColor(UITheme.COLOR_RISK_LOW);
            g2.fillRoundRect(curX, barY, attendedW + (curX == 15 ? 0 : 4), barHeight, 8, 8);
            curX += attendedW;
        }

        // In-Queue (Teal)
        if (inQueueW > 0) {
            g2.setColor(UITheme.COLOR_ACCENT);
            g2.fillRect(curX, barY, inQueueW, barHeight);
            curX += inQueueW;
        }

        // Scheduled (Sky Blue)
        if (scheduledW > 0) {
            g2.setColor(new Color(147, 197, 253));
            g2.fillRect(curX, barY, scheduledW, barHeight);
            curX += scheduledW;
        }

        // No-Show (Red)
        if (noShowW > 0) {
            g2.setColor(UITheme.COLOR_RISK_HIGH);
            g2.fillRoundRect(curX, barY, noShowW, barHeight, 8, 8);
        }

        // Legend below bar
        int legendY = barY + barHeight + 35;
        drawLegendItem(g2, 15, legendY, UITheme.COLOR_RISK_LOW, "Attended: " + attended + String.format(" (%.1f%%)", (double) attended / total * 100));
        drawLegendItem(g2, 160, legendY, UITheme.COLOR_RISK_HIGH, "Missed: " + noShow + String.format(" (%.1f%%)", (double) noShow / total * 100));
        drawLegendItem(g2, 290, legendY, UITheme.COLOR_ACCENT, "In-Queue: " + inQueue);
        drawLegendItem(g2, 400, legendY, new Color(147, 197, 253), "Scheduled: " + scheduled);

        g2.dispose();
    }

    private void drawLegendItem(Graphics2D g2, int x, int y, Color color, String text) {
        g2.setColor(color);
        g2.fillOval(x, y - 9, 10, 10);
        g2.setColor(UITheme.COLOR_TEXT_PRIMARY);
        g2.setFont(UITheme.FONT_SMALL);
        g2.drawString(text, x + 15, y);
    }
}
