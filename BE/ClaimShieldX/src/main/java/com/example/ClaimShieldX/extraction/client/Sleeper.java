package com.example.ClaimShieldX.extraction.client;

import java.time.Duration;

/** Waiting, behind an interface so retry and rate-limit logic can be tested without real delays. */
public interface Sleeper {
    void sleep(Duration duration) throws InterruptedException;
}
