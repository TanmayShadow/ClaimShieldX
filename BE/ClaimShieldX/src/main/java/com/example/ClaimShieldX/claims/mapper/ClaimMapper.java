package com.example.ClaimShieldX.claims.mapper;

import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.claims.dto.*;
import com.example.ClaimShieldX.claims.model.*;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ClaimMapper {
    public Claim toEntity(ClaimCreationRequest request, UserPrincipal userPrincipal){
        Claim claim = new Claim();
        claim.setInternalId(request.internalId().strip());
        claim.setClaimVersion(request.claimVersion());
        claim.setCurrentStage(request.currentStage());
        claim.setPolicy(toPolicy(request.policy()));
        claim.setClaimant(toClaimant(request.claimant()));
        claim.setGarage(toGarage(request.garage()));
        claim.setIncident(toIncident(request.incident()));
        claim.setFinancial(toFinancial(request.financial()));
        claim.setOrganizationId(userPrincipal.getOrganization().getId());

        // Server-controlled: never taken from the request body.
        Instant now = Instant.now();
        Workflow workflow = new Workflow();
        workflow.setCreatedBy(userPrincipal.getId().toString());
        workflow.setCreatedAt(now);
        workflow.setUpdatedAt(now);
        claim.setWorkflow(workflow);

        return claim;
    }

    public ClaimResponse toResponse(Claim claim){
        return new ClaimResponse(
                claim.getId(),
                claim.getInternalId(),
                claim.getClaimVersion(),
                claim.getCurrentStage(),
                toPolicyDto(claim.getPolicy()),
                toClaimantDto(claim.getClaimant()),
                toGarageDto(claim.getGarage()),
                toIncidentDto(claim.getIncident()),
                toFinancialDto(claim.getFinancial()),
                claim.getCreatedAt(),
                claim.getUpdatedAt()
        );
    }

    private Policy toPolicy(PolicyDto dto){
        Policy policy = new Policy();
        policy.setPolicyToken(dto.policyToken());
        policy.setPolicyType(dto.policyType());
        policy.setPolicyStartDate(dto.policyStartDate());
        policy.setPolicyEndDate(dto.policyEndDate());
        return policy;
    }

    private Claimant toClaimant(ClaimantDto dto){
        if(dto == null)
            return null;
        Claimant claimant = new Claimant();
        claimant.setClaimantToken(dto.claimantToken());
        return claimant;
    }

    private Garage toGarage(GarageDto dto){
        Garage garage = new Garage();
        garage.setGarageId(dto.garageId());
        return garage;
    }

    private Incident toIncident(IncidentDto dto){
        Incident incident = new Incident();
        incident.setIncidentAt(dto.incidentAt());
        incident.setReportedAt(dto.reportedAt());
        incident.setPermittedLocationPrecision(dto.permittedLocationPrecision());
        return incident;
    }

    private Financial toFinancial(FinancialDto dto){
        Financial financial = new Financial();
        financial.setInitialEstimateAmount(dto.initialEstimateAmount());
        financial.setApprovedEstimateAmount(dto.approvedEstimateAmount());
        financial.setCurrency(dto.currency());
        financial.setDeductible(dto.deductible());
        return financial;
    }

    private PolicyDto toPolicyDto(Policy policy){
        if(policy == null)
            return null;
        return new PolicyDto(policy.getPolicyToken(), policy.getPolicyType(), policy.getPolicyStartDate(), policy.getPolicyEndDate());
    }

    private ClaimantDto toClaimantDto(Claimant claimant){
        if(claimant == null)
            return null;
        return new ClaimantDto(claimant.getClaimantToken());
    }

    private GarageDto toGarageDto(Garage garage){
        if(garage == null)
            return null;
        return new GarageDto(garage.getGarageId());
    }

    private IncidentDto toIncidentDto(Incident incident){
        if(incident == null)
            return null;
        return new IncidentDto(incident.getIncidentAt(), incident.getReportedAt(), incident.getPermittedLocationPrecision());
    }

    private FinancialDto toFinancialDto(Financial financial){
        if(financial == null)
            return null;
        return new FinancialDto(financial.getInitialEstimateAmount(), financial.getApprovedEstimateAmount(), financial.getCurrency(), financial.getDeductible());
    }
}
