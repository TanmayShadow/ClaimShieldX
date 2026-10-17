package com.example.ClaimShieldX.claims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

//        Policy	policy_token, policy_type, policy_start_date, policy_end_date

@Embeddable
@Getter
@Setter
public class Policy {
    @Column(name="policy_token") //instead of exposing internal policy id companies can pass another id which us 1-1 mapped by the company itself
    private String policyToken;

    @Column(name="policy_type")
    private String policyType;

    @Column(name="policy_start_date")
    private Instant policyStartDate;

    @Column(name="policy_end_date")
    private Instant policyEndDate;

}
