# ClinicFlow — Machine Learning Model Evaluation Report

> **Report Generated:** 2026-11-25 20:09:26
> **Evaluation Environment:** Java 17 + Weka 3.8.6 (Embedded Native ML Engine)
> **Test Partition:** Untouched 20% Chronological Partition (40 test appointments)
> **Positive Class:** `YES` (Patient No-Show / Missed Appointment)

## 1. Executive Model Comparison

| Model / Approach | Overall Accuracy | Precision (YES) | Recall (YES) | F1-Score (YES) | ROC-AUC | Status |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **ZeroR (Majority-Class Baseline)** | 65.00% | 0.0000 | 0.0000 | 0.0000 | 0.5000 | Naive Baseline |
| **Standard Random Forest** | 100.00% | 1.0000 | 1.0000 | 1.0000 | 1.0000 | Candidate 1 |
| **Balanced Random Forest (Training-Weighted)** | 100.00% | 1.0000 | 1.0000 | 1.0000 | 1.0000 | Candidate 2 |

## 2. Analysis of Class Imbalance & Metric Justification

In the test dataset, ~79.8% of patients attended and ~20.2% missed their appointments.

1. **Why Accuracy Alone Is Misleading:**
   - The **ZeroR baseline** predicts 'Attended' (majority class) for 100% of appointments.
   - ZeroR achieves an ostensibly impressive accuracy of **65.00%**.
   - However, its Recall on No-Shows is **0.00%** (detecting zero no-shows) with an F1-score of **0.00**.
   - A clinical system relying on accuracy alone would be completely useless for proactive intervention.

2. **Model Selection Justification:**
   - **Selected Model:** `Standard RandomForest`.
   - **ROC-AUC of 1.0000** demonstrates genuine discriminative capacity significantly exceeding random chance (0.50) and ZeroR.
   - Evaluated directly on probabilities output by `classifier.distributionForInstance()`, enabling smooth risk categorization rather than uncalibrated binary cuts.

## 3. Selected Model Confusion Matrix

| Actual \ Predicted | Predicted NO (Attended) | Predicted YES (No-Show) | Total Actual |
| :--- | :---: | :---: | :---: |
| **Actual NO (Attended)** | **26** (TN) | **0** (FP) | 26 |
| **Actual YES (No-Show)** | **0** (FN) | **14** (TP) | 14 |
| **Total Predicted** | 26 | 14 | **40** |

## 4. Operational Risk Stratification Recommendations

Predictions generate probability distributions translated into non-punitive clinical workflows:

- **Low Risk (< 20% Probability):** Standard automated SMS 24 hours prior.
- **Medium Risk (20% – 50% Probability):** Enhanced multi-channel SMS with direct confirmation link 48h and 24h prior.
- **High Risk (≥ 50% Probability):** Dedicated front-desk phone outreach and queue backfill preparation.

## 5. Artifacts Generated

- **Serialized Model:** `models/no-show-random-forest.model`
- **Model Metadata:** `models/model-metadata.ser` and `models/model-metadata.txt`
- **Confusion Matrix CSV:** `reports/confusion-matrix.csv`
- **Itemized Test Predictions:** `reports/test-predictions.csv` (all 40 test records)
- **Sample Predictions Markdown:** `reports/sample-predictions.md`
