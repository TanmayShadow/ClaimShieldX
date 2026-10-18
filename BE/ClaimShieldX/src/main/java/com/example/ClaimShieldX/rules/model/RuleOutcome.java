package com.example.ClaimShieldX.rules.model;

/**
 * What a rule concluded about the data it was given. None of these is a verdict on the claim.
 * INCOMPLETE means evidence needed to run the rule is missing or unreadable; NOT_APPLICABLE means the rule is
 * irrelevant for this claim (an invoice rule on a theft claim), so the report is not filled with missing-evidence noise.
 */
public enum RuleOutcome {
    PASS,
    FLAG,
    INCOMPLETE,
    NOT_APPLICABLE,
    ERROR
}
