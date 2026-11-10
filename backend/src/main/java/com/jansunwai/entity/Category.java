package com.jansunwai.entity;

/** Complaint categories, each with the department that normally handles it. */
public enum Category {
    ROAD_DAMAGE("PUBLIC_WORKS", "Road damage"),
    WATER_SUPPLY("WATER_SUPPLY", "Water supply"),
    DRAINAGE("DRAINAGE", "Drainage & sewage"),
    GARBAGE("SANITATION", "Garbage"),
    STREETLIGHT("ELECTRICAL", "Streetlight"),
    SANITATION("SANITATION", "Public sanitation"),
    OTHER("GENERAL", "Other");

    private final String defaultDepartmentCode;
    private final String label;

    Category(String defaultDepartmentCode, String label) {
        this.defaultDepartmentCode = defaultDepartmentCode;
        this.label = label;
    }

    public String defaultDepartmentCode() { return defaultDepartmentCode; }
    public String label() { return label; }
}
