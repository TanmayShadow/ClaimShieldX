package com.example.ClaimShieldX.rules;

import com.example.ClaimShieldX.support.TestActors;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.example.ClaimShieldX.rules.model.RuleRun;
import com.example.ClaimShieldX.rules.repository.RuleRunRepository;
import org.mockito.Mockito;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RuleRunConcurrencyTests extends RuleApiTestBase {
    @MockitoSpyBean RuleRunRepository runRepository;

    /** The branch the threads above rarely reach: this request looked, found nothing, and lost the insert to an identical request. */
    @Test
    void aRequestThatLosesTheInsertRaceIsGivenTheWinnersRunNotAnError() throws Exception {
        TestActors actor = investigator();
        UUID claim = cleanClaim(actor, "CLM-LOSER");
        String winner = runOk(actor, claim);
        UUID winnerId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(winner, "$.id"));
        RuleRun existing = runRepository.findById(winnerId).orElseThrow();
        Mockito.doReturn(Optional.empty(), Optional.of(existing)).when(runRepository)
                .findByOrganizationIdAndClaimIdAndRuleSetVersionAndConfigSha256AndInputFingerprint(any(), any(), any(), any(), any());
        try {
            runRules(actor, claim).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(winnerId.toString())).andExpect(jsonPath("$.reusedExistingRun").value(true));
        } finally {
            Mockito.reset(runRepository);
        }

        assertThat(runCount(claim)).isEqualTo(1);
    }

    @Test
    @Timeout(120)
    void manyRequestsWithIdenticalInputsAtTheSameMomentCreateExactlyOneRun() throws Exception {
        TestActors actor = investigator();
        UUID claim = cleanClaim(actor, "CLM-RACE");
        int requests = 8;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<int[]>> futures = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    var response = runRules(actor, claim).andReturn().getResponse();
                    synchronized (ids) {
                        ids.add(JsonPath.read(response.getContentAsString(), "$.id"));
                    }
                    return new int[]{response.getStatus()};
                }));
            }
            ready.await();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<int[]> future : futures) {
                statuses.add(future.get(90, TimeUnit.SECONDS)[0]);
            }

            assertThat(statuses).containsOnly(200, 201);
            assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
            assertThat(ids).hasSize(requests).containsOnly(ids.get(0));
            assertThat(runCount(claim)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from rule_results where claim_id = ?", Integer.class, claim)).isEqualTo(8);
        } finally {
            pool.shutdownNow();
        }
    }
}
