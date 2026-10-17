package com.example.ClaimShieldX.claims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

//        Incident	incident_at, reported_at, incident type, permitted location precision
@Embeddable
@Getter
@Setter
public class Incident {
    @Column(name="incident_at")
    private Instant incidentAt;

    @Column(name="reported_at")
    private Instant reportedAt;

    @Enumerated(EnumType.STRING)
    @Column(name="incident_type")
    private IncidentType incidentType;

    @Column(name="permitted_location_precision")
    private String permittedLocationPrecision;
}
