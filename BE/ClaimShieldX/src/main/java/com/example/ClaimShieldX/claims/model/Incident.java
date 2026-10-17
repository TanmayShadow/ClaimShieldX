package com.example.ClaimShieldX.claims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
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

    @Column(name="permitted_location_precision")
    private String permittedLocationPrecision;
}
