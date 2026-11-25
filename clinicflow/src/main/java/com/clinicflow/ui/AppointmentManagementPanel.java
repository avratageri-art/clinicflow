package com.clinicflow.ui;

import com.clinicflow.model.Appointment;
import com.clinicflow.model.AttendanceStatus;
import com.clinicflow.model.RiskCategory;
import com.clinicflow.service.AppointmentService;
import com.clinicflow.ui.common.CardPanel;
import com.clinicflow.ui.common.UITheme;
import com.clinicflow.util.ClinicFlowException;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Screen for managing clinical appointments, viewing ML risk ratings,
 * filtering schedules, and safely executing attendance state transitions.
 */
public class AppointmentManagementPanel extends JPanel {

    private final AppointmentService appointmentService;
    private final ClinicFlowApp parentApp;

    private JComboBox<String> cmbFilter;
    private DefaultTableModel tableModel;
    private JTable appointmentTable;
    private JLabel lblCount;

    public AppointmentManagementPanel(AppointmentService appointmentService, ClinicFlowApp parentApp) {
        this.appointmentService = appointmentService;
        this.parentApp = parentApp;

        setLayout(new BorderLayout(20, 20));
        setBackground(UITheme.COLOR_BG);
        setBorder(new EmptyBorder(24, 28, 24, 28));

        initUI();
        refreshTable();
    }

    private void initUI() {
        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel title = new JLabel("Appointment Management & Lifecycle");
        title.setFont(UITheme.FONT_TITLE);
        title.setForeground(UITheme.COLOR_PRIMARY);

        lblCount = new JLabel("0 appointments recorded");
        lblCount.setFont(UITheme.FONT_SUBTITLE);
        lblCount.setForeground(UITheme.COLOR_TEXT_SECONDARY);

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 2, 2));
        titleBlock.setOpaque(false);
        titleBlock.add(title);
        titleBlock.add(lblCount);

        JPanel actionBlock = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionBlock.setOpaque(false);

        JButton btnBook = UITheme.createAccentButton("+ Book Appointment");
        btnBook.addActionListener(e -> parentApp.openAppointmentBookingDialog());

        JButton btnRefresh = UITheme.createButton("Refresh", false);
        btnRefresh.addActionListener(e -> refreshTable());

        actionBlock.add(btnRefresh);
        actionBlock.add(btnBook);

        headerPanel.add(titleBlock, BorderLayout.WEST);
        headerPanel.add(actionBlock, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // Center Card
        CardPanel contentCard = new CardPanel();
        contentCard.setLayout(new BorderLayout(14, 14));
        contentCard.setBorder(new EmptyBorder(18, 20, 18, 20));

        // Filter Bar
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filterBar.setOpaque(false);

        JLabel lblFilter = new JLabel("Filter View:");
        lblFilter.setFont(UITheme.FONT_BODY_BOLD);

        String[] filters = {"ALL", "TODAY", "UPCOMING", "HIGH_RISK", "COMPLETED", "NO_SHOW", "CANCELLED"};
        cmbFilter = new JComboBox<>(filters);
        cmbFilter.setFont(UITheme.FONT_BODY);
        cmbFilter.addActionListener(e -> refreshTable());

        filterBar.add(lblFilter);
        filterBar.add(cmbFilter);
        contentCard.add(filterBar, BorderLayout.NORTH);

        // Table
        String[] cols = {
                "ID", "Token", "Date", "Time", "Patient Name", "Doctor",
                "Status", "ML Risk Level", "Suggested Action", "Reminder Status"
        };
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        appointmentTable = new JTable(tableModel);
        UITheme.styleTable(appointmentTable);

        JScrollPane scrollPane = new JScrollPane(appointmentTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(UITheme.COLOR_BORDER));
        scrollPane.getViewport().setBackground(Color.WHITE);
        contentCard.add(scrollPane, BorderLayout.CENTER);

        // Action Toolbar Below Table
        JPanel tableActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        tableActions.setOpaque(false);

        JButton btnCheckIn = UITheme.createAccentButton("Check In Patient");
        btnCheckIn.addActionListener(e -> checkInSelected());

        JButton btnComplete = UITheme.createButton("Mark Completed", false);
        btnComplete.addActionListener(e -> markCompletedSelected());

        JButton btnNoShow = UITheme.createButton("Record No-Show", false);
        btnNoShow.setForeground(UITheme.COLOR_RISK_HIGH);
        btnNoShow.addActionListener(e -> recordNoShowSelected());

        JButton btnCancel = UITheme.createButton("Cancel Appointment", false);
        btnCancel.addActionListener(e -> cancelSelected());

        JButton btnView = UITheme.createButton("View Details", false);
        btnView.addActionListener(e -> viewSelectedDetails());

        tableActions.add(btnCheckIn);
        tableActions.add(btnComplete);
        tableActions.add(btnNoShow);
        tableActions.add(btnCancel);
        tableActions.add(btnView);

        contentCard.add(tableActions, BorderLayout.SOUTH);
        add(contentCard, BorderLayout.CENTER);
    }

    public void refreshTable() {
        String filter = (String) cmbFilter.getSelectedItem();
        List<Appointment> list = appointmentService.getAppointmentsByFilter(filter);

        tableModel.setRowCount(0);
        for (Appointment a : list) {
            String id = String.valueOf(a.getAppointmentId());
            String token = a.getQueueToken() != null ? a.getQueueToken() : "-";
            String date = a.getAppointmentDay().toString();
            String time = a.getAppointmentTime() != null ? a.getAppointmentTime().toString() : "09:00";
            String pat = a.getPatient() != null ? a.getPatient().getName() : "Unknown";
            String doc = a.getDoctor() != null ? "Dr. " + a.getDoctor().getName() : "Physician";
            String status = a.getAttendanceStatus().name();

            String risk = "N/A";
            String action = "Standard";
            if (a.getPredictionResult() != null && a.getPredictionResult().isModelAvailable()) {
                risk = String.format("%s (%.1f%%)",
                        a.getPredictionResult().getRiskCategory().name(),
                        a.getPredictionResult().getNoShowProbability() * 100.0);
                action = a.getPredictionResult().getSuggestedAction().name();
            } else if (a.getPredictionResult() != null) {
                risk = "UNAVAILABLE";
            }

            String rem = a.getReminderStatus() != null ? a.getReminderStatus() : "PENDING";

            tableModel.addRow(new Object[]{id, token, date, time, pat, doc, status, risk, action, rem});
        }

        lblCount.setText(list.size() + " appointments displayed (" + filter + ")");
    }

    private Appointment getSelectedAppointmentOrWarn() {
        int row = appointmentTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select an appointment from the table first.",
                    "Selection Required", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        Long id = Long.parseLong(tableModel.getValueAt(row, 0).toString());
        return appointmentService.getAppointmentById(id).orElse(null);
    }

    private void checkInSelected() {
        Appointment a = getSelectedAppointmentOrWarn();
        if (a == null) return;
        try {
            appointmentService.checkInPatient(a.getAppointmentId());
            refreshTable();
            JOptionPane.showMessageDialog(this,
                    "Patient " + a.getPatient().getName() + " checked in successfully.\n" +
                            "Assigned Token: " + a.getQueueToken() + "\n" +
                            "Patient has been added to today's active waiting queue.",
                    "Patient Checked In", JOptionPane.INFORMATION_MESSAGE);
        } catch (ClinicFlowException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Cannot Check In", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void markCompletedSelected() {
        Appointment a = getSelectedAppointmentOrWarn();
        if (a == null) return;
        try {
            appointmentService.completeAppointment(a.getAppointmentId());
            refreshTable();
            JOptionPane.showMessageDialog(this, "Appointment marked as ATTENDED / COMPLETED.", "Consultation Finished", JOptionPane.INFORMATION_MESSAGE);
        } catch (ClinicFlowException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void recordNoShowSelected() {
        Appointment a = getSelectedAppointmentOrWarn();
        if (a == null) return;

        int confirm = JOptionPane.showConfirmDialog(this,
                "Confirm recording actual patient NO-SHOW for:\n" +
                        "Appointment #" + a.getAppointmentId() + " (" + a.getPatient().getName() + ")?\n\n" +
                        "IMPORTANT: A high-risk ML prediction does NOT automatically make a patient a no-show.\n" +
                        "Only record this if the patient truly failed to attend the consultation.",
                "Record Actual No-Show", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                appointmentService.recordNoShow(a.getAppointmentId());
                refreshTable();
                JOptionPane.showMessageDialog(this, "Patient attendance updated to NO-SHOW.", "Outcome Recorded", JOptionPane.INFORMATION_MESSAGE);
            } catch (ClinicFlowException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void cancelSelected() {
        Appointment a = getSelectedAppointmentOrWarn();
        if (a == null) return;

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to cancel appointment #" + a.getAppointmentId() + " for " + a.getPatient().getName() + "?",
                "Confirm Cancellation", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                appointmentService.cancelAppointment(a.getAppointmentId());
                refreshTable();
                JOptionPane.showMessageDialog(this, "Appointment cancelled.", "Cancelled", JOptionPane.INFORMATION_MESSAGE);
            } catch (ClinicFlowException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Cannot Cancel", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void viewSelectedDetails() {
        Appointment a = getSelectedAppointmentOrWarn();
        if (a == null) return;

        String mlDetails = "Model Unavailable";
        if (a.getPredictionResult() != null && a.getPredictionResult().isModelAvailable()) {
            mlDetails = String.format(
                    "• Predicted Class: %s\n" +
                    "• No-Show Score: %.2f%%\n" +
                    "• Attendance Probability: %.2f%%\n" +
                    "• Risk Tier: %s\n" +
                    "• Recommended Action: %s\n" +
                    "• Status Note: Model-estimated score (not a calibrated probability)",
                    a.getPredictionResult().getPredictedClass(),
                    a.getPredictionResult().getNoShowProbability() * 100.0,
                    a.getPredictionResult().getAttendanceProbability() * 100.0,
                    a.getPredictionResult().getRiskCategory(),
                    a.getPredictionResult().getSuggestedAction()
            );
        }

        String details = String.format(
                "APPOINTMENT DETAILS #%d\n" +
                "=====================================================\n" +
                "• Queue Token: %s\n" +
                "• Patient: %s (ID: %d, Age: %d)\n" +
                "• Attending Doctor: Dr. %s (%s, Room %s)\n" +
                "• Scheduled Consultation: %s at %s\n" +
                "• Booking Registered At: %s (Waiting Lead Time: %d days)\n" +
                "• Clinic Neighbourhood: %s\n" +
                "• Current Lifecycle Status: %s\n" +
                "• Check-In Time: %s\n" +
                "• Reminder Status: %s\n" +
                "• Clinical Notes: %s\n\n" +
                "MACHINE LEARNING RISK EVALUATION:\n" +
                "-----------------------------------------------------\n" +
                "%s\n",
                a.getAppointmentId(),
                a.getQueueToken(),
                a.getPatient().getName(), a.getPatient().getId(), a.getPatient().getAge(),
                a.getDoctor().getName(), a.getDoctor().getSpecialty(), a.getDoctor().getRoomNumber(),
                a.getAppointmentDay(), a.getAppointmentTime(),
                a.getScheduledDay(), a.getLeadDays(),
                a.getNeighbourhood(),
                a.getAttendanceStatus().name(),
                a.getCheckInTime() != null ? a.getCheckInTime() : "Not Checked In",
                a.getReminderStatus(),
                a.getNotes().isEmpty() ? "None" : a.getNotes(),
                mlDetails
        );

        JTextArea ta = new JTextArea(details, 18, 52);
        ta.setEditable(false);
        ta.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JOptionPane.showMessageDialog(this, new JScrollPane(ta), "Appointment Record #" + a.getAppointmentId(), JOptionPane.PLAIN_MESSAGE);
    }
}
