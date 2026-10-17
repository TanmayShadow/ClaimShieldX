package com.example.ClaimShieldX.readiness.model;

import com.example.ClaimShieldX.claims.model.ClaimStage;
import com.example.ClaimShieldX.documents.model.DocumentType;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.util.UUID;

/** One row of the checklist configuration. Read-only: rows are changed by migration, not by the application. */
@Entity
@Immutable
@Table(name = "document_requirements")
@Getter
public class DocumentRequirement {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false)
    private ClaimStage stage;

    /** "ANY", or the name of an {@code IncidentType}. */
    @Column(name = "incident_type", nullable = false)
    private String incidentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private DocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "requirement", nullable = false)
    private RequirementLevel requirement;

    @Column(name = "gating", nullable = false)
    private boolean gating;
}
