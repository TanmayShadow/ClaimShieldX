package com.example.ClaimShieldX.extraction.eval;

import com.example.ClaimShieldX.documents.model.DocumentStatus;
import com.example.ClaimShieldX.extraction.client.*;
import com.example.ClaimShieldX.extraction.config.ExtractionProperties;
import com.example.ClaimShieldX.extraction.eval.EvaluationScorer.Outcome;
import com.example.ClaimShieldX.extraction.eval.EvaluationScorer.SampleScore;
import com.example.ClaimShieldX.extraction.eval.SyntheticDocuments.Sample;
import com.example.ClaimShieldX.extraction.validate.ExtractionProcessor;
import com.example.ClaimShieldX.extraction.validate.ExtractionProcessor.ClaimFacts;
import com.example.ClaimShieldX.extraction.validate.ExtractionProcessor.Processed;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Measures extraction against the live model on the synthetic dataset. It calls the real Gemini API with the project's key, so
 * it is skipped unless CLAIMSHIELD_LIVE_GEMINI=1 and the default build can never reach a model. Only synthetic documents are
 * sent. Settings come from the environment: CLAIMSHIELD_EVAL_MODEL, _FALLBACKS (comma list; empty for a clean single-model
 * measurement), _ONLY (comma list of sample ids), _OUT (folder for the report), _LABEL, _THINKING.
 *
 * <p>Requests are spaced by the same rate gate the application uses, below the free tier's 15 requests a minute.
 */
@EnabledIfEnvironmentVariable(named = "CLAIMSHIELD_LIVE_GEMINI", matches = "1")
class LiveExtractionEvaluation {
    private static final ClaimFacts CLAIM = new ClaimFacts(SyntheticDocuments.GARAGE_ID, SyntheticDocuments.VEHICLE_ID, "INR", SyntheticDocuments.INCIDENT);

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static List<String> csv(String value) {
        return Arrays.stream(value.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    record Row(Sample sample, SampleScore score, Processed processed, ModelAnswer answer, ModelFailure failure, long latencyMs) {
    }

    @Test
    void measure() throws Exception {
        String apiKey = System.getenv("SPRING_AI_GOOGLE_GENAI_API_KEY");
        String model = env("CLAIMSHIELD_EVAL_MODEL", "gemini-3.5-flash-lite");
        List<String> fallbacks = csv(env("CLAIMSHIELD_EVAL_FALLBACKS", ""));
        Set<String> only = new LinkedHashSet<>(csv(env("CLAIMSHIELD_EVAL_ONLY", "")));
        Path out = Path.of(env("CLAIMSHIELD_EVAL_OUT", System.getProperty("java.io.tmpdir")));
        String label = env("CLAIMSHIELD_EVAL_LABEL", model);
        String thinking = env("CLAIMSHIELD_EVAL_THINKING", "LOW");
        Files.createDirectories(out);

        ExtractionProperties props = new ExtractionProperties("gemini", model, fallbacks, "itemized-cost-2", "1", thinking, 16384, Duration.ofSeconds(90), 3,
                Duration.ofSeconds(2), Duration.ofSeconds(30), 10, 1, 20L * 1024 * 1024, 40, 200, 5, Duration.ofMinutes(5), new BigDecimal("1.00"), ZoneId.of("Asia/Kolkata"));
        Sleeper sleeper = d -> Thread.sleep(d);
        ModelCaller caller = new ModelCaller(props, new GeminiExtractionClient(props, apiKey), new ModelCallGate(props, sleeper), sleeper);
        ExtractionProcessor processor = new ExtractionProcessor(props);

        List<Sample> samples = SyntheticDocuments.dataset().stream().filter(s -> only.isEmpty() || only.contains(s.id())).toList();
        List<Row> rows = new ArrayList<>();
        System.out.println("EVAL " + label + ": " + samples.size() + " documents, model chain " + props.modelChain());
        for (Sample sample : samples) {
            long started = System.nanoTime();
            ModelAnswer answer = null;
            ModelFailure failure = null;
            Processed processed = null;
            SampleScore score = null;
            try {
                answer = caller.call(sample.type(), sample.content(), sample.mimeType());
                processed = processor.process(answer.document(), sample.type(), UUID.randomUUID(), pages(sample), CLAIM);
                score = EvaluationScorer.score(sample, processed);
            } catch (ModelFailure f) {
                failure = f;
            }
            Row row = new Row(sample, score, processed, answer, failure, Duration.ofNanos(System.nanoTime() - started).toMillis());
            rows.add(row);
            System.out.println("  " + line(row));
            write(out, label, rows);
        }
        System.out.println(summary(rows));
    }

    private static int pages(Sample sample) throws Exception {
        if (!sample.mimeType().equals("application/pdf")) {
            return 1;
        }
        try (PDDocument document = Loader.loadPDF(sample.content())) {
            return document.getNumberOfPages();
        }
    }

    private static String line(Row r) {
        if (r.failure() != null) {
            return String.format("%-26s FAILED %s after %d call(s), %d ms", r.sample().id(), r.failure().code(), r.failure().modelCalls(), r.latencyMs());
        }
        SampleScore s = r.score();
        long correct = s.count(Outcome.CORRECT);
        long expectedPresent = s.fields().stream().filter(f -> f.expected() != null).count();
        return String.format("%-26s fields %d/%d invented %d | lines %d/%d | status %s (expected %s)%s | %d ms, %d call(s), %s tokens",
                r.sample().id(), correct, expectedPresent, s.count(Outcome.INVENTED), s.lines().attributesCorrect(), s.lines().attributesExpected(),
                s.actualStatus(), s.expectedStatus(), s.statusAgrees() ? "" : "  <-- DIFFERS", r.latencyMs(), r.answer().modelCalls(),
                r.answer().inputTokens() + "+" + r.answer().outputTokens());
    }

    // ------------------------------------------------------------ report

    static String summary(List<Row> rows) {
        StringBuilder sb = new StringBuilder("\n=== SUMMARY ===\n");
        sb.append(aggregate("all documents", rows));
        for (String type : rows.stream().map(r -> r.sample().type().name()).distinct().toList()) {
            sb.append(aggregate("type " + type, rows.stream().filter(r -> r.sample().type().name().equals(type)).toList()));
        }
        for (String group : rows.stream().map(r -> r.sample().group()).distinct().toList()) {
            sb.append(aggregate("group " + group, rows.stream().filter(r -> r.sample().group().equals(group)).toList()));
        }
        return sb.toString();
    }

    private static String aggregate(String label, List<Row> rows) {
        List<Row> ok = rows.stream().filter(r -> r.failure() == null).toList();
        long correct = 0, wrong = 0, missed = 0, invented = 0, absent = 0, lineCorrect = 0, lineExpected = 0, countMatch = 0, statusAgree = 0, issuesExpected = 0, issuesRaised = 0, unsafe = 0;
        for (Row r : ok) {
            SampleScore s = r.score();
            correct += s.count(Outcome.CORRECT);
            wrong += s.count(Outcome.WRONG);
            missed += s.count(Outcome.MISSED);
            invented += s.count(Outcome.INVENTED);
            absent += s.count(Outcome.CORRECTLY_ABSENT);
            lineCorrect += s.lines().attributesCorrect();
            lineExpected += s.lines().attributesExpected();
            countMatch += s.lines().countMatches() ? 1 : 0;
            statusAgree += s.statusAgrees() ? 1 : 0;
            unsafe += s.unsafelyVerified() ? 1 : 0;
            issuesExpected += r.sample().expected().expectedIssues().size();
            issuesRaised += r.sample().expected().expectedIssues().size() - s.expectedIssuesNotRaised().size();
        }
        List<Long> latencies = rows.stream().map(Row::latencyMs).sorted().toList();
        long calls = ok.stream().mapToLong(r -> r.answer().modelCalls()).sum();
        return String.format(Locale.ROOT,
                "%-28s n=%d (failed %d) | UNSAFE verified %d | fields correct %d/%d (%s) wrong %d missed %d | absent-field values invented %d of %d | line attrs %d/%d (%s), line count right %d/%d | status as expected %d/%d | expected issues raised %d/%d | model calls %d, median %d ms%n",
                label, rows.size(), rows.size() - ok.size(), unsafe, correct, correct + wrong + missed, pct(correct, correct + wrong + missed), wrong, missed,
                invented, invented + absent, lineCorrect, lineExpected, pct(lineCorrect, lineExpected), countMatch, ok.size(), statusAgree, ok.size(),
                issuesRaised, issuesExpected, calls, latencies.isEmpty() ? 0 : latencies.get(latencies.size() / 2));
    }

    private static String pct(long a, long b) {
        return b == 0 ? "n/a" : String.format(Locale.ROOT, "%.1f%%", 100.0 * a / b);
    }

    private static void write(Path out, String label, List<Row> rows) throws Exception {
        List<Map<String, Object>> records = new ArrayList<>();
        for (Row r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.sample().id());
            m.put("group", r.sample().group());
            m.put("type", r.sample().type());
            m.put("description", r.sample().description());
            m.put("bytes", r.sample().content().length);
            m.put("latencyMs", r.latencyMs());
            if (r.failure() != null) {
                m.put("failure", r.failure().code());
                m.put("modelCalls", r.failure().modelCalls());
            } else {
                m.put("modelId", r.answer().modelId());
                m.put("modelCalls", r.answer().modelCalls());
                m.put("inputTokens", r.answer().inputTokens());
                m.put("outputTokens", r.answer().outputTokens());
                m.put("expectedStatus", r.score().expectedStatus());
                m.put("unsafelyVerified", r.score().unsafelyVerified());
                m.put("actualStatus", r.score().actualStatus());
                m.put("expectedIssuesNotRaised", r.score().expectedIssuesNotRaised());
                m.put("issues", r.processed().result().issues());
                m.put("modelWarnings", r.processed().result().modelWarnings());
                m.put("fieldOutcomes", r.score().fields());
                m.put("lines", r.score().lines());
                m.put("computed", r.processed().result().computed());
            }
            records.add(m);
        }
        JsonMapper mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
        Files.writeString(out.resolve("eval-" + label.replaceAll("[^A-Za-z0-9._-]", "_") + ".json"), mapper.writeValueAsString(records));
        Files.writeString(out.resolve("eval-" + label.replaceAll("[^A-Za-z0-9._-]", "_") + ".txt"),
                rows.stream().map(LiveExtractionEvaluation::line).collect(Collectors.joining("\n")) + "\n" + summary(rows));
    }
}
