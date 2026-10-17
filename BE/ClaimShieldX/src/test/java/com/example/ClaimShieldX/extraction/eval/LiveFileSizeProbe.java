package com.example.ClaimShieldX.extraction.eval;

import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.extraction.client.GeminiExtractionClient;
import com.example.ClaimShieldX.extraction.client.ModelCallException;
import com.example.ClaimShieldX.extraction.client.ModelRequest;
import com.example.ClaimShieldX.extraction.client.ModelResponse;
import com.example.ClaimShieldX.extraction.config.ExtractionProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

/**
 * Finds out how large a file the model really accepts inline, with one call per size and no retries. Skipped unless
 * CLAIMSHIELD_LIVE_GEMINI=1 and CLAIMSHIELD_LIVE_SIZES is set (comma list of sizes in bytes, for example
 * 12000000,14680064,15500000). The file is a synthetic noise image with a short clean block of text in a corner.
 */
@EnabledIfEnvironmentVariable(named = "CLAIMSHIELD_LIVE_GEMINI", matches = "1")
@EnabledIfEnvironmentVariable(named = "CLAIMSHIELD_LIVE_SIZES", matches = ".+")
class LiveFileSizeProbe {
    @Test
    void probe() {
        ExtractionProperties props = ExtractionProperties.defaults();
        GeminiExtractionClient client = new GeminiExtractionClient(props, System.getenv("SPRING_AI_GOOGLE_GENAI_API_KEY"));
        for (String size : List.of(System.getenv("CLAIMSHIELD_LIVE_SIZES").split(","))) {
            long wanted = Long.parseLong(size.strip());
            byte[] png = SyntheticDocuments.noisePng(wanted, wanted);
            long started = System.nanoTime();
            String outcome;
            try {
                ModelResponse response = client.extract(new ModelRequest(DocumentType.FINAL_INVOICE, png, "image/png", props.model(), props.schemaVersion(), props.promptVersion()));
                outcome = "ACCEPTED (" + response.inputTokens() + " input tokens, answer " + response.json().length() + " chars)";
            } catch (ModelCallException e) {
                outcome = "REFUSED as " + e.category() + (e.getCause() == null ? "" : " [" + e.getCause().getClass().getSimpleName() + "]");
            }
            System.out.printf("SIZE %,d bytes (%.2f MB, base64 about %.2f MB): %s in %d ms%n", png.length, png.length / 1e6, png.length * 4.0 / 3 / 1e6, outcome,
                    (System.nanoTime() - started) / 1_000_000);
        }
    }
}
