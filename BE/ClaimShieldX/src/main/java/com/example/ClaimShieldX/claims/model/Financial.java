package com.example.ClaimShieldX.claims.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

//        Financial	initial_estimate_amount, approved_estimate_amount, currency, deductible where relevant
@Embeddable
@Getter
@Setter
public class Financial {
    @Column(name="initial_estimate_amount", precision = 15, scale = 2)
    private BigDecimal initialEstimateAmount;

    @Column(name="approved_estimate_amount", precision = 15, scale = 2)
    private BigDecimal approvedEstimateAmount;

    @Column(name="currency")
    private String currency;

    @Column(name="deductible", precision = 15, scale = 2)
    private BigDecimal deductible;
}
