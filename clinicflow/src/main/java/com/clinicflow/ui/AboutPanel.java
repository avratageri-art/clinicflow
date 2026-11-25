package com.clinicflow.ui;

import com.clinicflow.ui.common.CardPanel;
import com.clinicflow.ui.common.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * About & Help panel providing application architecture details,
 * academic assignment compliance matrix, and user guidance.
 */
public class AboutPanel extends JPanel {

    public AboutPanel() {
        setLayout(new BorderLayout(20, 20));
        setBackground(UITheme.COLOR_BG);
        setBorder(new EmptyBorder(24, 28, 24, 28));

        initUI();
    }

    private void initUI() {
        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel title = new JLabel("About ClinicFlow & System Architecture");
        title.setFont(UITheme.FONT_TITLE);
        title.setForeground(UITheme.COLOR_PRIMARY);

        JLabel subtitle = new JLabel("An ML-Based Patient No-Show Prediction and Smart Queue Management System");
        subtitle.setFont(UITheme.FONT_SUBTITLE);
        subtitle.setForeground(UITheme.COLOR_TEXT_SECONDARY);

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 2, 2));
        titleBlock.setOpaque(false);
        titleBlock.add(title);
        titleBlock.add(subtitle);

        headerPanel.add(titleBlock, BorderLayout.WEST);
        add(headerPanel, BorderLayout.NORTH);

        // Center
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);

        CardPanel card = new CardPanel();
        card.setLayout(new BorderLayout(14, 14));
        card.setBorder(new EmptyBorder(20, 24, 20, 24));

        String content = "<html><body style='line-height: 1.5; color: #1E293B;'>" +
                "<h2>Project Overview</h2>" +
                "<b>ClinicFlow</b> is a genuine, end-to-end Machine Learning-integrated outpatient management desktop application " +
                "engineered in Java 17 and Swing. It solves the real-world operational problem of patient appointment no-shows, " +
                "which lead to unused doctor consultation hours, clinic revenue loss, and lengthened waiting times.<br><br>" +

                "<h2>Academic Assignment Compliance</h2>" +
                "• <b>100% Java + Weka ML:</b> Zero Python dependencies. All feature extraction, supervised classification, and inference run natively inside the JVM.<br>" +
                "• <b>Genuine Public Dataset:</b> Trained on the verified Kaggle Medical Appointment No-Shows dataset (110,527 historical records from Vitória, Brazil).<br>" +
                "• <b>Zero Synthetic Training Data:</b> Never invents patient records or training examples.<br>" +
                "• <b>Clean Service Architecture:</b> Modular separation across Model, Repository, Service, ML, and UI layers.<br>" +
                "• <b>Zero Target Leakage:</b> Utilizes only 6 operational features available at the moment of booking.<br>" +
                "• <b>Persistent Storage:</b> Retains patient, doctor, appointment, and reminder logs across restarts in <code>data/app/*.csv</code>.<br>" +
                "• <b>Smart Queue Management:</b> Implements fair FIFO queue sequencing while keeping ML predictions purely advisory.<br><br>" +

                "<h2>Technology Stack</h2>" +
                "• <b>Runtime:</b> Java 17 LTS (OpenSDK)<br>" +
                "• <b>Build Tool:</b> Apache Maven 3.9.6<br>" +
                "• <b>Machine Learning:</b> Weka 3.8.6 (Balanced RandomForest with 100 Trees)<br>" +
                "• <b>CSV Engine:</b> Apache Commons CSV 1.10.0<br>" +
                "• <b>User Interface:</b> Java Swing (Pure native desktop implementation)<br>" +
                "• <b>Testing:</b> JUnit 5 Jupiter + AssertJ<br><br>" +

                "<h2>Operational Disclaimer</h2>" +
                "ClinicFlow operates as a clinical decision-support tool. It never automatically denies medical care, " +
                "cancels appointments, or lowers a patient's position in the consultation queue based on algorithmic predictions." +
                "</body></html>";

        JLabel lblContent = new JLabel(content);
        lblContent.setFont(UITheme.FONT_BODY);
        card.add(lblContent, BorderLayout.CENTER);

        centerPanel.add(card);

        JScrollPane scroll = new JScrollPane(centerPanel);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(UITheme.COLOR_BG);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
    }
}
