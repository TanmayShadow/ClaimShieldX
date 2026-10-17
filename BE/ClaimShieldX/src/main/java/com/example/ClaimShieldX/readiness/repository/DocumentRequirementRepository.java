package com.example.ClaimShieldX.readiness.repository;

import com.example.ClaimShieldX.readiness.model.DocumentRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** The whole table is a few dozen rows and is the same for every organization, so it is simply read in full. */
@Repository
public interface DocumentRequirementRepository extends JpaRepository<DocumentRequirement, UUID> {
}
