package com.clinicflow.ml;

/**
 * Value object recording an excluded anomaly record during dataset cleaning.
 */
public class CleaningRecord {

    private final long appointmentId;
    private final long patientId;
    private final String reason;
    private final String scheduledDay;
    private final String appointmentDay;
    private final int age;
    private final long leadDays;
    private final String noShow;

    public CleaningRecord(long appointmentId, long patientId, String reason,
                          String scheduledDay, String appointmentDay,
                          int age, long leadDays, String noShow) {
        this.appointmentId = appointmentId;
        this.patientId = patientId;
        this.reason = reason;
        this.scheduledDay = scheduledDay;
        this.appointmentDay = appointmentDay;
        this.age = age;
        this.leadDays = leadDays;
        this.noShow = noShow;
    }

    public long getAppointmentId() { return appointmentId; }
    public long getPatientId() { return patientId; }
    public String getReason() { return reason; }
    public String getScheduledDay() { return scheduledDay; }
    public String getAppointmentDay() { return appointmentDay; }
    public int getAge() { return age; }
    public long getLeadDays() { return leadDays; }
    public String getNoShow() { return noShow; }
}
