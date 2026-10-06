package example.orders;

import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrderJobService {
    public enum Stage { CHECKOUT, FULFILLMENT, RECEIPTS, CUSTOMER_ORDER_UPDATES }
    public enum Status { COMPLETED, FAILED }
    public record JobResult(String runId, String orderId, Stage stage, Status status, String detail) {}
    public record Decision(String runId, String outcome) {}

    interface FailureReporter { void capture(JobResult result); }
    private final FailureReporter reporter;

    @Autowired
    public OrderJobService(InfraiFailureReporter reporter) { this.reporter = reporter; }
    OrderJobService(FailureReporter reporter) { this.reporter = reporter; }

    public Decision record(JobResult result) {
        if (result == null || blank(result.runId()) || blank(result.orderId()) || result.stage() == null || result.status() == null)
            throw new IllegalArgumentException("runId, orderId, stage and status are required");
        if (result.status() == Status.COMPLETED) return new Decision(result.runId(), "completed");
        reporter.capture(result);
        return new Decision(result.runId(), "failure_recorded");
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
