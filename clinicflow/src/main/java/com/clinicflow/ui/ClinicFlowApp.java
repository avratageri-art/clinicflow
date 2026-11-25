package com.clinicflow.ui;

import com.clinicflow.ml.DataValidator;
import com.clinicflow.model.Doctor;
import com.clinicflow.model.Gender;
import com.clinicflow.repository.*;
import com.clinicflow.service.*;
import com.clinicflow.ui.common.UITheme;
import com.clinicflow.util.ApplicationConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Main application window for ClinicFlow.
 * Implements sidebar navigation, CardLayout screen switching, asynchronous background
 * Weka model initialization, and status bar monitoring.
 */
public class ClinicFlowApp extends JFrame {

    private final ApplicationConfig config;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final ReminderRepository reminderRepository;

    private final PatientService patientService;
    private final DoctorService doctorService;
    private final AppointmentService appointmentService;
    private final ReminderService reminderService;
    private final ModelInsightsService modelInsightsService;
    private final WekaNoShowPredictionService predictionService;

    // UI Navigation Components
    private CardLayout cardLayout;
    private JPanel centerCards;
    private Map<String, JButton> navButtons = new HashMap<>();
    private JLabel lblModelStatus;
    private JLabel lblStorageStatus;

    // Screens
    private DashboardPanel dashboardPanel;
    private PatientManagementPanel patientPanel;
    private DoctorManagementPanel doctorPanel;
    private AppointmentManagementPanel appointmentPanel;
    private QueueManagementPanel queuePanel;
    private ReminderManagementPanel reminderPanel;
    private ModelInsightsPanel insightsPanel;
    private AboutPanel aboutPanel;

    public ClinicFlowApp() {
        this(ApplicationConfig.getInstance());
    }

    public ClinicFlowApp(ApplicationConfig config) {
        super("ClinicFlow — Smart Clinic Appointment & Queue Management System");
        this.config = config;

        // 1. Initialize Repositories (CSV Persistent)
        this.patientRepository = new CsvPatientRepository();
        this.doctorRepository = new CsvDoctorRepository();
        this.appointmentRepository = new CsvAppointmentRepository(patientRepository, doctorRepository);
        this.reminderRepository = new CsvReminderRepository();

        // 2. Initialize Prediction Service
        this.predictionService = new WekaNoShowPredictionService(config);

        // 3. Initialize Domain Services
        DataValidator validator = new DataValidator();
        this.patientService = new PatientService(patientRepository, appointmentRepository);
        this.doctorService = new DoctorService(doctorRepository, appointmentRepository);
        this.appointmentService = new AppointmentService(patientRepository, appointmentRepository, doctorRepository, validator, predictionService);
        this.reminderService = new ReminderService(reminderRepository, appointmentRepository);
        this.modelInsightsService = new ModelInsightsService(config);

        // Seed initial attending doctors if database is empty
        seedInitialDoctorsIfEmpty();

        // 4. Initialize Desktop UI
        initUI();

        // 5. Asynchronous Background Model Loading (SwingWorker)
        loadModelInBackground();
    }

    private void seedInitialDoctorsIfEmpty() {
        if (doctorRepository.count() == 0) {
            doctorService.registerDoctor(101L, "Beatriz Costa", Gender.F, 42, "Endocrinology", "Room 205", "+55-27-99123-4567", true);
            doctorService.registerDoctor(102L, "Carlos Eduardo", Gender.M, 48, "Cardiology", "Room 108", "+55-27-99234-5678", true);
            doctorService.registerDoctor(103L, "Sofia Alcantara", Gender.F, 36, "Pediatrics", "Room 302", "+55-27-99345-6789", true);
            doctorService.registerDoctor(104L, "Mateus Oliveira", Gender.M, 54, "General Practice", "Room 101", "+55-27-99456-7890", true);
        }
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1320, 840);
        setMinimumSize(new Dimension(1100, 700));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UITheme.COLOR_BG);

        // A. Left Sidebar
        JPanel sidebar = createSidebar();
        root.add(sidebar, BorderLayout.WEST);

        // B. Center Content Area (CardLayout)
        cardLayout = new CardLayout();
        centerCards = new JPanel(cardLayout);
        centerCards.setOpaque(false);

        dashboardPanel = new DashboardPanel(patientService, doctorService, appointmentService, this);
        patientPanel = new PatientManagementPanel(patientService, this);
        doctorPanel = new DoctorManagementPanel(doctorService, this);
        appointmentPanel = new AppointmentManagementPanel(appointmentService, this);
        queuePanel = new QueueManagementPanel(appointmentService, this);
        reminderPanel = new ReminderManagementPanel(reminderService, this);
        insightsPanel = new ModelInsightsPanel(modelInsightsService, this::loadModelInBackground);
        aboutPanel = new AboutPanel();

        centerCards.add(dashboardPanel, "Dashboard");
        centerCards.add(patientPanel, "Patients");
        centerCards.add(doctorPanel, "Doctors");
        centerCards.add(appointmentPanel, "Appointments");
        centerCards.add(queuePanel, "Queue Management");
        centerCards.add(reminderPanel, "Reminder Management");
        centerCards.add(insightsPanel, "ML Model Insights");
        centerCards.add(aboutPanel, "About / Help");

        root.add(centerCards, BorderLayout.CENTER);

        // C. Bottom Status Bar
        JPanel statusBar = createStatusBar();
        root.add(statusBar, BorderLayout.SOUTH);

        setContentPane(root);
        navigateTo("Dashboard");
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(250, 0));
        sidebar.setBackground(UITheme.COLOR_PRIMARY);

        // Brand Banner
        JPanel brandPanel = new JPanel(new BorderLayout(6, 6));
        brandPanel.setOpaque(false);
        brandPanel.setBorder(new EmptyBorder(24, 20, 24, 20));

        JLabel brandTitle = new JLabel("ClinicFlow");
        brandTitle.setFont(new Font("Segoe UI", Font.BOLD, 24));
        brandTitle.setForeground(Color.WHITE);

        JLabel brandSub = new JLabel("Smart Clinic & Queue System");
        brandSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        brandSub.setForeground(new Color(186, 230, 253)); // Light Sky

        brandPanel.add(brandTitle, BorderLayout.NORTH);
        brandPanel.add(brandSub, BorderLayout.SOUTH);
        sidebar.add(brandPanel, BorderLayout.NORTH);

        // Navigation Items
        JPanel navList = new JPanel();
        navList.setLayout(new BoxLayout(navList, BoxLayout.Y_AXIS));
        navList.setOpaque(false);
        navList.setBorder(new EmptyBorder(10, 12, 10, 12));

        String[] navItems = {
                "Dashboard", "Patients", "Doctors", "Appointments",
                "Queue Management", "Reminder Management", "ML Model Insights", "About / Help"
        };

        for (String item : navItems) {
            JButton btn = new JButton(item);
            btn.setMaximumSize(new Dimension(230, 42));
            btn.setFont(UITheme.FONT_BODY_BOLD);
            btn.setForeground(new Color(224, 242, 254));
            btn.setBackground(UITheme.COLOR_PRIMARY);
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setHorizontalAlignment(SwingConstants.LEFT);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.setBorder(new EmptyBorder(10, 16, 10, 16));

            btn.addActionListener(e -> navigateTo(item));
            navButtons.put(item, btn);
            navList.add(btn);
            navList.add(Box.createVerticalStrut(4));
        }

        sidebar.add(navList, BorderLayout.CENTER);

        // Footer info in sidebar
        JPanel sideFooter = new JPanel(new GridLayout(2, 1, 2, 2));
        sideFooter.setOpaque(false);
        sideFooter.setBorder(new EmptyBorder(16, 20, 20, 20));

        JLabel lblVer = new JLabel("Version 1.0.0 (Java 17)");
        lblVer.setFont(UITheme.FONT_SMALL);
        lblVer.setForeground(new Color(148, 163, 184));

        JLabel lblEngine = new JLabel("Weka 3.8.6 ML Engine");
        lblEngine.setFont(UITheme.FONT_SMALL);
        lblEngine.setForeground(new Color(148, 163, 184));

        sideFooter.add(lblVer);
        sideFooter.add(lblEngine);
        sidebar.add(sideFooter, BorderLayout.SOUTH);

        return sidebar;
    }

    private JPanel createStatusBar() {
        JPanel status = new JPanel(new BorderLayout(16, 0));
        status.setBackground(UITheme.COLOR_CARD_BG);
        status.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UITheme.COLOR_BORDER),
                new EmptyBorder(6, 18, 6, 18)
        ));

        lblModelStatus = new JLabel("ML MODEL: LOADING IN BACKGROUND...");
        lblModelStatus.setFont(UITheme.FONT_SMALL);
        lblModelStatus.setForeground(UITheme.COLOR_RISK_MEDIUM);

        lblStorageStatus = new JLabel("Storage: data/app/*.csv | System Active");
        lblStorageStatus.setFont(UITheme.FONT_SMALL);
        lblStorageStatus.setForeground(UITheme.COLOR_TEXT_SECONDARY);

        JLabel lblTime = new JLabel("ClinicFlow v1.0.0 | Local CSV + Weka ML");
        lblTime.setFont(UITheme.FONT_SMALL);
        lblTime.setForeground(UITheme.COLOR_TEXT_MUTED);

        status.add(lblModelStatus, BorderLayout.WEST);
        status.add(lblStorageStatus, BorderLayout.CENTER);
        status.add(lblTime, BorderLayout.EAST);

        return status;
    }

    public void navigateTo(String screenName) {
        cardLayout.show(centerCards, screenName);

        // Highlight selected nav button
        for (Map.Entry<String, JButton> entry : navButtons.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(screenName)) {
                entry.getValue().setBackground(UITheme.COLOR_ACCENT);
                entry.getValue().setForeground(Color.WHITE);
            } else {
                entry.getValue().setBackground(UITheme.COLOR_PRIMARY);
                entry.getValue().setForeground(new Color(224, 242, 254));
            }
        }

        // Refresh panel data
        switch (screenName) {
            case "Dashboard" -> dashboardPanel.refreshData();
            case "Patients" -> patientPanel.refreshTable();
            case "Doctors" -> doctorPanel.refreshTable();
            case "Appointments" -> appointmentPanel.refreshTable();
            case "Queue Management" -> queuePanel.refreshQueue();
            case "Reminder Management" -> reminderPanel.refreshData();
        }
    }

    public void openAppointmentBookingDialog() {
        openAppointmentBookingDialog(null);
    }

    public void openAppointmentBookingDialog(Long preselectedPatientId) {
        AppointmentBookingDialog dlg = new AppointmentBookingDialog(
                this, patientService, doctorService, appointmentService, preselectedPatientId,
                () -> {
                    dashboardPanel.refreshData();
                    appointmentPanel.refreshTable();
                    queuePanel.refreshQueue();
                    reminderPanel.refreshData();
                }
        );
        dlg.setVisible(true);
    }

    private void loadModelInBackground() {
        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                long start = System.currentTimeMillis();
                boolean loaded = predictionService.reloadModel();
                long elapsed = System.currentTimeMillis() - start;
                System.out.printf("[Background Model Loader] Weka model loaded in %d ms (Status: %b)\n", elapsed, loaded);
                return loaded;
            }

            @Override
            protected void done() {
                try {
                    boolean success = get();
                    if (success) {
                        lblModelStatus.setText("● ML MODEL: AVAILABLE (Weka Balanced RandomForest 100 Trees)");
                        lblModelStatus.setForeground(UITheme.COLOR_RISK_LOW);
                    } else {
                        lblModelStatus.setText("▲ ML MODEL: UNAVAILABLE (File absent or uninitialized)");
                        lblModelStatus.setForeground(UITheme.COLOR_RISK_HIGH);
                    }
                } catch (Exception e) {
                    lblModelStatus.setText("▲ ML MODEL: ERROR LOADING MODEL");
                    lblModelStatus.setForeground(UITheme.COLOR_RISK_HIGH);
                }
            }
        };
        worker.execute();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            ClinicFlowApp app = new ClinicFlowApp();
            app.setVisible(true);
        });
    }
}
