package com.example.ClaimShieldX.readiness.model;

/**
 * How complete the evidence is. Says nothing about the claim itself: it never means a claim is fine or suspect.
 * NOT_READY means the user cannot proceed: the one mandatory document (the one the stage's review is about) is
 * missing or failed. Any other gap is a warning, and the user can proceed with the checks that can still run.
 */
public enum Readiness {
    READY,
    READY_WITH_WARNINGS,
    NOT_READY
}
