package com.example.ClaimShieldX.extraction.schema;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReviewIssue(IssueCode code, IssueSeverity severity, String field, String message) {
}
