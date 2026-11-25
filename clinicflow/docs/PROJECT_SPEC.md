# ClinicFlow — Project Specification

## 1. Problem Statement

Outpatient medical clinics frequently experience substantial rates of patient missed appointments ("no-shows"). When patients fail to appear for booked consultations without prior cancellation:

1. **Clinic Resource Underutilization:** Highly trained physicians and specialized examination rooms sit idle during scheduled appointment slots.
2. **Prolonged Wait Times:** Other patients requiring urgent or routine consultations face weeks or months of unnecessary queue delays.
3. **Operational Financial Loss:** Healthcare facilities experience reduced clinical throughput and administrative disruption.

---

## 2. Real-World Need

While reminder systems exist, generic reminders dispatched to all patients are costly, inefficient, and often ignored. Conversely, excessive double-booking creates severe clinic congestion when all patients show up.

A clinical decision support system that accurately predicts the **likelihood of an appointment no-show at booking time** enables clinic administrators to:
- Deploy targeted, differentiated confirmation protocols (e.g., automated SMS for low risk vs. direct phone outreach for high risk).
- Optimize clinic queue scheduling without punitive actions (such as refusing service or cancelling appointments).

---

## 3. Project Objectives

1. **Real-World Problem Solving:** Deliver a functional clinic appointment management system addressing outpatient attendance rates.
2. **Machine Learning Integration:** Train a supervised Random Forest classification model in Java using Weka on 110,527 historical records to generate well-calibrated no-show probabilities.
3. **Clinical Ethics & Patient Safety:** Ensure that predictions strictly inform reminder intensity and staff workflows, never automated denial of care.
4. **Software Engineering Rigour:** Implement clean architecture in Java 17 demonstrating Object-Oriented Programming (encapsulation, inheritance, polymorphism, generic repositories, custom exceptions, and file handling).
5. **Academic Compliance:** Meet all requirements of university-level software and ML project assignments.

---

## 4. System Scope

* **Target Users:** Clinic receptionists, practice managers, triage staff.
* **Operational Scope:** Outpatient clinics, scheduled medical consultations.
* **Exclusions:** Emergency room arrivals (unscheduled walk-ins) and inpatient admissions.

---

## 5. Input Data, Processing Logic & Expected Output

### Input Data (Available at Booking Time)
* Patient demographics: `Age`, `Gender`, `Scholarship` (Bolsa Família social welfare enrollment).
* Chronic health indicators: `Hipertension`, `Diabetes`, `Alcoholism`, `Handcap`.
* Scheduling parameters: `ScheduledDay` (booking timestamp), `AppointmentDay` (target consultation date), `Neighbourhood` (clinic location).

### Processing Logic
1. **Validation:** Ensure valid age range [0, 120], appointment date not preceding scheduling date, valid clinical codes.
2. **Feature Extraction:** Compute `leadDays` (waiting duration), `bookingDayOfWeek`, and `appointmentDayOfWeek`.
3. **ML Inference:** Evaluate feature vector using the Weka Random Forest model.
4. **Risk Stratification:**
   - **Low Risk:** Probability < 0.20
   - **Medium Risk:** 0.20 ≤ Probability < 0.50
   - **High Risk:** Probability ≥ 0.50
5. **Action Recommendation:**
   - **Low Risk:** Standard automated SMS 24 hours prior.
   - **Medium Risk:** Multi-channel SMS with direct confirmation link 48h and 24h prior.
   - **High Risk:** Dedicated phone call by clinic front desk and waitlist backfill preparation.

### Expected Output
* Predicted binary class (`Yes` = No-Show, `No` = Attended).
* Probability distribution score (e.g., `72.4%`).
* Stratified risk category (`HIGH`).
* Concrete operational reminder recommendation.
