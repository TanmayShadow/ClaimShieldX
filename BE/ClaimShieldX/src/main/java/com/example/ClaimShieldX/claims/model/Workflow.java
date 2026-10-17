package com.example.ClaimShieldX.claims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

//        Workflow	status, created_by, created_at, updated_at
@Embeddable
@Getter
@Setter
public class Workflow {
    @Column(name="status")
    private String status;

    @Column(name="workflow_created_by")
    private String createdBy;

    private Instant createdAt;

    private Instant updatedAt;
}
