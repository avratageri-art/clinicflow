package com.clinicflow.ui;

import com.clinicflow.model.ReminderRecord;
import com.clinicflow.service.ReminderService;
import com.clinicflow.ui.common.CardPanel;
import com.clinicflow.ui.common.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Screen managing automated and staff-assisted patient appointment confirmation reminders.
 * For coursework evaluation, reminders are simulated locally with full persistent logging.
 */
public class ReminderManagementPanel extends JPanel {

    private final ReminderService reminderService;
    private final ClinicFlowApp parentApp;

    private DefaultTableModel pendingModel;
    private JTable pendingTable;
    private DefaultTableModel historyModel;
    private JTable historyTable;
    private JLabel lblPendingCount;

    public ReminderManagementPanel(ReminderService reminderService, ClinicFlowApp parentApp) {
        this.reminderService = reminderService;
        this.parentApp = parentApp;

        setLayout(new BorderLayout(20, 20));
        setBackground(UITheme.COLOR_BG);
        setBorder(new EmptyBorder(24, 28, 24, 28));

        initUI();
        refreshData();
    }

    private void initUI() {
        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel title = new JLabel("Patient Reminder & Outreach Management");
        title.setFont(UITheme.FONT_TITLE);
        title.setForeground(UITheme.COLOR_PRIMARY);

        lblPendingCount = new JLabel("0 reminders pending dispatch");
        lblPendingCount.setFont(UITheme.FONT_SUBTITLE);
        lblPendingCount.setForeground(UITheme.COLOR_TEXT_SECONDARY);

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 2, 2));
        titleBlock.setOpaque(false);
        titleBlock.add(title);
        titleBlock.add(lblPendingCount);

        JPanel actionBlock = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionBlock.setOpaque(false);

        JButton btnSync = UITheme.createAccentButton("Sync with Appointments");
        btnSync.addActionListener(e -> {
            reminderService.syncRemindersWithAppointments();
            refreshData();
            JOptionPane.showMessageDialog(this, "Reminders synchronized with upcoming appointments.", "Sync Complete", JOptionPane.INFORMATION_MESSAGE);
        });

        JButton btnRefresh = UITheme.createButton("Refresh", false);
        btnRefresh.addActionListener(e -> refreshData());

        actionBlock.add(btnRefresh);
        actionBlock.add(btnSync);

        headerPanel.add(titleBlock, BorderLayout.WEST);
        headerPanel.add(actionBlock, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // Center: Tabbed Pane (Pending Reminders vs Sent History)
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(UITheme.FONT_HEADER);

        // Tab 1: Pending Reminders
        JPanel pendingTab = new JPanel(new BorderLayout(14, 14));
        pendingTab.setBackground(UITheme.COLOR_BG);
        pendingTab.setBorder(new EmptyBorder(14, 14, 14, 14));

        // Academic notice banner
        JPanel noticeCard = new JPanel(new BorderLayout());
        noticeCard.setBackground(new Color(254, 243, 199)); // Light Amber
        noticeCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(252, 211, 77), 1, true),
                new EmptyBorder(10, 14, 10, 14)
        ));
        JLabel lblNotice = new JLabel(
                "<html><b>Academic Simulation Banner:</b> " + ReminderService.ACADEMIC_DISCLAIMER +
                " Outgoing reminder records are stored persistently in <code>data/app/reminders.csv</code>.</html>"
        );
        lblNotice.setFont(UITheme.FONT_BODY);
        lblNotice.setForeground(new Color(146, 64, 14));
        noticeCard.add(lblNotice, BorderLayout.CENTER);
        pendingTab.add(noticeCard, BorderLayout.NORTH);

        String[] pendingCols = {
                "Reminder ID", "Appt ID", "Patient Name", "Contact Phone", "Appt Date & Time",
                "Doctor", "ML Risk Level", "Recommended Outreach Action", "Status"
        };
        pendingModel = new DefaultTableModel(pendingCols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        pendingTable = new JTable(pendingModel);
        UITheme.styleTable(pendingTable);
        JScrollPane pendingScroll = new JScrollPane(pendingTable);
        pendingScroll.setBorder(BorderFactory.createLineBorder(UITheme.COLOR_BORDER));
        pendingScroll.getViewport().setBackground(Color.WHITE);
        pendingTab.add(pendingScroll, BorderLayout.CENTER);

        JPanel pendingActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        pendingActions.setOpaque(false);
        JButton btnSendSelected = UITheme.createAccentButton("Simulate Send Selected Reminder");
        btnSendSelected.addActionListener(e -> simulateSendSelected());

        JButton btnSendAll = UITheme.createButton("Simulate Send All Pending", false);
        btnSendAll.addActionListener(e -> simulateSendAllPending());

        pendingActions.add(btnSendSelected);
        pendingActions.add(btnSendAll);
        pendingTab.add(pendingActions, BorderLayout.SOUTH);

        tabbedPane.addTab("Pending Reminders Queue", pendingTab);

        // Tab 2: Reminder Sent History
        JPanel historyTab = new JPanel(new BorderLayout(14, 14));
        historyTab.setBackground(UITheme.COLOR_BG);
        historyTab.setBorder(new EmptyBorder(14, 14, 14, 14));

        String[] histCols = {
                "Reminder ID", "Appt ID", "Patient Name", "Contact", "Appt Date",
                "Action Taken", "Sent Timestamp", "Audit Log Notes"
        };
        historyModel = new DefaultTableModel(histCols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        historyTable = new JTable(historyModel);
        UITheme.styleTable(historyTable);
        JScrollPane histScroll = new JScrollPane(historyTable);
        histScroll.setBorder(BorderFactory.createLineBorder(UITheme.COLOR_BORDER));
        histScroll.getViewport().setBackground(Color.WHITE);
        historyTab.add(histScroll, BorderLayout.CENTER);

        tabbedPane.addTab("Sent Reminder Audit History", historyTab);

        add(tabbedPane, BorderLayout.CENTER);
    }

    public void refreshData() {
        reminderService.syncRemindersWithAppointments();

        List<ReminderRecord> pending = reminderService.getPendingReminders();
        pendingModel.setRowCount(0);
        for (ReminderRecord r : pending) {
            pendingModel.addRow(new Object[]{
                    r.getReminderId(),
                    r.getAppointmentId(),
                    r.getPatientName(),
                    r.getPatientContact(),
                    r.getAppointmentDate() + " " + (r.getAppointmentTime() != null ? r.getAppointmentTime() : ""),
                    r.getDoctorName(),
                    r.getRiskCategory().name(),
                    r.getRecommendedAction().name(),
                    r.getStatus()
            });
        }
        lblPendingCount.setText(pending.size() + " reminders pending dispatch");

        List<ReminderRecord> history = reminderService.getSentReminders();
        historyModel.setRowCount(0);
        for (ReminderRecord r : history) {
            String sentStr = r.getSentTimestamp() != null ?
                    r.getSentTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "N/A";
            historyModel.addRow(new Object[]{
                    r.getReminderId(),
                    r.getAppointmentId(),
                    r.getPatientName(),
                    r.getPatientContact(),
                    r.getAppointmentDate(),
                    r.getRecommendedAction().name(),
                    sentStr,
                    r.getNotes()
            });
        }
    }

    private void simulateSendSelected() {
        int row = pendingTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a reminder from the table first.",
                    "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Long remId = (Long) pendingModel.getValueAt(row, 0);
        ReminderRecord sent = reminderService.simulateSendReminder(remId);
        refreshData();
        JOptionPane.showMessageDialog(this,
                "Simulated Reminder Dispatched Successfully!\n\n" +
                        "• Reminder ID: #" + sent.getReminderId() + "\n" +
                        "• Patient: " + sent.getPatientName() + " (" + sent.getPatientContact() + ")\n" +
                        "• Outreach Type: " + sent.getRecommendedAction().name() + "\n" +
                        "• Action Description: " + sent.getRecommendedAction().getActionDescription() + "\n" +
                        "• Sent Timestamp: " + sent.getSentTimestamp() + "\n\n" +
                        "Notice: Simulated for academic demonstration. Stored in data/app/reminders.csv.",
                "Simulation Confirmed", JOptionPane.INFORMATION_MESSAGE);
    }

    private void simulateSendAllPending() {
        List<ReminderRecord> pending = reminderService.getPendingReminders();
        if (pending.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No pending reminders in the queue.", "Empty Queue", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int count = 0;
        for (ReminderRecord r : pending) {
            reminderService.simulateSendReminder(r.getReminderId());
            count++;
        }
        refreshData();
        JOptionPane.showMessageDialog(this,
                "Simulated dispatch for " + count + " pending reminders complete!\n" +
                        "Audit records updated in data/app/reminders.csv.",
                "Batch Simulation Finished", JOptionPane.INFORMATION_MESSAGE);
    }
}
