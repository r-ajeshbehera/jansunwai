package com.jansunwai.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real HTTP against the real app (random port) and a real PostgreSQL with PostGIS + pgvector.
 *
 * Database, in this order:
 *  1. TEST_DB_URL / TEST_DB_USERNAME / TEST_DB_PASSWORD -> use an existing database (it must be EMPTY: Flyway migrates it);
 *  2. otherwise Testcontainers starts the image TEST_DB_IMAGE (default jansunwai-postgres:16, built from docker/postgres).
 * The container is started once and shared by all integration test classes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegrationTestBase {
    private static final PostgreSQLContainer<?> DB;

    static {
        if (System.getenv("TEST_DB_URL") == null) {
            String image = System.getenv().getOrDefault("TEST_DB_IMAGE", "jansunwai-postgres:16");
            DB = new PostgreSQLContainer<>(DockerImageName.parse(image).asCompatibleSubstituteFor("postgres"));
            DB.start();
        } else {
            DB = null;
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        if (DB != null) {
            r.add("spring.datasource.url", DB::getJdbcUrl);
            r.add("spring.datasource.username", DB::getUsername);
            r.add("spring.datasource.password", DB::getPassword);
        } else {
            r.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
            r.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DB_USERNAME", "postgres"));
            r.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", ""));
        }
    }

    public static final String PASSWORD = "Demo@1234";
    protected static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcTemplate jdbc;

    public record Res(int status, JsonNode body) {
        public String text(String field) { return body.path(field).asText(); }
        public long id() { return body.path("id").asLong(); }
    }

    protected Res api(String method, String path, Object body, String token) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path))
                    .timeout(Duration.ofSeconds(30));
            if (token != null) b.header("Authorization", "Bearer " + token);
            if (body != null) {
                b.header("Content-Type", "application/json");
                b.method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            } else {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode node = r.body() == null || r.body().isBlank() ? JSON.nullNode() : JSON.readTree(r.body());
            return new Res(r.statusCode(), node);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected Res get(String path, String token) { return api("GET", path, null, token); }
    protected Res post(String path, Object body, String token) { return api("POST", path, body, token); }
    protected Res patch(String path, Object body, String token) { return api("PATCH", path, body, token); }

    protected Res upload(String path, String kind, String filename, String contentType, byte[] data, String token) {
        try {
            String boundary = "----js" + System.nanoTime();
            var out = new java.io.ByteArrayOutputStream();
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"kind\"\r\n\r\n" + kind + "\r\n").getBytes());
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename
                    + "\"\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes());
            out.write(data);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes());
            HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build();
            HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            return new Res(r.statusCode(), r.body().isBlank() ? JSON.nullNode() : JSON.readTree(r.body()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Logs in once per user and caches the token (the tokens are only revoked by the logout test, which uses its own). */
    protected String token(String email) {
        return TOKENS.computeIfAbsent(email, e -> {
            Res r = post("/auth/login", Map.of("email", e, "password", PASSWORD), null);
            if (r.status() != 200) throw new IllegalStateException("login failed for " + e + ": " + r);
            return r.text("accessToken");
        });
    }

    protected static final String CITIZEN = "citizen1@jansunwai.demo", CITIZEN2 = "citizen2@jansunwai.demo",
            ROADS = "roads.officer@jansunwai.demo", ROADS_FIELD = "roads.field@jansunwai.demo",
            ROADS_FIELD2 = "roads.field2@jansunwai.demo", WATER = "water.officer@jansunwai.demo",
            DRAINAGE = "drainage.officer@jansunwai.demo", DRAINAGE_FIELD = "drainage.field@jansunwai.demo",
            ADMIN = "admin@jansunwai.demo";

    /** Saheed Nagar demo ward (W01) and a point inside it. */
    protected static final double LAT = 20.2960, LNG = 85.8430;

    protected Res submit(String title, String description, Double lat, Double lng) {
        var body = new java.util.HashMap<String, Object>();
        body.put("title", title);
        body.put("description", description);
        if (lat != null) { body.put("latitude", lat); body.put("longitude", lng); }
        Res r = post("/complaints", body, token(CITIZEN));
        if (r.status() != 201) throw new IllegalStateException("submit failed: " + r);
        return r;
    }

    /** The AI pipeline runs asynchronously after commit; wait until it has moved the complaint on. */
    protected JsonNode awaitStatus(long id, String status) {
        long end = System.currentTimeMillis() + 15_000;
        JsonNode last = null;
        while (System.currentTimeMillis() < end) {
            last = get("/complaints/" + id, token(ADMIN)).body();
            if (status.equals(last.path("status").asText())) return last;
            sleep(150);
        }
        throw new AssertionError("complaint " + id + " did not reach " + status + "; last = " + last);
    }

    protected long departmentId(String code) {
        return jdbc.queryForObject("select id from departments where code = ?", Long.class, code);
    }

    protected long userId(String email) {
        return jdbc.queryForObject("select id from users where email = ?", Long.class, email);
    }

    /** Submit + wait for AI + approve as the department officer. Returns the complaint id. */
    protected long approvedComplaint(String title, String description, String officerEmail, String deptCode,
                                     String category, String priority) {
        long id = submit(title, description, LAT, LNG).id();
        awaitStatus(id, "UNDER_REVIEW");
        Res r = post("/complaints/" + id + "/review", Map.of("decision", "APPROVE", "category", category,
                "departmentId", departmentId(deptCode), "priority", priority), token(officerEmail));
        if (r.status() != 200) throw new IllegalStateException("approve failed: " + r);
        return id;
    }

    protected static final byte[] PNG = pngBytes();

    private static byte[] pngBytes() {
        byte[] head = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] b = new byte[64];
        System.arraycopy(head, 0, b, 0, head.length);
        return b;
    }

    protected static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
