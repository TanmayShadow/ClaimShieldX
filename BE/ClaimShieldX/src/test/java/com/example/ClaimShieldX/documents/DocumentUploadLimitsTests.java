package com.example.ClaimShieldX.documents;

import com.example.ClaimShieldX.support.DbFixtures;
import com.example.ClaimShieldX.support.TestActors;
import com.example.ClaimShieldX.support.TestFiles;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against a real embedded server, because MockMvc does not apply the servlet container's multipart limits.
 * Limits are shrunk so the boundaries can be tested with small files: 2 KB per file and 3 documents per claim.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.servlet.multipart.max-file-size=2KB",
        "spring.servlet.multipart.max-request-size=4KB",
        "app.documents.max-per-claim=3"
})
class DocumentUploadLimitsTests {
    private static final String BOUNDARY = "----claimshield-test-boundary";

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.example.ClaimShieldX.documents.storage.DocumentStorage storage;
    @Value("${app.auth.jwt-secret}") private String jwtSecret;

    private final HttpClient http = HttpClient.newHttpClient();
    private DbFixtures fixtures;
    private TestActors actor;
    private UUID claim;

    @BeforeEach
    void setUp() {
        fixtures = new DbFixtures(jdbc);
        actor = TestActors.create(fixtures, jwtSecret, "INVESTIGATOR");
        claim = fixtures.claim(actor.organizationId(), "CLM-" + UUID.randomUUID());
    }

    @AfterEach
    void cleanUp() {
        for (String objectKey : jdbc.queryForList("select object_key from documents where organization_id = ?", String.class, actor.organizationId())) {
            storage.delete(objectKey);
        }
        fixtures.cleanUp();
    }

    private HttpResponse<String> post(byte[] fileContent, String key) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"documentType\"\r\n\r\nFINAL_INVOICE\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"invoice.pdf\"\r\nContent-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(fileContent);
        body.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/claims/" + claim + "/documents"))
                .header("Authorization", actor.bearer())
                .header("Idempotency-Key", key)
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void aFileExactlyAtTheLimitIsAcceptedThroughARealServer() throws Exception {
        HttpResponse<String> response = post(TestFiles.pdfOfSize(1, 2048), "key-" + UUID.randomUUID());

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat((Integer) JsonPath.read(response.body(), "$.sizeBytes")).isEqualTo(2048);
    }

    @Test
    void aFileOneByteOverTheLimitIsRejectedWithA413AndStoresNothing() throws Exception {
        HttpResponse<String> response = post(TestFiles.pdfOfSize(2, 2049), "key-" + UUID.randomUUID());

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("PAYLOAD_TOO_LARGE");
        assertThat(response.body()).doesNotContain("Exception").doesNotContain("org.apache");
        assertThat(jdbc.queryForObject("select count(*) from documents where claim_id = ?", Integer.class, claim)).isZero();
    }

    @Test
    void aMuchLargerFileIsAlsoRejectedCleanly() throws Exception {
        HttpResponse<String> response = post(TestFiles.pdfOfSize(3, 40_000), "key-" + UUID.randomUUID());

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("PAYLOAD_TOO_LARGE");
    }

    @Test
    void aClaimCanHoldOnlyTheConfiguredNumberOfDocuments() throws Exception {
        String lastKey = null;
        for (int i = 0; i < 3; i++) {
            lastKey = "key-" + UUID.randomUUID();
            assertThat(post(TestFiles.pdfOfSize(10 + i, 500), lastKey).statusCode()).isEqualTo(201);
        }

        HttpResponse<String> fourth = post(TestFiles.pdfOfSize(99, 500), "key-" + UUID.randomUUID());

        assertThat(fourth.statusCode()).isEqualTo(409);
        assertThat((String) JsonPath.read(fourth.body(), "$.code")).isEqualTo("DOCUMENT_LIMIT_REACHED");
        assertThat(jdbc.queryForObject("select count(*) from documents where claim_id = ?", Integer.class, claim)).isEqualTo(3);
    }

    @Test
    void replayingAnEarlierUploadStillWorksOnceTheClaimIsFull() throws Exception {
        String firstKey = "key-" + UUID.randomUUID();
        byte[] first = TestFiles.pdfOfSize(20, 500);
        assertThat(post(first, firstKey).statusCode()).isEqualTo(201);
        post(TestFiles.pdfOfSize(21, 500), "key-" + UUID.randomUUID());
        post(TestFiles.pdfOfSize(22, 500), "key-" + UUID.randomUUID());

        HttpResponse<String> replay = post(first, firstKey);

        assertThat(replay.statusCode()).isEqualTo(201);
        assertThat(replay.headers().firstValue("Idempotent-Replay")).contains("true");
    }

    @Test
    void theOriginalCanBeDownloadedThroughARealServerByteForByte() throws Exception {
        byte[] content = TestFiles.pdfOfSize(30, 1500);
        HttpResponse<String> uploaded = post(content, "key-" + UUID.randomUUID());
        String id = JsonPath.read(uploaded.body(), "$.id");

        HttpResponse<byte[]> download = http.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/v1/claims/" + claim + "/documents/" + id + "/content"))
                .header("Authorization", actor.bearer()).GET().build(), HttpResponse.BodyHandlers.ofByteArray());

        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(content);
        assertThat(download.headers().firstValue("Content-Length")).contains("1500");
        assertThat(download.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
    }
}
