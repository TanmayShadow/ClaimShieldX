package com.example.ClaimShieldX.claims.model;

//        Vehicle	vehicle_token, make/model. The raw registration number is deliberately never stored.

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
public class Vehicle {
    @Column(name="vehicle_token")
    private String vehicleToken;

    @Column(name="vehicle_make")
    private String make;

    @Column(name="vehicle_model")
    private String model;

}
