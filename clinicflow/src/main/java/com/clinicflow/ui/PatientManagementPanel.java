package com.clinicflow.ui;

import com.clinicflow.model.Appointment;
import com.clinicflow.model.Gender;
import com.clinicflow.model.Patient;
import com.clinicflow.service.PatientService;
import com.clinicflow.ui.common.CardPanel;
import com.clinicflow.ui.common.UITheme;
import com.clinicflow.util.ClinicFlowException;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Screen for patient registration, medical history management, searching,
 * profile editing, and appointment history inspection.
 */
public class PatientManagementPanel extends JPanel {

    private final PatientService patientService;
    private final ClinicFlowApp parentApp;

    private JTextField txtSearch;
    private DefaultTableModel tableModel;
    private JTable patientTable;
    private JLabel lblCount;

    public PatientManagementPanel(PatientService patientService, ClinicFlowApp parentApp) {
        this.patientService = patientService;
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

        JLabel title = new JLabel("Patient Management");
        title.setFont(UITheme.FONT_TITLE);
        title.setForeground(UITheme.COLOR_PRIMARY);

        lblCount = new JLabel("0 patients registered");
        lblCount.setFont(UITheme.FONT_SUBTITLE);
        lblCount.setForeground(UITheme.COLOR_TEXT_SECONDARY);

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 2, 2));
        titleBlock.setOpaque(false);
        titleBlock.add(title);
        titleBlock.add(lblCount);

        JPanel actionBlock = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionBlock.setOpaque(false);

        JButton btnRegister = UITheme.createAccentButton("+ Register Patient");
        btnRegister.addActionListener(e -> openPatientDialog(null));

        JButton btnRefresh = UITheme.createButton("Refresh", false);
        btnRefresh.addActionListener(e -> refreshTable());

        actionBlock.add(btnRefresh);
        actionBlock.add(btnRegister);

        headerPanel.add(titleBlock, BorderLayout.WEST);
        headerPanel.add(actionBlock, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // Center: Card containing Search and Table
        CardPanel contentCard = new CardPanel();
        contentCard.setLayout(new BorderLayout(14, 14));
        contentCard.setBorder(new EmptyBorder(18, 20, 18, 20));

        // Search Bar
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        searchBar.setOpaque(false);
        JLabel lblFind = new JLabel("Search Patient:");
        lblFind.setFont(UITheme.FONT_BODY_BOLD);
        lblFind.setForeground(UITheme.COLOR_TEXT_PRIMARY);

        txtSearch = UITheme.createTextField(25);
        txtSearch.setToolTipText("Search by patient name, phone, or ID...");

        JButton btnSearch = UITheme.createButton("Search", false);
        btnSearch.addActionListener(e -> performSearch());

        JButton btnClear = UITheme.createButton("Clear", false);
        btnClear.addActionListener(e -> {
            txtSearch.setText("");
            refreshTable();
        });

        searchBar.add(lblFind);
        searchBar.add(txtSearch);
        searchBar.add(btnSearch);
        searchBar.add(btnClear);

        contentCard.add(searchBar, BorderLayout.NORTH);

        // Table
        String[] cols = {"Patient ID", "Full Name", "Gender", "Age", "Contact Number", "Hypertension", "Diabetes", "Chronic Status"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        patientTable = new JTable(tableModel);
        UITheme.styleTable(patientTable);

        JScrollPane scrollPane = new JScrollPane(patientTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(UITheme.COLOR_BORDER));
        scrollPane.getViewport().setBackground(Color.WHITE);
        contentCard.add(scrollPane, BorderLayout.CENTER);

        // Action Toolbar Below Table
        JPanel tableActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        tableActions.setOpaque(false);

        JButton btnEdit = UITheme.createButton("Edit Patient", false);
        btnEdit.addActionListener(e -> editSelectedPatient());

        JButton btnHistory = UITheme.createButton("View History", false);
        btnHistory.addActionListener(e -> viewSelectedPatientHistory());

        JButton btnDelete = UITheme.createButton("Delete Patient", false);
        btnDelete.setForeground(UITheme.COLOR_RISK_HIGH);
        btnDelete.addActionListener(e -> deleteSelectedPatient());

        JButton btnBookForPatient = UITheme.createAccentButton("Book Appointment for Selected");
        btnBookForPatient.addActionListener(e -> bookForSelectedPatient());

        tableActions.add(btnEdit);
        tableActions.add(btnHistory);
        tableActions.add(btnDelete);
        tableActions.add(btnBookForPatient);

        contentCard.add(tableActions, BorderLayout.SOUTH);

        add(contentCard, BorderLayout.CENTER);
    }

    private void performSearch() {
        String q = txtSearch.getText().trim();
        List<Patient> results = patientService.searchPatients(q);
        populateTable(results);
    }

    public void refreshTable() {
        populateTable(patientService.getAllPatients());
    }

    private void populateTable(List<Patient> patients) {
        tableModel.setRowCount(0);
        for (Patient p : patients) {
            tableModel.addRow(new Object[]{
                    p.getId(),
                    p.getName(),
                    p.getGender().name(),
                    p.getAge(),
                    p.getContactNumber().isEmpty() ? "N/A" : p.getContactNumber(),
                    p.isHipertension() ? "Yes" : "No",
                    p.isDiabetes() ? "Yes" : "No",
                    p.hasChronicConditions() ? "Active" : "None"
            });
        }
        lblCount.setText(patients.size() + " patients registered (persisted in data/app/patients.csv)");
    }

    private Patient getSelectedPatientOrWarn() {
        int row = patientTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a patient from the table first.",
                    "Selection Required", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        Long id = (Long) tableModel.getValueAt(row, 0);
        return patientService.getPatientById(id).orElse(null);
    }

    private void editSelectedPatient() {
        Patient p = getSelectedPatientOrWarn();
        if (p != null) {
            openPatientDialog(p);
        }
    }

    private void viewSelectedPatientHistory() {
        Patient p = getSelectedPatientOrWarn();
        if (p == null) return;

        List<Appointment> history = patientService.getPatientHistory(p.getId());
        if (history.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Patient " + p.getName() + " has no appointment history records.",
                    "Appointment History", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Appointment History for: ").append(p.getName()).append(" (ID: ").append(p.getId()).append(")\n\n");
        for (Appointment a : history) {
            sb.append(String.format("• Appt #%d | Date: %s %s | Doctor: %s | Status: %s | ML Risk: %s\n",
                    a.getAppointmentId(),
                    a.getAppointmentDay(),
                    a.getAppointmentTime() != null ? a.getAppointmentTime() : "",
                    a.getDoctor() != null ? a.getDoctor().getName() : "N/A",
                    a.getAttendanceStatus().name(),
                    a.getPredictionResult() != null && a.getPredictionResult().isModelAvailable() ?
                            a.getPredictionResult().getRiskCategory().name() : "N/A"));
        }

        JTextArea ta = new JTextArea(sb.toString(), 14, 50);
        ta.setEditable(false);
        ta.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JOptionPane.showMessageDialog(this, new JScrollPane(ta), "Patient History", JOptionPane.PLAIN_MESSAGE);
    }

    private void deleteSelectedPatient() {
        Patient p = getSelectedPatientOrWarn();
        if (p == null) return;

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete patient: " + p.getName() + " (ID: " + p.getId() + ")?\n" +
                        "This will remove the record from data/app/patients.csv.",
                "Confirm Deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                boolean deleted = patientService.deletePatient(p.getId());
                if (deleted) {
                    JOptionPane.showMessageDialog(this, "Patient deleted successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                    refreshTable();
                }
            } catch (ClinicFlowException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Cannot Delete", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void bookForSelectedPatient() {
        Patient p = getSelectedPatientOrWarn();
        if (p != null) {
            parentApp.openAppointmentBookingDialog(p.getId());
        }
    }

    private void openPatientDialog(Patient existing) {
        boolean isEdit = existing != null;
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                isEdit ? "Edit Patient Details" : "Register New Patient", true);
        dlg.setLayout(new BorderLayout());

        JPanel form = new JPanel(new GridLayout(8, 2, 10, 12));
        form.setBorder(new EmptyBorder(20, 24, 20, 24));
        form.setBackground(Color.WHITE);

        JTextField txtName = UITheme.createTextField(20);
        JComboBox<Gender> cmbGender = new JComboBox<>(Gender.values());
        JTextField txtAge = UITheme.createTextField(10);
        JTextField txtContact = UITheme.createTextField(15);
        JCheckBox chkScholarship = new JCheckBox("Welfare / Scholarship Program");
        JCheckBox chkHypertension = new JCheckBox("Hypertension");
        JCheckBox chkDiabetes = new JCheckBox("Diabetes");
        JCheckBox chkAlcoholism = new JCheckBox("Alcoholism");

        chkScholarship.setOpaque(false);
        chkHypertension.setOpaque(false);
        chkDiabetes.setOpaque(false);
        chkAlcoholism.setOpaque(false);

        if (isEdit) {
            txtName.setText(existing.getName());
            cmbGender.setSelectedItem(existing.getGender());
            txtAge.setText(String.valueOf(existing.getAge()));
            txtContact.setText(existing.getContactNumber());
            chkScholarship.setSelected(existing.isScholarship());
            chkHypertension.setSelected(existing.isHipertension());
            chkDiabetes.setSelected(existing.isDiabetes());
            chkAlcoholism.setSelected(existing.isAlcoholism());
        }

        form.add(new JLabel("Full Name: *"));
        form.add(txtName);
        form.add(new JLabel("Gender: *"));
        form.add(cmbGender);
        form.add(new JLabel("Age (years): *"));
        form.add(txtAge);
        form.add(new JLabel("Contact Phone:"));
        form.add(txtContact);
        form.add(new JLabel("Chronic Conditions:"));
        form.add(chkHypertension);
        form.add(new JLabel(""));
        form.add(chkDiabetes);
        form.add(new JLabel(""));
        form.add(chkAlcoholism);
        form.add(new JLabel("Welfare Enrollment:"));
        form.add(chkScholarship);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        btnPanel.setBackground(UITheme.COLOR_BG);
        JButton btnCancel = UITheme.createButton("Cancel", false);
        btnCancel.addActionListener(e -> dlg.dispose());

        JButton btnSave = UITheme.createAccentButton(isEdit ? "Update Patient" : "Save Patient");
        btnSave.addActionListener(e -> {
            try {
                String name = txtName.getText().trim();
                Gender gender = (Gender) cmbGender.getSelectedItem();
                int age = Integer.parseInt(txtAge.getText().trim());
                String contact = txtContact.getText().trim();
                boolean schol = chkScholarship.isSelected();
                boolean hyper = chkHypertension.isSelected();
                boolean diab = chkDiabetes.isSelected();
                boolean alc = chkAlcoholism.isSelected();

                if (isEdit) {
                    patientService.updatePatient(existing.getId(), name, gender, age, contact, schol, hyper, diab, alc, 0);
                    JOptionPane.showMessageDialog(dlg, "Patient updated successfully.", "Saved", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    patientService.registerPatient(null, name, gender, age, contact, schol, hyper, diab, alc, 0);
                    JOptionPane.showMessageDialog(dlg, "Patient registered successfully.", "Saved", JOptionPane.INFORMATION_MESSAGE);
                }
                dlg.dispose();
                refreshTable();
            } catch (NumberFormatException nfe) {
                JOptionPane.showMessageDialog(dlg, "Please enter a valid numeric age.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dlg, ex.getMessage(), "Validation Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnPanel.add(btnCancel);
        btnPanel.add(btnSave);

        dlg.add(form, BorderLayout.CENTER);
        dlg.add(btnPanel, BorderLayout.SOUTH);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setResizable(false);
        dlg.setVisible(true);
    }
}
