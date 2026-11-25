# ClinicFlow — Machine Learning Model Evaluation & Audit

## 1. Measured Evaluation Results on Untouched Test Set

All metrics were computed by [ModelEvaluator.java](file:///D:/JAVA_ML/clinicflow/src/main/java/com/clinicflow/ml/ModelEvaluator.java) on the held-out test partition consisting of **22,104 genuine historical appointments**.

| Model Candidate | Accuracy | Precision (YES) | Recall (YES) | F1-Score (YES) | ROC-AUC | Outcome |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **ZeroR (Majority-Class Baseline)** | **88.09%** | 0.0000 | 0.0000 | 0.0000 | 0.5000 | Baseline |
| **Standard RandomForest** | 84.34% | **0.2432** | 0.1489 | 0.1847 | **0.6660** | Candidate 1 |
| **Balanced RandomForest (Selected)** | 77.50% | 0.2128 | **0.3293** | **0.2585** | 0.6658 | **Selected Optimal Model** |

---

## 2. Confusion Matrix & Independent Verification

From [confusion-matrix.csv](file:///D:/JAVA_ML/clinicflow/reports/confusion-matrix.csv) and independent recomputation from [test-predictions.csv](file:///D:/JAVA_ML/clinicflow/reports/test-predictions.csv):

| Actual \ Predicted | Predicted NO (Attended) | Predicted YES (No-Show) | Total Actual |
| :--- | :---: | :---: | :---: |
| **Actual NO (Attended)** | **16,264** (True Negatives) | **3,207** (False Positives) | 19,471 |
| **Actual YES (No-Show)** | **1,766** (False Negatives) | **867** (True Positives) | 2,633 |
| **Total Predicted** | 18,030 | 4,074 | **22,104** |

---

## 3. Detailed Metric Interpretation & Trade-Off Analysis

### The Accuracy Paradox in Imbalanced Healthcare Datasets
* In the chronological test partition, 19,471 patients attended and 2,633 missed their consultations.
* A naive **ZeroR classifier** predicting "Attended" for every appointment achieves **88.09% accuracy**.
* However, ZeroR identifies **0 no-shows** (Recall = 0.00%, F1 = 0.00). In a clinical environment, relying on accuracy would mean missing every single at-risk appointment.

### Clinical Utility of Balanced Random Forest
* **Balanced Random Forest** uses training-set class weighting (`ClassBalancer`). Although overall accuracy drops to 77.50%, its **Recall on no-shows more than doubles to 32.93%** (867 detected no-shows vs 392 in standard RF), and its **F1-Score increases from 0.1847 to 0.2585**.
* For clinic queue management, higher recall is far more valuable because false positives merely result in an extra reminder SMS or courtesy call (no service is denied), whereas false negatives result in unconfirmed no-shows and empty consultation slots.

---

## 4. Chronological Train/Test Partition & Distribution Shift Analysis

### Verification of Chronological Partition
* **Training Partition (80%):** 88,417 records (`ScheduledDay` from 2015-11-10 to 2016-05-25 08:47:31Z).
* **Testing Partition (20%):** 22,104 records (`ScheduledDay` from 2016-05-25 08:47:32Z to 2016-06-08 20:07:23Z).
* **Verification:** Zero temporal overlap between training and testing sets.

### Explanation of Test No-Show Rate Shift (11.91% vs 22.26% Train)
The lower test no-show rate is **not a splitting or preprocessing defect**, but a mathematical artifact of dataset collection right-censoring:
1. Historical appointments in the raw dataset cease on **June 8, 2016**.
2. Any patient scheduling after May 25, 2016 who booked an appointment with a lead time greater than 14 days was excluded from the dataset because their appointment would occur after June 8.
3. As a result, average lead time drops from **12.32 days** in train to **1.62 days** in test.
4. Same-day bookings (`leadTimeDays == 0`) increase from **28.83%** in train to **59.13%** in test.
5. Because same-day bookings have a very low historical no-show rate (~4.1%–4.9%), this large concentration of same-day bookings pulls the test partition's aggregate no-show rate down to 11.91%.
6. Counterfactual validation shows that splitting by `AppointmentDay` yields consistent no-show rates (20.62% in train vs. 18.45% in test).

---

## 5. Serialized Model Profiling & Resource Footprint

* **Model File Size:** 245,249,485 bytes (~233.9 MB).
* **Ensemble Structure:** 100 deep, unconstrained decision trees (`maxDepth = 0`) with continuous thresholds.
* **Loading Time:** ~4,249 ms (4.25 seconds) average across cold starts.
* **Heap Memory:** ~300 MB resident memory after garbage collection.
* **Inference Latency:** < 0.5 ms per single-appointment prediction.
* **Resource Assessment:** Model loading is a one-time startup operation. Sub-millisecond inference and 300 MB memory are well within modern outpatient clinic hardware capabilities (8–32 GB RAM). Preserving full tree depth maintains ensemble diversity and recall.

---

## 6. Probabilistic Prediction Calibration Note

Model probabilities output by `classifier.distributionForInstance()` represent the proportion of decision trees in the ensemble voting for each class. 

> [!NOTE]
> These probabilities represent **model-estimated posterior scores** rather than statistically calibrated probabilities (such as those produced by Platt Scaling or Isotonic Regression). While effective for ordinal ranking and stratified risk categorization (Low: <20%, Medium: 20–50%, High: ≥50%), they represent model estimates rather than absolute actuarial probabilities.

---

## 7. Operational Risk Stratification Matrix

| Probability Range ($P_{\text{No-Show}}$) | Risk Tier | Clinical Action | Operational Procedure |
| :---: | :---: | :---: | :--- |
| **$P < 0.20$** | **`LOW`** | `STANDARD` | Automated standard SMS confirmation 24 hours prior. |
| **$0.20 \le P < 0.50$** | **`MEDIUM`** | `ENHANCED_SMS` | Two-stage SMS with interactive response link 48h and 24h prior. |
| **$P \ge 0.50$** | **`HIGH`** | `PHONE_CALL` | Manual front-desk telephone outreach; standby patient queue alerting. |
