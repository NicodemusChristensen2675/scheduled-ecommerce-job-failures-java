package example.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InfraiFailureReporter implements OrderJobService.FailureReporter {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final String baseUrl;
    private final String key;

    public InfraiFailureReporter(@Value("${orders.alerts.base-url}") String baseUrl,
                                 @Value("${orders.alerts.api-key}") String key) {
        this.baseUrl = baseUrl;
        this.key = key;
    }

    @Override public void capture(OrderJobService.JobResult result) {
        if (key.isBlank()) throw new IllegalStateException("Set INFRAI_API_KEY before reporting failures");
        try {
            String body = json.writeValueAsString(Map.of(
                "title", "Scheduled order job failed: " + result.stage(),
                "message", result.detail() == null ? "Scheduled run failed" : result.detail(),
                "level", "error",
                "fingerprint", List.of("order-job", result.stage().name()),
                "exception", "ScheduledJobFailure: " + (result.detail() == null ? "job failed" : result.detail()),
                "context", Map.of("runId", result.runId(), "orderId", result.orderId(), "stage", result.stage().name())
            ));
            for (int attempt = 0; attempt < 4; attempt++) {
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/errors/capture"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", result.runId() + ":" + result.stage())
                    .method("POST", HttpRequest.BodyPublishers.ofString(body)).build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode envelope = json.readTree(response.body());
                if (response.statusCode() == 429 && attempt < 3) {
                    long seconds = response.headers().firstValue("Retry-After")
                        .flatMap(value -> { try { return java.util.Optional.of(Long.parseLong(value)); }
                            catch (NumberFormatException ignored) { return java.util.Optional.<Long>empty(); } })
                        .orElse(1L << attempt);
                    Thread.sleep(Math.min(30, Math.max(0, seconds)) * 1000);
                    continue;
                }
                if (!envelope.path("ok").asBoolean(false)) {
                    JsonNode error = envelope.path("error");
                    throw new CaptureException(response.statusCode(), error.path("code").asText("CAPTURE_REJECTED"), error.toString());
                }
                if (response.statusCode() >= 500) throw new IllegalStateException("Capture request failed: HTTP " + response.statusCode());
                return;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Capture request could not be completed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Capture request interrupted", e);
        }
    }

    public static class CaptureException extends RuntimeException {
        private final int status;
        private final String code;
        CaptureException(int status, String code, String detail) { super(detail); this.status = status; this.code = code; }
        public int status() { return status; }
        public String code() { return code; }
    }
}
