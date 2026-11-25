# ClinicFlow — Training & Testing Methodology

## 1. Dataset Overview

* **Source File:** `data/raw/appointments.csv` (110,527 historical records, preserved unmodified)
* **Cleaned Output:** `data/processed/clean_appointments.csv` (110,521 valid records)
* **Filtered Entries:** Exactly 6 anomaly records (1 record with Age = -1; 5 records where appointment date preceded scheduling date). Zero synthetic data was created.

---

## 2. Chronological 80:20 Split (Simulated Future Prediction)

Traditional machine learning workflows often employ randomized splits (e.g. random k-fold cross-validation or random 80:20 partitioning). However, for healthcare appointments, **random splitting creates temporal leakage**: the model learns from appointments in June to predict appointments in April.

ClinicFlow strictly implements a **chronological split**:
1. All 110,521 cleaned records are sorted in ascending order by their booking timestamp (`ScheduledDay`).
2. The earliest 80% (88,417 records) are allocated exclusively to the **Training Partition**.
3. The remaining 20% (22,104 records) are allocated to the **Testing Partition**.
4. The testing partition remains completely untouched during model training, hyperparameter selection, and preprocessing calibration.

| Partition | Record Count | Percentage | Date Range (ScheduledDay) |
| :--- | :---: | :---: | :--- |
| **Training Set** | 88,417 | 80.00% | 2015-11-10T07:13:56Z — 2016-05-25T08:47:32Z |
| **Testing Set** | 22,104 | 20.00% | 2016-05-25T08:47:32Z — 2016-06-08T20:07:23Z |
| **Total Cleaned** | 110,521 | 100.00% | Full historical period |

---

## 3. Algorithm & Hyperparameters

* **Algorithm:** Random Forest (`weka.classifiers.trees.RandomForest`)
* **Number of Trees (`numIterations`):** 100
* **Maximum Tree Depth (`maxDepth`):** 0 (unlimited depth, fully grown randomized decision trees)
* **Random Seed (`seed`):** 42 (ensures deterministic reproducibility across executions)
* **Features per Split (`numFeatures`):** $\lfloor \log_2(K) + 1 \rfloor = 3$ features randomly considered at each node split
* **Class Imbalance Strategy:** `weka.filters.supervised.instance.ClassBalancer` applied strictly to the training partition inside a `FilteredClassifier`, weighting instances so each class carries equal aggregate weight during tree construction without modifying test instances.
