# ClinicFlow — Student-Friendly Machine Learning Guide

This guide explains the foundational concepts of Machine Learning, Weka, and the Random Forest algorithm in clear, academic language for coursework presentations.

---

## 1. Machine Learning Fundamentals

### Supervised Learning
In **Supervised Learning**, a computer algorithm learns patterns from historical examples that already have known answers (labels). By examining thousands of past appointments where we know whether the patient attended or not, the algorithm learns mathematical relationships between booking circumstances and attendance outcomes.

### Feature vs. Target Variable
* **Feature (Input Attribute):** A measurable characteristic known before an outcome occurs. Examples in ClinicFlow:
  * `leadTimeDays`: How many days in advance was the appointment booked?
  * `bookingHour`: What time of day was the call placed?
  * `age`: How old is the patient?
* **Target Variable (Label):** The outcome we want to predict. In ClinicFlow, the target is `noShow` (`NO` if the patient attended, `YES` if they failed to show up).

### Classification
When the target variable consists of distinct categories (like `YES` and `NO`) rather than continuous numbers (like blood pressure or income), the task is called **Classification**. Because there are exactly two outcomes, it is a **Binary Classification** problem.

---

## 2. How the Random Forest Algorithm Works

A **Random Forest** is an ensemble (collection) of individual **Decision Trees**:

```
                       [New Appointment Vector]
                                  |
           +----------------------+----------------------+
           |                      |                      |
           v                      v                      v
    +--------------+       +--------------+       +--------------+
    |    Tree 1    |       |    Tree 2    |       |   Tree 100   |
    | (Vote: 70% Y)|       | (Vote: 80% Y)|       | (Vote: 60% Y)|
    +--------------+       +--------------+       +--------------+
           |                      |                      |
           +----------------------+----------------------+
                                  |
                                  v
                        [Ensemble Average]
                   No-Show Probability = 70.0%
                         Risk Tier: HIGH
                   Action: Priority Phone Call
```

1. **Bootstrap Aggregation (Bagging):** Each tree is trained on a random sample of the training dataset drawn with replacement.
2. **Random Feature Subsets:** At each decision node, only a random subset of features ($\approx \log_2(6) + 1 = 3$) is evaluated to find the best split. This decorrelates the trees so they do not all make the same errors.
3. **Probabilistic Voting:** Rather than a simple binary vote, the ensemble calculates the average vote across all 100 trees to estimate class probabilities (e.g., 28.5% No-Show, 71.5% Attended).

---

## 3. Training versus Testing & Data Leakage

* **Training Set (80%):** The study material given to the model to learn patterns.
* **Testing Set (20%):** The final examination. It must contain only records the model has never seen before.
* **Data Leakage:** Occurs when information that would not be available in real life accidentally leaks into training. For example, if we included `SMS_received` (which is sent 24 hours before the appointment) in a model that runs at booking time (weeks earlier), the model would have access to future data it cannot possess in reality.

---

## 4. Evaluation Metrics Explained

* **Accuracy:** Percentage of total correct predictions. When classes are imbalanced (80% attend, 20% miss), accuracy is misleading: a model predicting "attend" every time gets 80% accuracy without helping any clinic!
* **Precision:** When the model predicts `YES` (no-show), how often is it actually right? $\frac{TP}{TP + FP}$
* **Recall (Sensitivity):** Out of all patients who genuinely missed their appointments, what percentage did the model catch? $\frac{TP}{TP + FN}$
* **F1-Score:** The harmonic mean of precision and recall: $2 \cdot \frac{\text{Precision} \cdot \text{Recall}}{\text{Precision} + \text{Recall}}$.
* **ROC-AUC:** Area under the ROC curve. Measures the model's ability to rank high-risk patients higher than low-risk patients across all thresholds. A score of 0.5 is no better than flipping a coin; 1.0 is a perfect score.

---

## 5. Geographic & Demographic Generalization Warning

The genuine dataset used in ClinicFlow was collected in public health clinics across **Vitória, Espírito Santo, Brazil in 2016**.
* Healthcare access, appointment booking customs, public transportation availability, mobile phone penetration, and seasonal monsoon impacts in **Indian clinics** differ fundamentally from Brazil.
* Therefore, while the architecture, Weka pipeline, and algorithms implemented in ClinicFlow are globally valid, **local validation on Indian clinic datasets** would be strictly required before production clinical deployment in India.
