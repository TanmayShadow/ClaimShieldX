package com.example.ClaimShieldX.claims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

//        Claimant	claimant_token, only when necessary and authorized
@Embeddable
@Getter
@Setter
public class Claimant {
    @Column(name="claimant_token")
    private String claimantToken;
}
