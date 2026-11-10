package com.jansunwai.service;

import com.jansunwai.repository.ComplaintSqlRepository;
import org.springframework.stereotype.Component;

import java.time.Year;

/**
 * JS-2026-001234. The number comes from a PostgreSQL SEQUENCE: nextval() is atomic, so two citizens submitting at
 * the same millisecond always get different IDs (no "SELECT max()+1" race). The UNIQUE constraint is the backup.
 */
@Component
public class TrackingIdGenerator {
    private final ComplaintSqlRepository sql;

    public TrackingIdGenerator(ComplaintSqlRepository sql) {
        this.sql = sql;
    }

    public String next() {
        return format(Year.now().getValue(), sql.nextTrackingNumber());
    }

    static String format(int year, long number) {
        return String.format("JS-%d-%06d", year, number);
    }
}
