package com.clinicflow.model;

/**
 * Gender enumeration matching the dataset categories ('F' and 'M').
 */
public enum Gender {
    F("Female"),
    M("Male");

    private final String displayName;

    Gender(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Gender fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Gender code cannot be null");
        }
        String clean = code.trim().toUpperCase();
        if ("F".equals(clean) || "FEMALE".equals(clean)) {
            return F;
        } else if ("M".equals(clean) || "MALE".equals(clean)) {
            return M;
        }
        throw new IllegalArgumentException("Unknown gender code: " + code);
    }
}
