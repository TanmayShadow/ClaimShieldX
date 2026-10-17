package com.example.ClaimShieldX.claims.service;

import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.claims.dto.ClaimCreationRequest;
import com.example.ClaimShieldX.claims.dto.ClaimResponse;
import com.example.ClaimShieldX.claims.mapper.ClaimMapper;
import com.example.ClaimShieldX.claims.model.Claim;
import com.example.ClaimShieldX.claims.repository.ClaimsRepository;
import com.example.ClaimShieldX.exceptions.DuplicateClaimException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class ClaimsService {
    private static final String ORG_INTERNAL_ID_CONSTRAINT = "uq_claims_org_internal_id";

    private final ClaimsRepository claimsRepository;
    private final ClaimMapper claimMapper;

    public ClaimsService(ClaimsRepository claimsRepository, ClaimMapper claimMapper){
        this.claimsRepository = claimsRepository;
        this.claimMapper = claimMapper;
    }

    @Transactional
    public ClaimResponse createClaim(ClaimCreationRequest claimCreationRequest, UserPrincipal userPrincipal){
        Claim claim = claimMapper.toEntity(claimCreationRequest, userPrincipal);
        if(claimsRepository.existsByOrganizationIdAndInternalId(claim.getOrganizationId(), claim.getInternalId())){
            throw new DuplicateClaimException("Duplicate Claim found for same internal_id.");
        }
        try {
            return claimMapper.toResponse(claimsRepository.saveAndFlush(claim));
        } catch (DataIntegrityViolationException e) {
            // The exists-check above can lose a race; the unique constraint is the real guard.
            if(violatesInternalIdUniqueness(e)){
                throw new DuplicateClaimException("Duplicate Claim found for same internal_id.");
            }
            throw e;
        }
    }

    private boolean violatesInternalIdUniqueness(Throwable e){
        for(Throwable cause = e; cause != null; cause = cause.getCause()){
            if(cause instanceof ConstraintViolationException violation
                    && ORG_INTERNAL_ID_CONSTRAINT.equals(violation.getConstraintName())){
                return true;
            }
        }
        return false;
    }
}
