package com.clinicflow.ui;

import com.clinicflow.model.Doctor;
import com.clinicflow.model.Gender;
import com.clinicflow.service.DoctorService;
import com.clinicflow.ui.common.CardPanel;
import com.clinicflow.ui.common.UITheme;
import com.clinicflow.util.ClinicFlowException;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Screen for managing medical staff, specialties, consultation rooms,
 * contact details, and clinic availability status.
 */
public class DoctorManagementPanel extends JPanel {

    private final DoctorService doctorService;
    private final ClinicFlowApp parentApp;

    private JTextField txtSearch;
    private DefaultTableModel tableModel;
    private JTable doctorTable;
    private JLabel lblCount;

    public DoctorManagementPanel(DoctorService doctorService, ClinicFlowApp parentApp) {
        this.doctorService = doctorService;
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

        JLabel title = new JLabel("Doctor & Medical Staff Management");
        title.setFont(UITheme.FONT_TITLE);
        title.setForeground(UITheme.COLOR_PRIMARY);

        lblCount = new JLabel("0 doctors on duty");
        lblCount.setFont(UITheme.FONT_SUBTITLE);
        lblCount.setForeground(UITheme.COLOR_TEXT_SECONDARY);

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 2, 2));
        titleBlock.setOpaque(false);
        titleBlock.add(title);
        titleBlock.add(lblCount);

        JPanel actionBlock = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionBlock.setOpaque(false);

        JButton btnAddDoctor = UITheme.createAccentButton("+ Add Doctor");
        btnAddDoctor.addActionListener(e -> openDoctorDialog(null));

        JButton btnRefresh = UITheme.createButton("Refresh", false);
        btnRefresh.addActionListener(e -> refreshTable());

        actionBlock.add(btnRefresh);
        actionBlock.add(btnAddDoctor);

        headerPanel.add(titleBlock, BorderLayout.WEST);
        headerPanel.add(actionBlock, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // Center: Card with Search and Table
        CardPanel contentCard = new CardPanel();
        contentCard.setLayout(new BorderLayout(14, 14));
        contentCard.setBorder(new EmptyBorder(18, 20, 18, 20));

        // Search Bar
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        searchBar.setOpaque(false);
        JLabel lblFind = new JLabel("Search Doctor:");
        lblFind.setFont(UITheme.FONT_BODY_BOLD);
        lblFind.setForeground(UITheme.COLOR_TEXT_PRIMARY);

        txtSearch = UITheme.createTextField(25);
        txtSearch.setToolTipText("Search by doctor name or specialty...");

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
        String[] cols = {"Doctor ID", "Doctor Name", "Specialty", "Consultation Room", "Contact", "Availability Status"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        doctorTable = new JTable(tableModel);
        UITheme.styleTable(doctorTable);

        JScrollPane scrollPane = new JScrollPane(doctorTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(UITheme.COLOR_BORDER));
        scrollPane.getViewport().setBackground(Color.WHITE);
        contentCard.add(scrollPane, BorderLayout.CENTER);

        // Action Toolbar Below Table
        JPanel tableActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        tableActions.setOpaque(false);

        JButton btnToggle = UITheme.createAccentButton("Toggle Availability");
        btnToggle.addActionListener(e -> toggleDoctorAvailability());

        JButton btnEdit = UITheme.createButton("Edit Doctor", false);
        btnEdit.addActionListener(e -> editSelectedDoctor());

        JButton btnDelete = UITheme.createButton("Remove Doctor", false);
        btnDelete.setForeground(UITheme.COLOR_RISK_HIGH);
        btnDelete.addActionListener(e -> deleteSelectedDoctor());

        tableActions.add(btnToggle);
        tableActions.add(btnEdit);
        tableActions.add(btnDelete);

        contentCard.add(tableActions, BorderLayout.SOUTH);

        add(contentCard, BorderLayout.CENTER);
    }

    private void performSearch() {
        String q = txtSearch.getText().trim();
        List<Doctor> results = doctorService.searchDoctors(q);
        populateTable(results);
    }

    public void refreshTable() {
        populateTable(doctorService.getAllDoctors());
    }

    private void populateTable(List<Doctor> doctors) {
        tableModel.setRowCount(0);
        long available = 0;
        for (Doctor d : doctors) {
            if (d.isAvailable()) available++;
            tableModel.addRow(new Object[]{
                    d.getId(),
                    "Dr. " + d.getName(),
                    d.getSpecialty(),
                    d.getRoomNumber(),
                    d.getContactNumber().isEmpty() ? "N/A" : d.getContactNumber(),
                    d.isAvailable() ? "AVAILABLE" : "ON LEAVE / UNAVAILABLE"
            });
        }
        lblCount.setText(doctors.size() + " doctors registered (" + available + " currently available)");
    }

    private Doctor getSelectedDoctorOrWarn() {
        int row = doctorTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a doctor from the table first.",
                    "Selection Required", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        Long id = (Long) tableModel.getValueAt(row, 0);
        return doctorService.getDoctorById(id).orElse(null);
    }

    private void toggleDoctorAvailability() {
        Doctor d = getSelectedDoctorOrWarn();
        if (d != null) {
            Doctor updated = doctorService.toggleAvailability(d.getId());
            refreshTable();
            JOptionPane.showMessageDialog(this,
                    "Dr. " + updated.getName() + " availability updated to: " +
                            (updated.isAvailable() ? "AVAILABLE" : "UNAVAILABLE"),
                    "Status Updated", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void editSelectedDoctor() {
        Doctor d = getSelectedDoctorOrWarn();
        if (d != null) {
            openDoctorDialog(d);
        }
    }

    private void deleteSelectedDoctor() {
        Doctor d = getSelectedDoctorOrWarn();
        if (d == null) return;

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to remove Dr. " + d.getName() + " (ID: " + d.getId() + ")?\n" +
                        "Note: Cannot delete doctors with scheduled future appointments.",
                "Confirm Doctor Removal", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                boolean deleted = doctorService.deleteDoctor(d.getId());
                if (deleted) {
                    JOptionPane.showMessageDialog(this, "Doctor removed successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                    refreshTable();
                }
            } catch (ClinicFlowException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Cannot Remove Doctor", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void openDoctorDialog(Doctor existing) {
        boolean isEdit = existing != null;
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                isEdit ? "Edit Doctor Profile" : "Add Attending Doctor", true);
        dlg.setLayout(new BorderLayout());

        JPanel form = new JPanel(new GridLayout(7, 2, 10, 12));
        form.setBorder(new EmptyBorder(20, 24, 20, 24));
        form.setBackground(Color.WHITE);

        JTextField txtName = UITheme.createTextField(20);
        JComboBox<Gender> cmbGender = new JComboBox<>(Gender.values());
        JTextField txtAge = UITheme.createTextField(10);
        JTextField txtSpecialty = UITheme.createTextField(20);
        JTextField txtRoom = UITheme.createTextField(10);
        JTextField txtContact = UITheme.createTextField(15);
        JCheckBox chkAvailable = new JCheckBox("Available for consultations", true);
        chkAvailable.setOpaque(false);

        if (isEdit) {
            txtName.setText(existing.getName());
            cmbGender.setSelectedItem(existing.getGender());
            txtAge.setText(String.valueOf(existing.getAge()));
            txtSpecialty.setText(existing.getSpecialty());
            txtRoom.setText(existing.getRoomNumber());
            txtContact.setText(existing.getContactNumber());
            chkAvailable.setSelected(existing.isAvailable());
        }

        form.add(new JLabel("Doctor Full Name: *"));
        form.add(txtName);
        form.add(new JLabel("Gender: *"));
        form.add(cmbGender);
        form.add(new JLabel("Age (years):"));
        form.add(txtAge);
        form.add(new JLabel("Medical Specialty: *"));
        form.add(txtSpecialty);
        form.add(new JLabel("Consultation Room:"));
        form.add(txtRoom);
        form.add(new JLabel("Contact Phone:"));
        form.add(txtContact);
        form.add(new JLabel("Duty Status:"));
        form.add(chkAvailable);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        btnPanel.setBackground(UITheme.COLOR_BG);
        JButton btnCancel = UITheme.createButton("Cancel", false);
        btnCancel.addActionListener(e -> dlg.dispose());

        JButton btnSave = UITheme.createAccentButton(isEdit ? "Update Doctor" : "Save Doctor");
        btnSave.addActionListener(e -> {
            try {
                String name = txtName.getText().trim();
                Gender gender = (Gender) cmbGender.getSelectedItem();
                int age = txtAge.getText().trim().isEmpty() ? 40 : Integer.parseInt(txtAge.getText().trim());
                String specialty = txtSpecialty.getText().trim();
                String room = txtRoom.getText().trim();
                String contact = txtContact.getText().trim();
                boolean available = chkAvailable.isSelected();

                if (isEdit) {
                    doctorService.updateDoctor(existing.getId(), name, gender, age, specialty, room, contact, available);
                    JOptionPane.showMessageDialog(dlg, "Doctor profile updated.", "Saved", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    doctorService.registerDoctor(null, name, gender, age, specialty, room, contact, available);
                    JOptionPane.showMessageDialog(dlg, "Doctor registered successfully.", "Saved", JOptionPane.INFORMATION_MESSAGE);
                }
                dlg.dispose();
                refreshTable();
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
