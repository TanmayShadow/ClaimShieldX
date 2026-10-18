package com.example.ClaimShieldX.rules.service;

import com.example.ClaimShieldX.rules.model.RuleResultRow;
import com.example.ClaimShieldX.rules.model.RuleRun;
import com.example.ClaimShieldX.rules.repository.RuleResultRepository;
import com.example.ClaimShieldX.rules.repository.RuleRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Writes a run and all its results in one transaction, so a run is either complete or absent. */
@Service
class RuleRunRecords {
    private final RuleRunRepository runRepository;
    private final RuleResultRepository resultRepository;

    RuleRunRecords(RuleRunRepository runRepository, RuleResultRepository resultRepository) {
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
    }

    @Transactional
    RuleRun store(RuleRun run, List<RuleResultRow> rows) {
        RuleRun saved = runRepository.saveAndFlush(run);
        resultRepository.saveAllAndFlush(rows);
        return saved;
    }
}
