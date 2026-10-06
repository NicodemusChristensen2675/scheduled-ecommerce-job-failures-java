package example.orders;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class OrderJobApplication {
    private final OrderJobService jobs;
    public OrderJobApplication(OrderJobService jobs) { this.jobs = jobs; }
    public static void main(String[] args) { SpringApplication.run(OrderJobApplication.class, args); }

    @PostMapping("/order-jobs/results")
    public OrderJobService.Decision record(@RequestBody OrderJobService.JobResult result) { return jobs.record(result); }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of("error", error.getMessage()));
    }

    @ExceptionHandler(InfraiFailureReporter.CaptureException.class)
    public ResponseEntity<Map<String, String>> rejected(InfraiFailureReporter.CaptureException error) {
        HttpStatus status = error.status() >= 400 && error.status() < 500
            ? HttpStatus.valueOf(error.status()) : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(Map.of("error", error.code()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> unavailable(IllegalStateException error) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", error.getMessage()));
    }
}
