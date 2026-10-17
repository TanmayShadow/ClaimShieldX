package com.example.ClaimShieldX.claims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

// Garage	garage_id or a pseudonymous garage reference
@Embeddable
@Getter
@Setter
public class Garage {
    @Column(name="garage_id")
    private String garageId;
}
