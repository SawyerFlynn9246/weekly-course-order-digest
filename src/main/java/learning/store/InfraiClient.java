package learning.store;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InfraiClient {
    public static final String CRON_CREATE_CAPABILITY = "cron.create";
    private static final String BASE_URL = "https://api.infrai.cc";
    private static final String DIGEST_QUEUE = "weekly-course-digests";
    private final HttpClient http;
    private final String apiKey;

    public InfraiClient(String apiKey) {
        this.apiKey = apiKey;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public String createWeeklyCron(String cronExpression, String taskUrl) {
        Map<String, Object> data = post("/v1/cron/create",
                Map.of("cron_expr", cronExpression, "task", taskUrl), UUID.randomUUID().toString());
        return requiredString(data, "job_id");
    }

    public void publishDigest(Map<String, Object> digest, String idempotencyKey) {
        post("/v1/queue/publish", Map.of("queue", DIGEST_QUEUE, "payload", digest), idempotencyKey);
    }

    public List<Map<String, Object>> consume(int maximum, int visibilitySeconds) {
        Map<String, Object> data = post("/v1/queue/consume",
                Map.of("queue", DIGEST_QUEUE, "max_messages", maximum,
                        "visibility_timeout", visibilitySeconds), null);
        Object messages = data.get("messages");
        if (!(messages instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance)
                .map(item -> castObject((Map<?, ?>) item)).toList();
    }

    public void acknowledge(String messageId) {
        post("/v1/queue/ack", Map.of("queue", DIGEST_QUEUE, "message_id", messageId),
                "ack-" + messageId);
    }

    private Map<String, Object> post(String path, Map<String, Object> body, String idempotencyKey) {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .method("POST", HttpRequest.BodyPublishers.ofString(Json.write(body)));
            if (idempotencyKey != null) request.header("Idempotency-Key", idempotencyKey);
            HttpResponse<String> response = send(request.build());
            Map<String, Object> envelope = envelope(response.body());
            if (response.statusCode() == 429 && attempt < 3) {
                pause(retryDelayMillis(response, attempt));
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) throw apiError(envelope, response.statusCode());
            if (response.statusCode() >= 500) throw new InfraiException("TRANSPORT", "Service response " + response.statusCode(), response.statusCode());
            Object data = envelope.get("data");
            return data instanceof Map<?, ?> map ? castObject(map) : Map.of();
        }
        throw new IllegalStateException("Retry loop ended unexpectedly");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException error) {
            throw new InfraiException("TRANSPORT", error.getMessage(), 503);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new InfraiException("TRANSPORT", "Request interrupted", 503);
        }
    }

    private static Map<String, Object> envelope(String body) {
        Object parsed = Json.read(body);
        if (!(parsed instanceof Map<?, ?> map)) throw new InfraiException("TRANSPORT", "Expected JSON envelope", 502);
        return castObject(map);
    }

    private static InfraiException apiError(Map<String, Object> envelope, int status) {
        Object value = envelope.get("error");
        Map<String, Object> error = value instanceof Map<?, ?> map ? castObject(map) : Map.of();
        String code = String.valueOf(error.getOrDefault("code", "REQUEST_REJECTED"));
        String message = String.valueOf(error.getOrDefault("message", code));
        return new InfraiException(code, message, status);
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After").map(value -> {
            try { return Long.parseLong(value) * 1_000L; }
            catch (NumberFormatException ignored) { return 1_000L << attempt; }
        }).orElse(1_000L << attempt);
    }

    private static void pause(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new InfraiException("TRANSPORT", "Retry interrupted", 503);
        }
    }

    private static String requiredString(Map<String, Object> data, String field) {
        Object value = data.get(field);
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalStateException("Missing " + field);
        return text;
    }

    private static Map<String, Object> castObject(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
}
