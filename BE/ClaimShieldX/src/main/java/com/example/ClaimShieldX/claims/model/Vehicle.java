package com.example.ClaimShieldX.claims.model;

//        Vehicle	vehicle_token, permitted registration reference, make/model

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class Vehicle {
    @Column(name="vehicle_token")
    private String vehicleToken;

    @Column(name="permitted_registration_reference")
    private String permittedRegistrationReference;

    @Column(name="make")
    private String make;

    @Column(name="model")
    private String model;

}
