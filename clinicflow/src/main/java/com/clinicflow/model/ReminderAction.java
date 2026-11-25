package com.clinicflow.model;

/**
 * Suggested clinic action based on predicted no-show risk.
 * Never cancels appointments; only optimizes reminders and staff follow-up.
 */
public enum ReminderAction {
    STANDARD("Standard automated SMS 24 hours prior"),
    ENHANCED_SMS("Enhanced SMS with direct confirmation link 48h and 24h prior"),
    PHONE_CALL("Manual phone call by clinic front desk and priority confirmation");

    private final String actionDescription;

    ReminderAction(String actionDescription) {
        this.actionDescription = actionDescription;
    }

    public String getActionDescription() {
        return actionDescription;
    }

    public static ReminderAction forRisk(RiskCategory riskCategory) {
        if (riskCategory == null) {
            return STANDARD;
        }
        switch (riskCategory) {
            case HIGH:
                return PHONE_CALL;
            case MEDIUM:
                return ENHANCED_SMS;
            case LOW:
            default:
                return STANDARD;
        }
    }
}
