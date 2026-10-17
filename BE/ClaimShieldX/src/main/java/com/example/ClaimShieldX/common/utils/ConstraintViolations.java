package com.example.ClaimShieldX.common.utils;

import org.hibernate.exception.ConstraintViolationException;

/** Finds which database constraint caused an integrity violation, so callers can tell a duplicate from a real defect. */
public final class ConstraintViolations {
    private ConstraintViolations() {
    }

    public static boolean isViolationOf(Throwable error, String constraintName) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && constraintName.equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
