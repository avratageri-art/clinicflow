package com.clinicflow.ml;

import com.clinicflow.model.Appointment;
import org.apache.commons.csv.CSVRecord;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Centralized feature extraction engine for ClinicFlow.
 * Enforces an identical feature schema during both model training and live appointment prediction.
 * Strictly guarantees that no identifier attributes or future leakage enter the feature vector.
 */
public class FeatureExtractor {

    public static final String RELATION_NAME = "ClinicFlow_AppointmentNoShow";

    // Feature Name Constants
    public static final String ATTR_LEAD_TIME_DAYS = "leadTimeDays";
    public static final String ATTR_APPOINTMENT_WEEKDAY = "appointmentWeekday";
    public static final String ATTR_BOOKING_WEEKDAY = "bookingWeekday";
    public static final String ATTR_BOOKING_HOUR = "bookingHour";
    public static final String ATTR_SAME_DAY_BOOKING = "sameDayBooking";
    public static final String ATTR_AGE = "age";
    public static final String ATTR_NO_SHOW = "noShow";

    // Target Class Constants
    public static final String CLASS_NO = "NO";   // Index 0: Attended appointment
    public static final String CLASS_YES = "YES"; // Index 1: Missed appointment (Positive class)

    public static final List<String> WEEKDAY_VALUES = Arrays.asList(
            "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"
    );

    public static final List<String> BINARY_NOMINAL_VALUES = Arrays.asList("NO", "YES");

    private final ArrayList<Attribute> attributes;
    private final Instances datasetHeader;

    public FeatureExtractor() {
        this.attributes = new ArrayList<>();

        // 1. leadTimeDays (Numeric)
        attributes.add(new Attribute(ATTR_LEAD_TIME_DAYS));

        // 2. appointmentWeekday (Nominal)
        attributes.add(new Attribute(ATTR_APPOINTMENT_WEEKDAY, new ArrayList<>(WEEKDAY_VALUES)));

        // 3. bookingWeekday (Nominal)
        attributes.add(new Attribute(ATTR_BOOKING_WEEKDAY, new ArrayList<>(WEEKDAY_VALUES)));

        // 4. bookingHour (Numeric)
        attributes.add(new Attribute(ATTR_BOOKING_HOUR));

        // 5. sameDayBooking (Nominal: NO, YES)
        attributes.add(new Attribute(ATTR_SAME_DAY_BOOKING, new ArrayList<>(BINARY_NOMINAL_VALUES)));

        // 6. age (Numeric)
        attributes.add(new Attribute(ATTR_AGE));

        // 7. Target: noShow (Nominal: NO, YES) - Always last
        attributes.add(new Attribute(ATTR_NO_SHOW, new ArrayList<>(BINARY_NOMINAL_VALUES)));

        this.datasetHeader = new Instances(RELATION_NAME, attributes, 0);
        this.datasetHeader.setClassIndex(attributes.size() - 1);
    }

    public Instances getDatasetHeader() {
        return new Instances(datasetHeader, 0);
    }

    public List<String> getFeatureNames() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < attributes.size() - 1; i++) {
            names.add(attributes.get(i).name());
        }
        return Collections.unmodifiableList(names);
    }

    public String getTargetAttributeName() {
        return ATTR_NO_SHOW;
    }

    public List<String> getClassValues() {
        return Collections.unmodifiableList(BINARY_NOMINAL_VALUES);
    }

    /**
     * Extracts an unlabelled Weka Instance for live prediction on an appointment.
     * The class attribute is marked missing ('?').
     */
    public Instance extractInstance(Appointment appointment) {
        if (appointment == null) {
            throw new IllegalArgumentException("Appointment cannot be null");
        }

        long leadDays = appointment.getLeadDays();
        String apptWeekday = appointment.getAppointmentDayOfWeek();
        String schedWeekday = appointment.getScheduledDayOfWeek();
        int bookingHour = appointment.getScheduledDay().getHour();
        String sameDay = (leadDays == 0) ? "YES" : "NO";
        int age = appointment.getPatient().getAge();

        Instance instance = new DenseInstance(attributes.size());
        instance.setDataset(datasetHeader);

        instance.setValue(datasetHeader.attribute(ATTR_LEAD_TIME_DAYS), leadDays);
        instance.setValue(datasetHeader.attribute(ATTR_APPOINTMENT_WEEKDAY), apptWeekday);
        instance.setValue(datasetHeader.attribute(ATTR_BOOKING_WEEKDAY), schedWeekday);
        instance.setValue(datasetHeader.attribute(ATTR_BOOKING_HOUR), bookingHour);
        instance.setValue(datasetHeader.attribute(ATTR_SAME_DAY_BOOKING), sameDay);
        instance.setValue(datasetHeader.attribute(ATTR_AGE), age);
        instance.setMissing(datasetHeader.classIndex());

        return instance;
    }

    /**
     * Extracts a labelled Weka Instance from historical CSV records.
     */
    public Instance extractInstance(CSVRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("CSVRecord cannot be null");
        }

        LocalDateTime sched = LocalDateTime.parse(record.get("ScheduledDay"), DateTimeFormatter.ISO_DATE_TIME);
        LocalDate appt = LocalDate.parse(record.get("AppointmentDay").substring(0, 10));

        long leadDays = ChronoUnit.DAYS.between(sched.toLocalDate(), appt);
        String apptWeekday = appt.getDayOfWeek().name();
        String schedWeekday = sched.getDayOfWeek().name();
        int bookingHour = sched.getHour();
        String sameDay = (leadDays == 0) ? "YES" : "NO";
        int age = Integer.parseInt(record.get("Age"));

        String rawNoShow = record.get("No-show").trim();
        String classVal = "Yes".equalsIgnoreCase(rawNoShow) ? CLASS_YES : CLASS_NO;

        Instance instance = new DenseInstance(attributes.size());
        instance.setDataset(datasetHeader);

        instance.setValue(datasetHeader.attribute(ATTR_LEAD_TIME_DAYS), leadDays);
        instance.setValue(datasetHeader.attribute(ATTR_APPOINTMENT_WEEKDAY), apptWeekday);
        instance.setValue(datasetHeader.attribute(ATTR_BOOKING_WEEKDAY), schedWeekday);
        instance.setValue(datasetHeader.attribute(ATTR_BOOKING_HOUR), bookingHour);
        instance.setValue(datasetHeader.attribute(ATTR_SAME_DAY_BOOKING), sameDay);
        instance.setValue(datasetHeader.attribute(ATTR_AGE), age);
        instance.setClassValue(classVal);

        return instance;
    }
}
