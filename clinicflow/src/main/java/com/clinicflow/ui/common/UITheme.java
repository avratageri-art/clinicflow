package com.clinicflow.ui.common;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;

/**
 * Design system and styling palette for the ClinicFlow desktop application.
 * Professional healthcare aesthetic with dark navy/teal accents and high readability.
 */
public final class UITheme {

    private UITheme() {}

    // Color Palette
    public static final Color COLOR_PRIMARY = new Color(27, 54, 93);       // Dark Navy (#1B365D)
    public static final Color COLOR_PRIMARY_DARK = new Color(15, 32, 67);  // Deep Navy (#0F2043)
    public static final Color COLOR_ACCENT = new Color(0, 119, 145);       // Teal Accent (#007791)
    public static final Color COLOR_ACCENT_LIGHT = new Color(224, 242, 254);// Light Sky (#E0F2FE)
    public static final Color COLOR_BG = new Color(248, 250, 252);         // Slate Canvas (#F8FAFC)
    public static final Color COLOR_CARD_BG = Color.WHITE;
    public static final Color COLOR_TEXT_PRIMARY = new Color(30, 41, 59);  // Slate 800 (#1E293B)
    public static final Color COLOR_TEXT_SECONDARY = new Color(100, 116, 139); // Slate 500 (#64748B)
    public static final Color COLOR_TEXT_MUTED = new Color(148, 163, 184); // Slate 400
    public static final Color COLOR_BORDER = new Color(226, 232, 240);     // Slate 200 (#E2E8F0)

    // Risk Indicators
    public static final Color COLOR_RISK_HIGH = new Color(220, 38, 38);    // Red (#DC2626)
    public static final Color COLOR_RISK_HIGH_BG = new Color(254, 242, 242);
    public static final Color COLOR_RISK_MEDIUM = new Color(217, 119, 6);  // Amber (#D97706)
    public static final Color COLOR_RISK_MEDIUM_BG = new Color(254, 243, 199);
    public static final Color COLOR_RISK_LOW = new Color(22, 163, 74);     // Emerald (#16A34A)
    public static final Color COLOR_RISK_LOW_BG = new Color(240, 253, 244);

    // Typography
    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 22);
    public static final Font FONT_SUBTITLE = new Font("Segoe UI", Font.PLAIN, 14);
    public static final Font FONT_HEADER = new Font("Segoe UI", Font.BOLD, 15);
    public static final Font FONT_BODY_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 11);
    public static final Font FONT_METRIC = new Font("Segoe UI", Font.BOLD, 26);

    public static JButton createButton(String text, boolean isPrimary) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BODY_BOLD);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(8, 16, 8, 16));

        if (isPrimary) {
            btn.setBackground(COLOR_PRIMARY);
            btn.setForeground(Color.WHITE);
        } else {
            btn.setBackground(COLOR_CARD_BG);
            btn.setForeground(COLOR_TEXT_PRIMARY);
            btn.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(COLOR_BORDER, 1, true),
                    new EmptyBorder(7, 15, 7, 15)
            ));
        }
        return btn;
    }

    public static JButton createAccentButton(String text) {
        JButton btn = createButton(text, true);
        btn.setBackground(COLOR_ACCENT);
        return btn;
    }

    public static JTextField createTextField(int columns) {
        JTextField tf = new JTextField(columns);
        tf.setFont(FONT_BODY);
        tf.setForeground(COLOR_TEXT_PRIMARY);
        tf.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COLOR_BORDER, 1, true),
                new EmptyBorder(6, 10, 6, 10)
        ));
        return tf;
    }

    public static void styleTable(JTable table) {
        table.setFont(FONT_BODY);
        table.setRowHeight(34);
        table.setGridColor(COLOR_BORDER);
        table.setShowGrid(true);
        table.setShowVerticalLines(false);
        table.setSelectionBackground(new Color(241, 245, 249));
        table.setSelectionForeground(COLOR_TEXT_PRIMARY);
        table.setBackground(COLOR_CARD_BG);

        JTableHeader header = table.getTableHeader();
        header.setFont(FONT_BODY_BOLD);
        header.setBackground(new Color(241, 245, 249));
        header.setForeground(COLOR_TEXT_PRIMARY);
        header.setPreferredSize(new Dimension(header.getWidth(), 38));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_BORDER));

        ((DefaultTableCellRenderer) header.getDefaultRenderer()).setHorizontalAlignment(SwingConstants.LEFT);
    }
}
