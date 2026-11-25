package com.clinicflow.ui;

import com.clinicflow.model.*;
import com.clinicflow.service.AppointmentService;
import com.clinicflow.service.DoctorService;
import com.clinicflow.service.PatientService;
import com.clinicflow.ui.common.CardPanel;
import com.clinicflow.ui.common.UITheme;
import com.clinicflow.util.ValidationException;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Interactive Appointment Booking modal dialog with integrated real-time Weka ML prediction.
 * Extracts booking-time features, evaluates no-show risk dynamically, and generates queue tokens.
 */
public class AppointmentBookingDialog extends JDialog {

    private final PatientService patientService;
    private final DoctorService doctorService;
    private final AppointmentService appointmentService;
    private final Runnable onSuccessCallback;

    private JComboBox<PatientItem> cmbPatient;
    private JComboBox<DoctorItem> cmbDoctor;
    private JTextField txtDate;
    private JComboBox<String> cmbTime;
    private JTextField txtNeighbourhood;
    private JTextField txtNotes;

    // Real-time ML Prediction card labels
    private JLabel lblPredScore;
    private JLabel lblAttendedScore;
    private JLabel lblRiskBadge;
    private JLabel lblActionText;
    private JLabel lblModelStatus;

    public AppointmentBookingDialog(Frame parent, PatientService patientService,
                                    DoctorService doctorService, AppointmentService appointmentService,
                                    Long preselectedPatientId, Runnable onSuccessCallback) {
        super(parent, "Book Clinical Appointment & Assess No-Show Risk", true);
        this.patientService = patientService;
        this.doctorService = doctorService;
        this.appointmentService = appointmentService;
        this.onSuccessCallback = onSuccessCallback;

        setLayout(new BorderLayout());
        getContentPane().setBackground(UITheme.COLOR_BG);

        initUI(preselectedPatientId);
        updatePredictionPreview();

        pack();
        setLocationRelativeTo(parent);
        setResizable(false);
    }

    private void initUI(Long preselectedPatientId) {
        JPanel mainContent = new JPanel();
        mainContent.setLayout(new BoxLayout(mainContent, BoxLayout.Y_AXIS));
        mainContent.setBorder(new EmptyBorder(20, 24, 20, 24));
        mainContent.setBackground(UITheme.COLOR_BG);

        // Header Title
        JLabel title = new JLabel("New Patient Appointment Registration");
        title.setFont(UITheme.FONT_TITLE);
        title.setForeground(UITheme.COLOR_PRIMARY);

        JLabel subtitle = new JLabel("Schedule consultation, generate queue token, and calculate live ML attendance risk");
        subtitle.setFont(UITheme.FONT_SUBTITLE);
        subtitle.setForeground(UITheme.COLOR_TEXT_SECONDARY);

        mainContent.add(title);
        mainContent.add(Box.createVerticalStrut(4));
        mainContent.add(subtitle);
        mainContent.add(Box.createVerticalStrut(18));

        // Form Card
        CardPanel formCard = new CardPanel();
        formCard.setLayout(new GridLayout(6, 2, 12, 14));
        formCard.setBorder(new EmptyBorder(16, 20, 16, 20));

        // 1. Patient Selector
        cmbPatient = new JComboBox<>();
        List<Patient> patients = patientService.getAllPatients();
        PatientItem preselectedItem = null;
        for (Patient p : patients) {
            PatientItem item = new PatientItem(p);
            cmbPatient.addItem(item);
            if (preselectedPatientId != null && p.getId().equals(preselectedPatientId)) {
                preselectedItem = item;
            }
        }
        if (preselectedItem != null) {
            cmbPatient.setSelectedItem(preselectedItem);
        }
        cmbPatient.addActionListener(e -> updatePredictionPreview());

        // 2. Doctor Selector
        cmbDoctor = new JComboBox<>();
        List<Doctor> doctors = doctorService.getAvailableDoctors();
        for (Doctor d : doctors) {
            cmbDoctor.addItem(new DoctorItem(d));
        }

        // 3. Appointment Date
        txtDate = UITheme.createTextField(12);
        txtDate.setText(LocalDate.now().plusDays(1).toString());
        txtDate.setToolTipText("Format: YYYY-MM-DD");
        txtDate.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent evt) {
                updatePredictionPreview();
            }
        });

        // 4. Appointment Time
        String[] timeSlots = {
                "08:00", "08:30", "09:00", "09:30", "10:00", "10:30",
                "11:00", "11:30", "13:00", "13:30", "14:00", "14:30",
                "15:00", "15:30", "16:00", "16:30", "17:00"
        };
        cmbTime = new JComboBox<>(timeSlots);
        cmbTime.setSelectedItem("09:30");

        // 5. Neighbourhood & Notes
        txtNeighbourhood = UITheme.createTextField(15);
        txtNeighbourhood.setText("JARDIM DA PENHA");

        txtNotes = UITheme.createTextField(20);
        txtNotes.setText("Routine outpatient consultation");

        formCard.add(new JLabel("Select Patient: *"));
        formCard.add(cmbPatient);
        formCard.add(new JLabel("Attending Doctor: *"));
        formCard.add(cmbDoctor);
        formCard.add(new JLabel("Appointment Date (YYYY-MM-DD): *"));
        formCard.add(txtDate);
        formCard.add(new JLabel("Consultation Time Slot: *"));
        formCard.add(cmbTime);
        formCard.add(new JLabel("Clinic Neighbourhood: *"));
        formCard.add(txtNeighbourhood);
        formCard.add(new JLabel("Clinical / Visit Notes:"));
        formCard.add(txtNotes);

        mainContent.add(formCard);
        mainContent.add(Box.createVerticalStrut(18));

        // 6. Real-Time ML Prediction Display Card
        CardPanel mlCard = new CardPanel();
        mlCard.setLayout(new BorderLayout(10, 10));
        mlCard.setBorder(new EmptyBorder(14, 18, 14, 18));
        mlCard.setBackground(Color.WHITE);

        JPanel mlHeader = new JPanel(new BorderLayout());
        mlHeader.setOpaque(false);
        JLabel mlTitle = new JLabel("Real-Time Weka Machine Learning Risk Assessment");
        mlTitle.setFont(UITheme.FONT_HEADER);
        mlTitle.setForeground(UITheme.COLOR_PRIMARY);

        lblModelStatus = new JLabel("Connecting to Weka Model...");
        lblModelStatus.setFont(UITheme.FONT_SMALL);
        lblModelStatus.setForeground(UITheme.COLOR_ACCENT);

        mlHeader.add(mlTitle, BorderLayout.WEST);
        mlHeader.add(lblModelStatus, BorderLayout.EAST);
        mlCard.add(mlHeader, BorderLayout.NORTH);

        JPanel mlBody = new JPanel(new GridLayout(2, 2, 10, 8));
        mlBody.setOpaque(false);

        lblPredScore = new JLabel("No-Show Probability: --");
        lblPredScore.setFont(UITheme.FONT_BODY_BOLD);

        lblAttendedScore = new JLabel("Attendance Probability: --");
        lblAttendedScore.setFont(UITheme.FONT_BODY);

        lblRiskBadge = new JLabel("Risk Level: --");
        lblRiskBadge.setFont(UITheme.FONT_BODY_BOLD);

        lblActionText = new JLabel("Suggested Action: --");
        lblActionText.setFont(UITheme.FONT_BODY);

        mlBody.add(lblPredScore);
        mlBody.add(lblRiskBadge);
        mlBody.add(lblAttendedScore);
        mlBody.add(lblActionText);
        mlCard.add(mlBody, BorderLayout.CENTER);

        JLabel lblDisclaimer = new JLabel("Notice: Model-estimated no-show score – not a calibrated probability. Predictions never cancel care.");
        lblDisclaimer.setFont(UITheme.FONT_SMALL);
        lblDisclaimer.setForeground(UITheme.COLOR_TEXT_MUTED);
        mlCard.add(lblDisclaimer, BorderLayout.SOUTH);

        mainContent.add(mlCard);
        add(mainContent, BorderLayout.CENTER);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        btnPanel.setBackground(UITheme.COLOR_CARD_BG);
        btnPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UITheme.COLOR_BORDER));

        JButton btnCancel = UITheme.createButton("Cancel", false);
        btnCancel.addActionListener(e -> dispose());

        JButton btnSuggestLowRisk = UITheme.createButton("Suggest Low-Risk Dates", false);
        btnSuggestLowRisk.setToolTipText("Scan upcoming days to find dates where this patient's no-show risk drops to LOW");
        btnSuggestLowRisk.addActionListener(e -> showLowerRiskRecommendations());

        JButton btnRecalc = UITheme.createButton("Recalculate Risk", false);
        btnRecalc.addActionListener(e -> updatePredictionPreview());

        JButton btnConfirm = UITheme.createAccentButton("Confirm Booking & Issue Token");
        btnConfirm.addActionListener(e -> executeBooking());

        btnPanel.add(btnCancel);
        btnPanel.add(btnSuggestLowRisk);
        btnPanel.add(btnRecalc);
        btnPanel.add(btnConfirm);
        add(btnPanel, BorderLayout.SOUTH);
    }

    private void showLowerRiskRecommendations() {
        PatientItem patItem = (PatientItem) cmbPatient.getSelectedItem();
        if (patItem == null) {
            JOptionPane.showMessageDialog(this, "Please select a patient first to evaluate lower-risk dates.", "Patient Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        DoctorItem docItem = (DoctorItem) cmbDoctor.getSelectedItem();
        Doctor doc = docItem != null ? docItem.doctor : null;

        List<AppointmentService.LowerRiskSlot> slots = appointmentService.findLowerRiskSlots(
                patItem.patient, doc, LocalDate.now(), 14, txtNeighbourhood.getText().trim()
        );

        if (slots.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No alternative slots could be evaluated (ML model may not be loaded yet).", "No Recommendations", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JDialog recDialog = new JDialog(this, "Smart Scheduling Recommendations (Lowest Risk Slots)", true);
        recDialog.setSize(640, 430);
        recDialog.setLocationRelativeTo(this);
        recDialog.setLayout(new BorderLayout(12, 12));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(UITheme.COLOR_PRIMARY);
        headerPanel.setBorder(new EmptyBorder(14, 16, 14, 16));
        JLabel titleLbl = new JLabel("Lower-Risk Date Recommendations for: " + patItem.patient.getName());
        titleLbl.setFont(UITheme.FONT_HEADER);
        titleLbl.setForeground(Color.WHITE);
        JLabel subLbl = new JLabel("The Weka Random Forest evaluated upcoming dates. Select a slot below to drop the risk to LOW:");
        subLbl.setFont(UITheme.FONT_BODY);
        subLbl.setForeground(new Color(226, 232, 240));
        headerPanel.add(titleLbl, BorderLayout.NORTH);
        headerPanel.add(subLbl, BorderLayout.SOUTH);
        recDialog.add(headerPanel, BorderLayout.NORTH);

        String[] cols = {"Date", "Weekday", "Lead Days", "Predicted No-Show", "Risk Category", "Suggested Action"};
        javax.swing.table.DefaultTableModel model = new javax.swing.table.DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        for (AppointmentService.LowerRiskSlot s : slots) {
            model.addRow(new Object[]{
                    s.getDate(),
                    s.getDate().getDayOfWeek().toString(),
                    s.getLeadDays() + (s.getLeadDays() == 0 ? " (Same-Day)" : " days"),
                    String.format("%.1f%%", s.getNoShowProbability() * 100.0),
                    s.getRiskCategory().name(),
                    s.getSuggestedAction().getActionDescription()
            });
        }

        JTable table = new JTable(model);
        UITheme.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        if (table.getRowCount() > 0) {
            table.setRowSelectionInterval(0, 0);
        }

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new EmptyBorder(10, 16, 10, 16));
        recDialog.add(scroll, BorderLayout.CENTER);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        JButton btnClose = UITheme.createButton("Close", false);
        btnClose.addActionListener(e -> recDialog.dispose());

        JButton btnApply = UITheme.createAccentButton("Apply Selected Date to Booking Form");
        btnApply.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow >= 0) {
                LocalDate chosenDate = (LocalDate) model.getValueAt(selectedRow, 0);
                txtDate.setText(chosenDate.toString());
                updatePredictionPreview();
                recDialog.dispose();
                JOptionPane.showMessageDialog(this,
                        "Appointment date updated to " + chosenDate + "!\nLive ML risk preview has been updated on the form.",
                        "Date Applied", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        actionPanel.add(btnClose);
        actionPanel.add(btnApply);
        recDialog.add(actionPanel, BorderLayout.SOUTH);

        recDialog.setVisible(true);
    }

    private void updatePredictionPreview() {
        PatientItem patItem = (PatientItem) cmbPatient.getSelectedItem();
        if (patItem == null) {
            lblPredScore.setText("Select a patient to evaluate risk");
            return;
        }

        try {
            LocalDate apptDate = LocalDate.parse(txtDate.getText().trim());
            LocalDateTime now = LocalDateTime.now();

            // Construct transient appointment for feature extraction
            Appointment temp = new Appointment(
                    999999L, patItem.patient, null, now, apptDate,
                    txtNeighbourhood.getText().trim()
            );

            PredictionResult pred = appointmentService.getPredictionService().predict(temp);
            if (pred.isModelAvailable()) {
                double noShowProb = pred.getNoShowProbability() * 100.0;
                double attProb = pred.getAttendanceProbability() * 100.0;
                RiskCategory risk = pred.getRiskCategory();
                ReminderAction action = pred.getSuggestedAction();

                lblModelStatus.setText("Weka Model Active (100 Trees)");
                lblPredScore.setText(String.format("Predicted No-Show Score: %.1f%%", noShowProb));
                lblAttendedScore.setText(String.format("Estimated Attendance: %.1f%%", attProb));
                lblRiskBadge.setText("Risk Level: " + risk.name());

                if (risk == RiskCategory.HIGH) {
                    lblRiskBadge.setForeground(UITheme.COLOR_RISK_HIGH);
                } else if (risk == RiskCategory.MEDIUM) {
                    lblRiskBadge.setForeground(UITheme.COLOR_RISK_MEDIUM);
                } else {
                    lblRiskBadge.setForeground(UITheme.COLOR_RISK_LOW);
                }

                lblActionText.setText("Recommended: " + action.getActionDescription());
            } else {
                lblModelStatus.setText("ML model unavailable");
                lblModelStatus.setForeground(UITheme.COLOR_RISK_MEDIUM);
                lblPredScore.setText("ML Model Not Loaded");
                lblRiskBadge.setText("Risk Level: N/A");
                lblAttendedScore.setText("Standard booking operational");
                lblActionText.setText("Action: Standard 24h SMS");
            }
        } catch (Exception ex) {
            lblPredScore.setText("Invalid date format (Use YYYY-MM-DD)");
            lblRiskBadge.setText("Risk Level: --");
        }
    }

    private void executeBooking() {
        PatientItem patItem = (PatientItem) cmbPatient.getSelectedItem();
        DoctorItem docItem = (DoctorItem) cmbDoctor.getSelectedItem();

        if (patItem == null) {
            JOptionPane.showMessageDialog(this, "Please select a patient.", "Missing Input", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (docItem == null) {
            JOptionPane.showMessageDialog(this, "Please select an attending doctor.", "Missing Input", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            LocalDate apptDate = LocalDate.parse(txtDate.getText().trim());
            String timeStr = (String) cmbTime.getSelectedItem();
            LocalTime apptTime = LocalTime.parse(timeStr);
            String hood = txtNeighbourhood.getText().trim();
            String notes = txtNotes.getText().trim();

            Appointment booked = appointmentService.bookAppointment(
                    null,
                    patItem.patient.getId(),
                    docItem.doctor,
                    LocalDateTime.now(),
                    apptDate,
                    apptTime,
                    hood,
                    notes
            );

            String token = booked.getQueueToken();
            String riskMsg = booked.getPredictionResult() != null && booked.getPredictionResult().isModelAvailable() ?
                    String.format("\nPredicted No-Show Risk: %s (%.1f%%)\nRecommended Follow-Up: %s",
                            booked.getPredictionResult().getRiskCategory(),
                            booked.getPredictionResult().getNoShowProbability() * 100.0,
                            booked.getPredictionResult().getSuggestedAction()) : "\nML Model: Unavailable";

            JOptionPane.showMessageDialog(this,
                    "Appointment booked successfully!\n\n" +
                            "• Appointment ID: #" + booked.getAppointmentId() + "\n" +
                            "• Queue Token: " + token + "\n" +
                            "• Patient: " + booked.getPatient().getName() + "\n" +
                            "• Doctor: Dr. " + booked.getDoctor().getName() + " (" + booked.getDoctor().getSpecialty() + ")\n" +
                            "• Date & Time: " + booked.getAppointmentDay() + " at " + booked.getAppointmentTime() + "\n" +
                            riskMsg + "\n\nRecord saved to data/app/appointments.csv.",
                    "Booking Confirmed", JOptionPane.INFORMATION_MESSAGE);

            dispose();
            if (onSuccessCallback != null) {
                onSuccessCallback.run();
            }
        } catch (ValidationException ve) {
            JOptionPane.showMessageDialog(this, ve.getMessage(), "Booking Validation Failed", JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to book appointment: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static class PatientItem {
        final Patient patient;
        PatientItem(Patient patient) { this.patient = patient; }
        @Override
        public String toString() {
            return String.format("%s (ID: %d, Age: %d)", patient.getName(), patient.getId(), patient.getAge());
        }
    }

    private static class DoctorItem {
        final Doctor doctor;
        DoctorItem(Doctor doctor) { this.doctor = doctor; }
        @Override
        public String toString() {
            return String.format("Dr. %s (%s, Room %s)", doctor.getName(), doctor.getSpecialty(), doctor.getRoomNumber());
        }
    }
}
