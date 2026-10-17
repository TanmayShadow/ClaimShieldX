package com.example.ClaimShieldX.extraction.client;

import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
class ThreadSleeper implements Sleeper {
    @Override
    public void sleep(Duration duration) throws InterruptedException {
        if (!duration.isNegative() && !duration.isZero()) {
            Thread.sleep(duration);
        }
    }
}
