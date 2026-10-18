package com.example.ClaimShieldX.investigation;

import com.example.ClaimShieldX.investigation.model.Investigation;
import com.example.ClaimShieldX.investigation.repository.InvestigationRepository;
import com.example.ClaimShieldX.support.TestActors;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The branch where two requests pass the "is one active?" check together and the database's unique index lets only one in. */
class InvestigationRaceTests extends InvestigationApiTestBase {
    @MockitoSpyBean InvestigationRepository repository;

    @Test
    void aRequestThatLosesTheInsertRaceGetsTheSameConflictAsOneThatSawTheActiveInvestigation() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID winner = createOk(actor, claim);
        Investigation active = repository.findById(winner).orElseThrow();
        String key = newKey();
        // The loser looked just before the winner committed (found nothing), then lost the insert; its second look finds the winner.
        Mockito.doReturn(Optional.empty(), Optional.of(active)).when(repository).findActive(any(), any());
        try {
            createInvestigation(actor, claim, key, null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVESTIGATION_ALREADY_ACTIVE"))
                    .andExpect(jsonPath("$.details.activeInvestigationId").value(winner.toString()));
        } finally {
            Mockito.reset(repository);
        }

        assertThat(jdbc.queryForObject("select count(*) from investigations where claim_id = ?", Integer.class, claim)).isEqualTo(1);
        // The key was released, so the same request can be made again once the claim is free.
        postAction(actor, winner, "cancel", newKey()).andExpect(status().isOk());
        createInvestigation(actor, claim, key, null).andExpect(status().isAccepted());
    }
}
