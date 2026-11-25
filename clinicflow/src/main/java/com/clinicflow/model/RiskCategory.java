package com.clinicflow.model;

/**
 * Risk category derived from predicted no-show probability.
 */
public enum RiskCategory {
    LOW("Low No-Show Risk", 0.0, 0.20),
    MEDIUM("Moderate No-Show Risk", 0.20, 0.50),
    HIGH("High No-Show Risk", 0.50, 1.0);

    private final String label;
    private final double minProbability;
    private final double maxProbability;

    RiskCategory(String label, double minProbability, double maxProbability) {
        this.label = label;
        this.minProbability = minProbability;
        this.maxProbability = maxProbability;
    }

    public String getLabel() {
        return label;
    }

    public double getMinProbability() {
        return minProbability;
    }

    public double getMaxProbability() {
        return maxProbability;
    }

    public static RiskCategory fromProbability(double probability) {
        if (probability < 0.20) {
            return LOW;
        } else if (probability < 0.50) {
            return MEDIUM;
        } else {
            return HIGH;
        }
    }
}
