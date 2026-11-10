package com.jansunwai.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** The boundary polygon (PostGIS) is read and written with SQL in GeoRepository, so it is not mapped here. */
@Entity
@Table(name = "wards")
public class Ward {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Ward() { }

    public Ward(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public void setIdForTest(Long id) { this.id = id; }
}
