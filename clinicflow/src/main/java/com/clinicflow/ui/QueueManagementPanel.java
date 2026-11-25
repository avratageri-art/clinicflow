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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Smart Queue Management screen handling active patient check-in, token progression,
 * and consultation workflow.
 * STRICT FAIRNESS: Queue ordering is FIFO based on check-in arrival time.
 * ML risk predictions are advisory only and NEVER alter patient queue position.
 */
public class QueueManagementPanel extends JPanel {

    private final AppointmentService appointmentService;
    private final ClinicFlowApp parentApp;

    private DefaultTableModel queueTableModel;
    private JTable queueTable;
    private JLabel lblQueueCount;

    public QueueManagementPanel(AppointmentService appointmentService, ClinicFlowApp parentApp) {
        this.appointmentService = appointmentService;
        this.parentApp = parentApp;

        setLayout(new BorderLayout(20, 20));
        setBackground(UITheme.COLOR_BG);
        setBorder(new EmptyBorder(24, 28, 24, 28));

        initUI();
        refreshQueue();
    }

    private void initUI() {
        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel title = new JLabel("Smart Clinic Queue Management");
        title.setFont(UITheme.FONT_TITLE);
        title.setForeground(UITheme.COLOR_PRIMARY);

        lblQueueCount = new JLabel("0 patients waiting in active queue");
        lblQueueCount.setFont(UITheme.FONT_SUBTITLE);
        lblQueueCount.setForeground(UITheme.COLOR_TEXT_SECONDARY);

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 2, 2));
        titleBlock.setOpaque(false);
        titleBlock.add(title);
        titleBlock.add(lblQueueCount);

        JPanel actionBlock = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionBlock.setOpaque(false);

        JButton btnQuickCheckIn = UITheme.createAccentButton("+ Check In Patient");
        btnQuickCheckIn.addActionListener(e -> openCheckInDialog());

        JButton btnRefresh = UITheme.createButton("Refresh Queue", false);
        btnRefresh.addActionListener(e -> refreshQueue());

        actionBlock.add(btnRefresh);
        actionBlock.add(btnQuickCheckIn);

        headerPanel.add(titleBlock, BorderLayout.WEST);
        headerPanel.add(actionBlock, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // Center: Card containing fairness banner and Queue Table
        CardPanel contentCard = new CardPanel();
        contentCard.setLayout(new BorderLayout(14, 14));
        contentCard.setBorder(new EmptyBorder(18, 20, 18, 20));

        // Clinical Fairness Notice Banner
        JPanel fairnessBanner = new JPanel(new BorderLayout());
        fairnessBanner.setBackground(new Color(239, 246, 255)); // Light Blue
        fairnessBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(191, 219, 254), 1, true),
                new EmptyBorder(10, 14, 10, 14)
        ));

        JLabel lblFairness = new JLabel(
                "<html><b>Clinical Operational Fairness Policy:</b> Queue priority follows arrival check-in order (FIFO). " +
                "Machine Learning no-show risk scores are strictly advisory for proactive reminders and <b>never</b> demote patients in queue or delay care.</html>"
        );
        lblFairness.setFont(UITheme.FONT_BODY);
        lblFairness.setForeground(new Color(30, 58, 138)); // Dark Navy Blue
        fairnessBanner.add(lblFairness, BorderLayout.CENTER);
        contentCard.add(fairnessBanner, BorderLayout.NORTH);

        // Queue Table
        String[] cols = {
                "Position", "Token", "Patient Name", "Attending Doctor", "Appt Time",
                "Check-In Time", "Queue Status", "Advisory ML Risk", "Appointment ID"
        };
        queueTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        queueTable = new JTable(queueTableModel);
        UITheme.styleTable(queueTable);

        JScrollPane scrollPane = new JScrollPane(queueTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(UITheme.COLOR_BORDER));
        scrollPane.getViewport().setBackground(Color.WHITE);
        contentCard.add(scrollPane, BorderLayout.CENTER);

        // Action Toolbar Below Table
        JPanel tableActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        tableActions.setOpaque(false);

        JButton btnCallNext = UITheme.createAccentButton("Call Next / Start Consultation");
        btnCallNext.addActionListener(e -> startConsultationSelected());

        JButton btnComplete = UITheme.createButton("Finish Consultation", false);
        btnComplete.addActionListener(e -> finishConsultationSelected());

        JButton btnNoShow = UITheme.createButton("Record No-Show", false);
        btnNoShow.setForeground(UITheme.COLOR_RISK_HIGH);
        btnNoShow.addActionListener(e -> recordNoShowSelected());

        JButton btnCancel = UITheme.createButton("Cancel Appointment", false);
        btnCancel.addActionListener(e -> cancelSelected());

        tableActions.add(btnCallNext);
        tableActions.add(btnComplete);
        tableActions.add(btnNoShow);
        tableActions.add(btnCancel);

        contentCard.add(tableActions, BorderLayout.SOUTH);
        add(contentCard, BorderLayout.CENTER);
    }

    public void refreshQueue() {
        LocalDate today = LocalDate.now();
        List<Appointment> queue = appointmentService.getTodayQueue(today);

        queueTableModel.setRowCount(0);
        int pos = 1;
        long inConsultationCount = 0;

        for (Appointment a : queue) {
            if (a.getAttendanceStatus() == AttendanceStatus.IN_CONSULTATION) {
                inConsultationCount++;
            }
            String posStr = a.getAttendanceStatus() == AttendanceStatus.IN_CONSULTATION ?
                    "NOW IN DOCTOR'S ROOM" : "#" + (pos++);

            String token = a.getQueueToken() != null ? a.getQueueToken() : "-";
            String pat = a.getPatient() != null ? a.getPatient().getName() : "Unknown";
            String doc = a.getDoctor() != null ? "Dr. " + a.getDoctor().getName() + " (" + a.getDoctor().getRoomNumber() + ")" : "Physician";
            String apptTime = a.getAppointmentTime() != null ? a.getAppointmentTime().toString() : "09:00";
            String checkInTime = a.getCheckInTime() != null ?
                    a.getCheckInTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")) : "-";
            String status = a.getAttendanceStatus().name();

            String risk = "N/A";
            if (a.getPredictionResult() != null && a.getPredictionResult().isModelAvailable()) {
                risk = String.format("%s (%.1f%%)",
                        a.getPredictionResult().getRiskCategory().name(),
                        a.getPredictionResult().getNoShowProbability() * 100.0);
            }

            queueTableModel.addRow(new Object[]{
                    posStr, token, pat, doc, apptTime, checkInTime, status, risk, a.getAppointmentId()
            });
        }

        lblQueueCount.setText(queue.size() + " patients in active queue (" + inConsultationCount + " currently in consultation)");
    }

    private Appointment getSelectedAppointmentOrWarn() {
        int row = queueTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a patient from the queue table first.",
                    "Selection Required", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        Long apptId = Long.parseLong(queueTableModel.getValueAt(row, 8).toString());
        return appointmentService.getAppointmentById(apptId).orElse(null);
    }

    private void startConsultationSelected() {
        Appointment a = getSelectedAppointmentOrWarn();
        if (a == null) return;
        try {
            appointmentService.startConsultation(a.getAppointmentId());
            refreshQueue();
            JOptionPane.showMessageDialog(this,
                    "Consultation started for Token " + a.getQueueToken() + " (" + a.getPatient().getName() + ").\n" +
                            "Doctor: Dr. " + a.getDoctor().getName() + " (" + a.getDoctor().getRoomNumber() + ").",
                    "Consultation Started", JOptionPane.INFORMATION_MESSAGE);
        } catch (ClinicFlowException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Cannot Start Consultation", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void finishConsultationSelected() {
        Appointment a = getSelectedAppointmentOrWarn();
        if (a == null) return;
        try {
            appointmentService.completeAppointment(a.getAppointmentId());
            refreshQueue();
            JOptionPane.showMessageDialog(this,
                    "Consultation completed for Token " + a.getQueueToken() + " (" + a.getPatient().getName() + ").\n" +
                            "Status updated to ATTENDED / COMPLETED and removed from active queue.",
                    "Consultation Finished", JOptionPane.INFORMATION_MESSAGE);
        } catch (ClinicFlowException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void recordNoShowSelected() {
        Appointment a = getSelectedAppointmentOrWarn();
        if (a == null) return;

        int confirm = JOptionPane.showConfirmDialog(this,
                "Record actual NO-SHOW for Token " + a.getQueueToken() + " (" + a.getPatient().getName() + ")?\n" +
                        "This will mark the outcome as NO_SHOW and remove them from the queue.",
                "Confirm No-Show", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                appointmentService.recordNoShow(a.getAppointmentId());
                refreshQueue();
                JOptionPane.showMessageDialog(this, "Patient marked as NO-SHOW.", "Outcome Recorded", JOptionPane.INFORMATION_MESSAGE);
            } catch (ClinicFlowException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void cancelSelected() {
        Appointment a = getSelectedAppointmentOrWarn();
        if (a == null) return;

        int confirm = JOptionPane.showConfirmDialog(this,
                "Cancel appointment for Token " + a.getQueueToken() + "?",
                "Confirm Cancellation", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                appointmentService.cancelAppointment(a.getAppointmentId());
                refreshQueue();
                JOptionPane.showMessageDialog(this, "Appointment cancelled.", "Cancelled", JOptionPane.INFORMATION_MESSAGE);
            } catch (ClinicFlowException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Cannot Cancel", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void openCheckInDialog() {
        LocalDate today = LocalDate.now();
        List<Appointment> scheduledToday = appointmentService.getAppointmentsForDate(today).stream()
                .filter(a -> a.getAttendanceStatus() == AttendanceStatus.SCHEDULED)
                .toList();

        if (scheduledToday.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "There are no scheduled pending appointments for today (" + today + ").\n" +
                            "Use the Appointment Booking screen to schedule a consultation.",
                    "No Appointments to Check In", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        Appointment[] options = scheduledToday.toArray(new Appointment[0]);
        Appointment chosen = (Appointment) JOptionPane.showInputDialog(
                this,
                "Select patient appointment to check in for today's queue:",
                "Check In Patient",
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );

        if (chosen != null) {
            try {
                appointmentService.checkInPatient(chosen.getAppointmentId());
                refreshQueue();
                JOptionPane.showMessageDialog(this,
                        "Patient " + chosen.getPatient().getName() + " checked in successfully.\n" +
                                "Assigned Token: " + chosen.getQueueToken(),
                        "Check-In Successful", JOptionPane.INFORMATION_MESSAGE);
            } catch (ClinicFlowException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Check-In Failed", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
