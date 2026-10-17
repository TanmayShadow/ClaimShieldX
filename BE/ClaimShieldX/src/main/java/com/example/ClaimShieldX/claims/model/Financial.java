package com.example.ClaimShieldX.claims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

//        Financial	initial_estimate_amount, approved_estimate_amount, currency, deductible where relevant
@Embeddable
@Getter
@Setter
public class Financial {
    @Column(name="initial_estimate_amount")
    private Long initialEstimateAmount;

    @Column(name="approved_estimate_amount")
    private Long approvedEstimateAmount;

    @Column(name="currency")
    private String currency;

    @Column(name="deductible")
    private Long deductible;
}
