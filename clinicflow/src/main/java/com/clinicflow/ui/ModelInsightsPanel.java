package com.clinicflow.ui;

import com.clinicflow.ml.NoShowModelTrainer;
import com.clinicflow.service.ModelInsightsService;
import com.clinicflow.service.ModelInsightsService.ModelSpec;
import com.clinicflow.ui.common.CardPanel;
import com.clinicflow.ui.common.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * Architectural dashboard explaining the Weka Machine Learning engine,
 * historical training specs, empirical evaluation metrics, and clinical trade-offs.
 */
public class ModelInsightsPanel extends JPanel {

    private final ModelInsightsService insightsService;
    private final Runnable onModelTrained;
    private JButton btnTrainModel;

    public ModelInsightsPanel(ModelInsightsService insightsService) {
        this(insightsService, null);
    }

    public ModelInsightsPanel(ModelInsightsService insightsService, Runnable onModelTrained) {
        this.insightsService = insightsService;
        this.onModelTrained = onModelTrained;

        setLayout(new BorderLayout(20, 20));
        setBackground(UITheme.COLOR_BG);
        setBorder(new EmptyBorder(24, 28, 24, 28));

        initUI();
    }

    private void initUI() {
        ModelSpec spec = insightsService.getModelSpecification();

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel title = new JLabel("Machine Learning Engine & Model Insights");
        title.setFont(UITheme.FONT_TITLE);
        title.setForeground(UITheme.COLOR_PRIMARY);

        JLabel subtitle = new JLabel("Weka RandomForest supervised classification architecture, empirical metrics, and clinical validity");
        subtitle.setFont(UITheme.FONT_SUBTITLE);
        subtitle.setForeground(UITheme.COLOR_TEXT_SECONDARY);

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 2, 2));
        titleBlock.setOpaque(false);
        titleBlock.add(title);
        titleBlock.add(subtitle);

        headerPanel.add(titleBlock, BorderLayout.WEST);

        boolean modelPresent = insightsService.isModelFilePresent();
        btnTrainModel = UITheme.createAccentButton(modelPresent ? "Retrain ML Model" : "Train ML Model");
        btnTrainModel.setToolTipText("Run the Weka RandomForest training pipeline on data/raw/appointments.csv");
        btnTrainModel.addActionListener(e -> triggerModelTraining());

        JPanel actionBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        actionBox.setOpaque(false);
        actionBox.add(btnTrainModel);
        headerPanel.add(actionBox, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // Center: Scrollable container with cards
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);

        // 1. Geographic & Demographic Limitation Warning Banner
        JPanel geoBanner = new JPanel(new BorderLayout());
        geoBanner.setBackground(new Color(254, 242, 242)); // Light Red
        geoBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(252, 165, 165), 1, true),
                new EmptyBorder(12, 16, 12, 16)
        ));
        JLabel lblGeo = new JLabel(
                "<html><b>Clinical Generalization & Demographic Warning:</b> This Machine Learning engine was trained on " +
                "genuine historical records from the SUS public municipal healthcare network in <b>Vitória, Espírito Santo, Brazil (2016)</b>. " +
                "While the Java architecture, Weka pipeline, and algorithms are globally valid, patient behaviors, urban transit, and clinic workflows " +
                "differ in Indian healthcare environments. <b>Local validation on Indian clinic datasets is strictly required prior to real-world medical deployment in India.</b></html>"
        );
        lblGeo.setFont(UITheme.FONT_BODY);
        lblGeo.setForeground(new Color(153, 27, 27));
        geoBanner.add(lblGeo, BorderLayout.CENTER);
        centerPanel.add(geoBanner);
        centerPanel.add(Box.createVerticalStrut(16));

        // 2. Training Specs & Model Metadata Grid
        CardPanel specCard = new CardPanel();
        specCard.setLayout(new BorderLayout(10, 10));
        specCard.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel specTitle = new JLabel("Model Architecture & Dataset Specifications");
        specTitle.setFont(UITheme.FONT_HEADER);
        specTitle.setForeground(UITheme.COLOR_PRIMARY);
        specCard.add(specTitle, BorderLayout.NORTH);

        JPanel specGrid = new JPanel(new GridLayout(4, 2, 16, 8));
        specGrid.setOpaque(false);

        specGrid.add(createSpecItem("ML Algorithm", spec.algorithm));
        specGrid.add(createSpecItem("Dataset Name", spec.datasetName));
        specGrid.add(createSpecItem("Raw Dataset Records", String.format("%,d records", spec.originalRecords)));
        specGrid.add(createSpecItem("Clean Dataset Records", String.format("%,d valid records (6 entry anomalies filtered)", spec.cleanRecords)));
        specGrid.add(createSpecItem("Training Partition (80%)", String.format("%,d records (Chronological sort by ScheduledDay)", spec.trainCount)));
        specGrid.add(createSpecItem("Testing Partition (20%)", String.format("%,d records (Held-out untouched test set)", spec.testCount)));
        specGrid.add(createSpecItem("Hyperparameters", String.format("%d trees, seed=%d, maxDepth=Unlimited (0)", spec.numTrees, spec.seed)));
        specGrid.add(createSpecItem("Target Variable", spec.targetVariable));

        specCard.add(specGrid, BorderLayout.CENTER);
        centerPanel.add(specCard);
        centerPanel.add(Box.createVerticalStrut(16));

        // 3. Predictive Feature Schema
        CardPanel featCard = new CardPanel();
        featCard.setLayout(new BorderLayout(10, 10));
        featCard.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel featTitle = new JLabel("Engineered Booking-Time Predictive Features (Zero Target Leakage)");
        featTitle.setFont(UITheme.FONT_HEADER);
        featTitle.setForeground(UITheme.COLOR_PRIMARY);
        featCard.add(featTitle, BorderLayout.NORTH);

        String featDesc = "<html>The model operates strictly on operational attributes available at the exact moment of appointment registration:<br>" +
                "• <b>leadTimeDays (Numeric):</b> Elapsed days between booking date and appointment date (strongest attendance predictor).<br>" +
                "• <b>appointmentWeekday (Nominal):</b> Day of the week of the consultation (Monday–Sunday).<br>" +
                "• <b>bookingWeekday (Nominal):</b> Day of the week on which the booking call was received.<br>" +
                "• <b>bookingHour (Numeric):</b> Hour of day (0–23) the appointment was registered.<br>" +
                "• <b>sameDayBooking (Nominal):</b> Binary indicator whether leadTimeDays == 0.<br>" +
                "• <b>age (Numeric):</b> Patient age in years.<br>" +
                "<i>Strictly Excluded:</i> PatientId, AppointmentID (identifiers), No-show (target), and SMS_received (occurs post-booking).</html>";
        JLabel lblFeat = new JLabel(featDesc);
        lblFeat.setFont(UITheme.FONT_BODY);
        lblFeat.setForeground(UITheme.COLOR_TEXT_PRIMARY);
        featCard.add(lblFeat, BorderLayout.CENTER);
        centerPanel.add(featCard);
        centerPanel.add(Box.createVerticalStrut(16));

        // 4. Comparative Evaluation Table
        CardPanel evalCard = new CardPanel();
        evalCard.setLayout(new BorderLayout(10, 10));
        evalCard.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel evalTitle = new JLabel("Empirical Model Performance on Untouched 20% Test Partition (22,104 Records)");
        evalTitle.setFont(UITheme.FONT_HEADER);
        evalTitle.setForeground(UITheme.COLOR_PRIMARY);
        evalCard.add(evalTitle, BorderLayout.NORTH);

        String[] cols = {"Candidate Model", "Accuracy", "Precision (No-Show)", "Recall (No-Show)", "F1-Score", "ROC-AUC", "Status"};
        DefaultTableModel evalModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        evalModel.addRow(new Object[]{"ZeroR (Majority Baseline)", "88.09%", "0.0000", "0.0000", "0.0000", "0.5000", "Naive Baseline"});
        evalModel.addRow(new Object[]{"Standard RandomForest", "84.34%", "0.2432", "0.1489", "0.1847", "0.6660", "Candidate 1"});
        evalModel.addRow(new Object[]{
                "Balanced RandomForest",
                String.format("%.2f%%", spec.accuracy * 100.0),
                String.format("%.4f", spec.precision),
                String.format("%.4f", spec.recall),
                String.format("%.4f", spec.f1),
                String.format("%.4f", spec.rocArea),
                "SELECTED OPTIMAL"
        });

        JTable evalTable = new JTable(evalModel);
        UITheme.styleTable(evalTable);
        JScrollPane evalScroll = new JScrollPane(evalTable);
        evalScroll.setPreferredSize(new Dimension(800, 125));
        evalCard.add(evalScroll, BorderLayout.CENTER);

        // Confusion matrix text below table
        String cmText = String.format(
                "<html><b>Selected Model Confusion Matrix:</b> " +
                "True Negatives (Attended) = <b>%,d</b> | False Positives (False Alarms) = <b>%,d</b> | " +
                "False Negatives (Missed No-Shows) = <b>%,d</b> | True Positives (Detected No-Shows) = <b>%,d</b></html>",
                spec.trueNegatives, spec.falsePositives, spec.falseNegatives, spec.truePositives
        );
        JLabel lblCm = new JLabel(cmText);
        lblCm.setFont(UITheme.FONT_BODY);
        lblCm.setBorder(new EmptyBorder(8, 0, 4, 0));
        evalCard.add(lblCm, BorderLayout.SOUTH);

        centerPanel.add(evalCard);
        centerPanel.add(Box.createVerticalStrut(16));

        // 5. Student-Friendly Machine Learning Guide
        CardPanel guideCard = new CardPanel();
        guideCard.setLayout(new BorderLayout(10, 10));
        guideCard.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel guideTitle = new JLabel("Student-Friendly Machine Learning Guide for Coursework Presentations");
        guideTitle.setFont(UITheme.FONT_HEADER);
        guideTitle.setForeground(UITheme.COLOR_PRIMARY);
        guideCard.add(guideTitle, BorderLayout.NORTH);

        String guideHtml = "<html><body style='line-height: 1.4;'>" +
                "<b>1. How RandomForest Works:</b> An ensemble of 100 individual decision trees. Each tree evaluates a bootstrap sample of data and " +
                "considers random subsets of features at each split to prevent overfitting and decorrelate predictions.<br><br>" +
                "<b>2. The Accuracy Paradox in Healthcare:</b> ZeroR achieves 88.09% accuracy by always predicting 'Attended', but catches 0 no-shows (Recall = 0.00). " +
                "Balanced Random Forest achieves 77.50% accuracy but catches <b>32.93% of no-shows (867 patients)</b>. In healthcare, Recall is far more valuable than naive accuracy.<br><br>" +
                "<b>3. Asymmetric Error Costs:</b> A False Positive merely results in a courtesy phone call or SMS reminder (benign). A False Negative results in an empty, wasted clinic slot.<br><br>" +
                "<b>4. Probability Calibration Notice:</b> Probabilities from <code>distributionForInstance()</code> represent ensemble vote proportions. " +
                "They are reliable ordinal risk scores (Low: &lt;20%, Medium: 20–50%, High: &ge;50%), but are not statistically calibrated actuarial probabilities." +
                "</body></html>";
        JLabel lblGuide = new JLabel(guideHtml);
        lblGuide.setFont(UITheme.FONT_BODY);
        guideCard.add(lblGuide, BorderLayout.CENTER);

        centerPanel.add(guideCard);

        JScrollPane scroll = new JScrollPane(centerPanel);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(UITheme.COLOR_BG);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
    }

    private JPanel createSpecItem(String label, String value) {
        JPanel p = new JPanel(new BorderLayout(4, 2));
        p.setOpaque(false);
        JLabel l = new JLabel(label + ":");
        l.setFont(UITheme.FONT_SMALL);
        l.setForeground(UITheme.COLOR_TEXT_SECONDARY);
        JLabel v = new JLabel(value);
        v.setFont(UITheme.FONT_BODY_BOLD);
        v.setForeground(UITheme.COLOR_TEXT_PRIMARY);
        p.add(l, BorderLayout.NORTH);
        p.add(v, BorderLayout.CENTER);
        return p;
    }

    private void triggerModelTraining() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "This will run the full Weka RandomForest training pipeline on data/raw/appointments.csv\n"
                        + "(110,527 records) and overwrite models/no-show-random-forest.model.\n\nProceed?",
                "Train ML Model",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        btnTrainModel.setEnabled(false);
        btnTrainModel.setText("Training Model...");

        SwingWorker<NoShowModelTrainer.TrainingPipelineResult, Void> worker = new SwingWorker<>() {
            @Override
            protected NoShowModelTrainer.TrainingPipelineResult doInBackground() {
                NoShowModelTrainer trainer = new NoShowModelTrainer();
                return trainer.runPipeline();
            }

            @Override
            protected void done() {
                try {
                    NoShowModelTrainer.TrainingPipelineResult res = get();
                    removeAll();
                    initUI();
                    revalidate();
                    repaint();
                    if (onModelTrained != null) {
                        onModelTrained.run();
                    }
                    JOptionPane.showMessageDialog(
                            ModelInsightsPanel.this,
                            String.format("Model trained and saved successfully!\nAccuracy: %.2f%% | Recall: %.4f | ROC-AUC: %.4f",
                                    res.getBalancedRfResult().getAccuracy() * 100.0,
                                    res.getBalancedRfResult().getRecall(),
                                    res.getBalancedRfResult().getRocArea()),
                            "Training Complete",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                } catch (Exception ex) {
                    btnTrainModel.setEnabled(true);
                    btnTrainModel.setText(insightsService.isModelFilePresent() ? "Retrain ML Model" : "Train ML Model");
                    JOptionPane.showMessageDialog(
                            ModelInsightsPanel.this,
                            "Model training failed: " + ex.getMessage(),
                            "Training Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        };
        worker.execute();
    }
}
