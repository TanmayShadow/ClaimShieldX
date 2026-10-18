package com.example.ClaimShieldX.analysis.reasoning;

import com.example.ClaimShieldX.analysis.Json;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The stored answer of each model role. Written once; a role with no row has not been asked yet. */
@Service
public class AnalysisStore {
    public record Analysis(String role, String status, String modelId, String errorCode, int modelCalls, Map<String, Object> result) {
        public boolean ok() {
            return "OK".equals(status);
        }
    }

    private final JdbcTemplate jdbc;

    public AnalysisStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Analysis> find(UUID organizationId, UUID investigationId, String role) {
        return jdbc.query("select role, status, model_id, error_code, model_calls, result::text from investigation_analyses where organization_id = ? and investigation_id = ? and role = ?",
                (rs, n) -> new Analysis(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), Json.map(rs.getString(6))),
                organizationId, investigationId, role).stream().findFirst();
    }

    public void saveOk(UUID organizationId, UUID claimId, UUID investigationId, String role, String modelId, int calls, Integer in, Integer out, Map<String, Object> result) {
        jdbc.update("""
                insert into investigation_analyses(id, organization_id, claim_id, investigation_id, role, status, model_id, prompt_version, model_calls, input_tokens, output_tokens, result)
                values (?, ?, ?, ?, ?, 'OK', ?, ?, ?, ?, ?, ?::jsonb) on conflict (investigation_id, role) do nothing
                """, UUID.randomUUID(), organizationId, claimId, investigationId, role, modelId, Prompts.VERSION, calls, in, out, Json.write(result));
    }

    public void saveUnavailable(UUID organizationId, UUID claimId, UUID investigationId, String role, String modelId, int calls, String errorCode) {
        jdbc.update("""
                insert into investigation_analyses(id, organization_id, claim_id, investigation_id, role, status, model_id, prompt_version, model_calls, error_code, result)
                values (?, ?, ?, ?, ?, 'UNAVAILABLE', ?, ?, ?, ?, '{}'::jsonb) on conflict (investigation_id, role) do nothing
                """, UUID.randomUUID(), organizationId, claimId, investigationId, role, modelId, Prompts.VERSION, calls, errorCode);
    }
}
