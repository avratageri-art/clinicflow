package com.clinicflow.ui;

import com.clinicflow.model.Appointment;
import com.clinicflow.model.AttendanceStatus;
import com.clinicflow.model.RiskCategory;
import com.clinicflow.service.AppointmentService;
import com.clinicflow.service.DoctorService;
import com.clinicflow.service.PatientService;
import com.clinicflow.ui.common.AttendanceChartComponent;
import com.clinicflow.ui.common.CardPanel;
import com.clinicflow.ui.common.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Main overview dashboard displaying live clinical metrics, attendance distribution,
 * today's appointment schedule, and operational action shortcuts.
 */
public class DashboardPanel extends JPanel {

    private final PatientService patientService;
    private final DoctorService doctorService;
    private final AppointmentService appointmentService;
    private final ClinicFlowApp parentApp;

    // Stat metric labels
    private JLabel lblTotalPatients;
    private JLabel lblTotalDoctors;
    private JLabel lblTodayAppointments;
    private JLabel lblCheckedIn;
    private JLabel lblCompleted;
    private JLabel lblMissed;
    private JLabel lblHighRisk;

    // Chart component
    private AttendanceChartComponent attendanceChart;

    // Table
    private DefaultTableModel tableModel;
    private JTable todayTable;

    public DashboardPanel(PatientService patientService, DoctorService doctorService,
                          AppointmentService appointmentService, ClinicFlowApp parentApp) {
        this.patientService = patientService;
        this.doctorService = doctorService;
        this.appointmentService = appointmentService;
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

        JLabel title = new JLabel("Clinic Operational Dashboard");
        title.setFont(UITheme.FONT_TITLE);
        title.setForeground(UITheme.COLOR_PRIMARY);

        JLabel subtitle = new JLabel("Real-time clinical throughput, patient queue status, and ML no-show risk tracking");
        subtitle.setFont(UITheme.FONT_SUBTITLE);
        subtitle.setForeground(UITheme.COLOR_TEXT_SECONDARY);

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 2, 2));
        titleBlock.setOpaque(false);
        titleBlock.add(title);
        titleBlock.add(subtitle);

        JPanel actionBlock = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionBlock.setOpaque(false);

        JButton btnBook = UITheme.createAccentButton("+ Book Appointment");
        btnBook.addActionListener(e -> parentApp.openAppointmentBookingDialog());

        JButton btnRefresh = UITheme.createButton("Refresh", false);
        btnRefresh.addActionListener(e -> refreshData());

        actionBlock.add(btnRefresh);
        actionBlock.add(btnBook);

        headerPanel.add(titleBlock, BorderLayout.WEST);
        headerPanel.add(actionBlock, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // Content Center: Scrollable
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);

        // 1. Metric Cards Grid
        JPanel metricsGrid = new JPanel(new GridLayout(2, 4, 14, 14));
        metricsGrid.setOpaque(false);
        metricsGrid.setMaximumSize(new Dimension(3000, 180));

        lblTotalPatients = new JLabel("0");
        lblTotalDoctors = new JLabel("0");
        lblTodayAppointments = new JLabel("0");
        lblCheckedIn = new JLabel("0");
        lblCompleted = new JLabel("0");
        lblMissed = new JLabel("0");
        lblHighRisk = new JLabel("0");

        metricsGrid.add(createMetricCard("Total Patients", lblTotalPatients, "Registered records", UITheme.COLOR_PRIMARY));
        metricsGrid.add(createMetricCard("Attending Doctors", lblTotalDoctors, "Active medical staff", UITheme.COLOR_ACCENT));
        metricsGrid.add(createMetricCard("Today's Consultations", lblTodayAppointments, LocalDate.now().toString(), UITheme.COLOR_PRIMARY));
        metricsGrid.add(createMetricCard("In Waiting Queue", lblCheckedIn, "Checked-in patients", UITheme.COLOR_ACCENT));

        metricsGrid.add(createMetricCard("Completed Today", lblCompleted, "Successfully attended", UITheme.COLOR_RISK_LOW));
        metricsGrid.add(createMetricCard("Missed Appointments", lblMissed, "Confirmed no-shows", UITheme.COLOR_RISK_HIGH));
        metricsGrid.add(createMetricCard("High-Risk Upcoming", lblHighRisk, "P(No-Show) >= 50%", UITheme.COLOR_RISK_HIGH));

        // Quick navigation card
        CardPanel quickCard = new CardPanel();
        quickCard.setLayout(new BorderLayout());
        quickCard.setBorder(new EmptyBorder(12, 14, 12, 14));
        JLabel qTitle = new JLabel("Quick Actions");
        qTitle.setFont(UITheme.FONT_HEADER);
        qTitle.setForeground(UITheme.COLOR_PRIMARY);

        JPanel qBtns = new JPanel(new GridLayout(2, 1, 6, 6));
        qBtns.setOpaque(false);
        JButton btnAddPat = UITheme.createButton("+ New Patient", false);
        btnAddPat.addActionListener(e -> parentApp.navigateTo("Patients"));
        JButton btnAddDoc = UITheme.createButton("+ New Doctor", false);
        btnAddDoc.addActionListener(e -> parentApp.navigateTo("Doctors"));
        qBtns.add(btnAddPat);
        qBtns.add(btnAddDoc);

        quickCard.add(qTitle, BorderLayout.NORTH);
        quickCard.add(qBtns, BorderLayout.CENTER);
        metricsGrid.add(quickCard);

        centerPanel.add(metricsGrid);
        centerPanel.add(Box.createVerticalStrut(20));

        // 2. Attendance Summary Chart Card
        CardPanel chartCard = new CardPanel();
        chartCard.setLayout(new BorderLayout(10, 10));
        chartCard.setBorder(new EmptyBorder(16, 20, 16, 20));
        chartCard.setMaximumSize(new Dimension(3000, 140));

        JLabel chartTitle = new JLabel("Appointment Attendance & Clinic Throughput Breakdown");
        chartTitle.setFont(UITheme.FONT_HEADER);
        chartTitle.setForeground(UITheme.COLOR_PRIMARY);

        attendanceChart = new AttendanceChartComponent();
        chartCard.add(chartTitle, BorderLayout.NORTH);
        chartCard.add(attendanceChart, BorderLayout.CENTER);

        centerPanel.add(chartCard);
        centerPanel.add(Box.createVerticalStrut(20));

        // 3. Today's Appointment Schedule Table Card
        CardPanel tableCard = new CardPanel();
        tableCard.setLayout(new BorderLayout(10, 10));
        tableCard.setBorder(new EmptyBorder(16, 20, 16, 20));

        JPanel tableHeader = new JPanel(new BorderLayout());
        tableHeader.setOpaque(false);
        JLabel tableTitle = new JLabel("Today's Scheduled Appointments");
        tableTitle.setFont(UITheme.FONT_HEADER);
        tableTitle.setForeground(UITheme.COLOR_PRIMARY);

        JButton btnViewAll = UITheme.createButton("View All Appointments ->", false);
        btnViewAll.addActionListener(e -> parentApp.navigateTo("Appointments"));

        tableHeader.add(tableTitle, BorderLayout.WEST);
        tableHeader.add(btnViewAll, BorderLayout.EAST);
        tableCard.add(tableHeader, BorderLayout.NORTH);

        String[] cols = {"Token", "Time", "Patient Name", "Doctor", "Status", "ML Risk Level", "Suggested Action"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        todayTable = new JTable(tableModel);
        UITheme.styleTable(todayTable);

        JScrollPane scrollPane = new JScrollPane(todayTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(UITheme.COLOR_BORDER));
        scrollPane.getViewport().setBackground(Color.WHITE);
        tableCard.add(scrollPane, BorderLayout.CENTER);

        centerPanel.add(tableCard);

        JScrollPane mainScroll = new JScrollPane(centerPanel);
        mainScroll.setBorder(null);
        mainScroll.getViewport().setBackground(UITheme.COLOR_BG);
        mainScroll.getVerticalScrollBar().setUnitIncrement(16);
        add(mainScroll, BorderLayout.CENTER);
    }

    private CardPanel createMetricCard(String title, JLabel valueLabel, String subtitle, Color accentColor) {
        CardPanel card = new CardPanel();
        card.setLayout(new BorderLayout(6, 6));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(UITheme.FONT_BODY_BOLD);
        lblTitle.setForeground(UITheme.COLOR_TEXT_SECONDARY);

        valueLabel.setFont(UITheme.FONT_METRIC);
        valueLabel.setForeground(accentColor);

        JLabel lblSub = new JLabel(subtitle);
        lblSub.setFont(UITheme.FONT_SMALL);
        lblSub.setForeground(UITheme.COLOR_TEXT_MUTED);

        card.add(lblTitle, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        card.add(lblSub, BorderLayout.SOUTH);

        return card;
    }

    public void refreshData() {
        int patientCount = patientService.getAllPatients().size();
        int doctorCount = doctorService.getAllDoctors().size();
        lblTotalPatients.setText(String.format("%,d", patientCount));
        lblTotalDoctors.setText(String.format("%,d", doctorCount));

        LocalDate today = LocalDate.now();
        List<Appointment> todayAppts = appointmentService.getAppointmentsForDate(today);
        lblTodayAppointments.setText(String.valueOf(todayAppts.size()));

        long checkedIn = todayAppts.stream().filter(a -> a.getAttendanceStatus() == AttendanceStatus.CHECKED_IN).count();
        long completed = todayAppts.stream().filter(a -> a.getAttendanceStatus() == AttendanceStatus.ATTENDED).count();
        long missed = todayAppts.stream().filter(a -> a.getAttendanceStatus() == AttendanceStatus.NO_SHOW).count();
        long scheduled = todayAppts.stream().filter(a -> a.getAttendanceStatus() == AttendanceStatus.SCHEDULED).count();

        lblCheckedIn.setText(String.valueOf(checkedIn));
        lblCompleted.setText(String.valueOf(completed));
        lblMissed.setText(String.valueOf(missed));

        // High risk upcoming
        long highRiskCount = appointmentService.getAllAppointments().stream()
                .filter(a -> !a.getAppointmentDay().isBefore(today) && !a.getAttendanceStatus().isFinished())
                .filter(a -> a.getPredictionResult() != null && a.getPredictionResult().getRiskCategory() == RiskCategory.HIGH)
                .count();
        lblHighRisk.setText(String.valueOf(highRiskCount));

        // Update chart
        attendanceChart.updateData((int) completed, (int) missed, (int) checkedIn, (int) scheduled);

        // Update table
        tableModel.setRowCount(0);
        for (Appointment a : todayAppts) {
            String token = a.getQueueToken() != null ? a.getQueueToken() : "-";
            String time = a.getAppointmentTime() != null ? a.getAppointmentTime().toString() : "09:00";
            String patName = a.getPatient() != null ? a.getPatient().getName() : "Unknown";
            String docName = a.getDoctor() != null ? a.getDoctor().getName() : "Physician";
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

            tableModel.addRow(new Object[]{token, time, patName, docName, status, risk, action});
        }
    }
}
