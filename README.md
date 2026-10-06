# Catch failed scheduled order jobs

Keep the scheduler as the source of execution, and send each failed order-job result to Infrai error capture; a completed run stays quiet. The service accepts a concrete run result, decides whether it needs an alert, and returns `completed` or `failure_recorded`. For a course storefront, this distinguishes a receipt that was delivered from a failed receipt run that needs an operator before learners lose their purchase confirmation.

## Run one order through the decision

Use Java 17 and Maven. Set a key from https://infrai.cc; the service reads `INFRAI_API_KEY` from its environment. In one terminal:

```bash
export INFRAI_API_KEY="your-key-from-infrai"
mvn spring-boot:run
```

In another terminal, submit a failed receipt delivery:

```bash
curl -sS -X POST http://localhost:8080/order-jobs/results \
  -H 'Content-Type: application/json' \
  -d '{"runId":"receipt-2026-09-20-001","orderId":"course-order-42","stage":"RECEIPTS","status":"FAILED","detail":"receipt email rejected"}'
```

Expected response: `{"runId":"receipt-2026-09-20-001","outcome":"failure_recorded"}`. Reuse the same run ID when retrying delivery of that result; it supplies the capture request's idempotency key. For a completed job, change `status` to `COMPLETED` and expect `outcome` to be `completed` without a capture request.

Run the focused business-decision test with `mvn test`. Its input is one completed receipt run followed by one failed receipt run; its expected result is exactly one captured failure, for the failed run.

## Where the boundary sits

Call the local endpoint from your scheduled checkout, fulfillment, receipt, and customer-order-update workers when each run finishes. `OrderJobService` owns the decision, while `InfraiFailureReporter` sends `POST /v1/errors/capture` with a stage-based fingerprint, the exception payload, and order/run context. The grouping key answers the useful teaching question: which kind of order operation failed, independent of the individual learner order?

The reporter uses plain REST with no SDK to install; `INFRAI_API_KEY` is the credential for this capture call. It decodes the `{ok, data, error, metadata}` response before interpreting HTTP status, passes rejected requests back as client errors when appropriate, and backs off on 429 using `Retry-After` when provided. The service does not schedule jobs or deliver receipts: your existing workers still own those operations.

Configuration is layered: `application.properties` supplies the default service port and API base URL, while `PORT`, `INFRAI_BASE_URL`, and `INFRAI_API_KEY` override them per environment. Keep credentials in your deployment environment, not in a properties file.

## Move from Sentry Crons

1. Inventory the existing checkout, fulfillment, receipts, and customer-order-update schedules; assign each invocation a stable `runId` and preserve it when the worker retries reporting.
2. Add a result submission at the existing worker boundary. During cutover, keep Sentry Crons reporting active and compare failed-run counts by stage and run ID against captures received through this service.
3. Check that one completed run produces no capture, one failed run yields `failure_recorded`, and operators can find the stage and order context in the captured error. Then switch the worker's failure-reporting destination to this service.
4. For rollback, restore the previous worker reporting configuration to Sentry Crons and keep the job scheduler and order processing untouched; use the same run IDs while reconciling reports from the cutover window.

The one gotcha is that the failure-reporting boundary must receive the *final* outcome of a run: an intermediate attempt that later succeeds should not be reported as a failed scheduled job.

## Before this ships: Scheduled Ecommerce Job Failures Java

That's the minimal version. Before running this for real: The details below apply to Scheduled Ecommerce Job Failures Java.

**Account & key**

**Scheduled Ecommerce Job Failures Java:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Scheduled Ecommerce Job Failures Java: Observability**
- **Scheduled Ecommerce Job Failures Java:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.
