package example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderJobServiceTest {
    @Test void onlyFailedRunsAreCaptured() {
        List<OrderJobService.JobResult> captured = new ArrayList<>();
        OrderJobService service = new OrderJobService(captured::add);
        var completed = new OrderJobService.JobResult("run-1", "order-7", OrderJobService.Stage.RECEIPTS, OrderJobService.Status.COMPLETED, null);
        var failed = new OrderJobService.JobResult("run-2", "order-7", OrderJobService.Stage.RECEIPTS, OrderJobService.Status.FAILED, "receipt delivery rejected");
        assertEquals("completed", service.record(completed).outcome());
        assertEquals("failure_recorded", service.record(failed).outcome());
        assertEquals(List.of(failed), captured);
    }
}
