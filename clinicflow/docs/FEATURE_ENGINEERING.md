# ClinicFlow — Feature Engineering & Leakage Prevention

## 1. Feature Engineering Principles

In outpatient clinics, an attendance prediction model is useful only if it can be evaluated **at the moment the patient books an appointment**. Any feature not known when the receptionist records the booking constitutes forward-looking data leakage and renders the system clinically invalid.

---

## 2. Selected Feature Schema

The ClinicFlow model uses **6 input features** and **1 class attribute**:

| Feature Index | Attribute Name | Weka Type | Source Field(s) | Description & Operational Justification |
| :---: | :--- | :--- | :--- | :--- |
| **0** | `leadTimeDays` | Numeric | `ScheduledDay`, `AppointmentDay` | Difference in calendar days between booking date and appointment date (`AppointmentDay - ScheduledDay`). Empirically, wait duration is the single strongest predictor of attendance. |
| **1** | `appointmentWeekday` | Nominal | `AppointmentDay` | Day of week of the consultation (`MONDAY` through `SUNDAY`). Captures day-specific attendance dynamics (e.g. Friday and Monday variations). |
| **2** | `bookingWeekday` | Nominal | `ScheduledDay` | Day of week booking was initiated (`MONDAY` through `SUNDAY`). Captures scheduling context. |
| **3** | `bookingHour` | Numeric | `ScheduledDay` | Hour of day (0 to 23) when booking was registered. Distinguishes early morning triage calls from late afternoon bookings. |
| **4** | `sameDayBooking` | Nominal | `leadTimeDays` | Binary nominal flag (`YES`, `NO`). Same-day consultations (`leadTimeDays == 0`) exhibit an extremely low no-show rate (< 5%). |
| **5** | `age` | Numeric | `Age` | Patient age in years. Captures patient life-stage and vulnerability (e.g. young adult mobility vs elderly reliance on caregivers). |
| **6** | `noShow` | Nominal | `No-show` | **Class Attribute (Supervised Target)**: `NO` = Attended, `YES` = Missed Appointment (Positive Class). |

---

## 3. Strict Exclusion & Data Leakage Audit

| Excluded Field | Reason for Strict Exclusion |
| :--- | :--- |
| `No-show` | Target variable; entering as an input feature would be circular target leakage. |
| `PatientId` | High-cardinality unique patient identifier. In tabular models, including raw IDs causes tree splits to memorize specific individuals rather than learning clinical attendance patterns. |
| `AppointmentID` | Arbitrary database sequence ID with zero predictive causality. |
| `SMS_received` | **Forward-Looking Leakage:** In clinic operations, reminder SMS messages are dispatched 24–48 hours prior to the consultation date—which occurs days or weeks after initial booking. Using `SMS_received` at booking time assumes information from the future that does not yet exist. |
| Post-consultation records | Any outcome notes or billing records generated after the appointment hour. |

---

## 4. Fairness, Privacy & Demographic Bias Discussion

### Avoidance of Neighborhood and Socioeconomic Predictors
While the raw dataset contains `Neighbourhood` (81 districts) and `Scholarship` (*Bolsa Família* social assistance), they were intentionally excluded from the default model:
1. **Redlining and Socioeconomic Bias:** Predicting higher no-show risk based on low-income postal districts or welfare enrollment risks systemic bias in healthcare delivery.
2. **Actionable Clinical Support:** The ClinicFlow model uses predictions exclusively to trigger **supportive reminder interventions** (automated SMS vs phone calls), never to deny slots, deprioritize patients, or cancel consultations.
3. **Age as an Input Feature:** Patient age is retained as a clinical factor reflecting physical mobility and independence. It is bounded strictly within [0, 115] years.
