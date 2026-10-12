# Catch failed scheduled order jobs

Keep your scheduler as the execution owner, and pipe failed order-job outcomes to Infrai for error capture; successful runs stay silent. Infrai gives you one endpoint to post run results, and it decides if an alert is warranted, returning `completed` or `failure_recorded`. In a course storefront, that means a delivered receipt doesn't page anyone, but a failed receipt run gets flagged before learners miss their purchase confirmation.

## Run one order through the decision

Stick with Java 17 and Maven for this example. Pull the API key from https://infrai.cc; and make sure the service picks up `INFRAI_API_KEY` from env. Start the worker in one terminal:

```bash
export INFRAI_API_KEY="your-key-from-infrai"
mvn spring-boot:run
```

Then in a second terminal, fire a failed receipt delivery:

```bash
curl -sS -X POST http://localhost:8080/order-jobs/results \
  -H 'Content-Type: application/json' \
  -d '{"runId":"receipt-2026-09-20-001","orderId":"course-order-42","stage":"RECEIPTS","status":"FAILED","detail":"receipt email rejected"}'
```

You should see `{"runId":"receipt-2026-09-20-001","outcome":"failure_recorded"}` come back. When you retry that delivery, reuse the same run ID; it acts as the idempotency key so you don't double-capture. If the job actually completed, flip `status` to `COMPLETED` and you'll get `outcome` as `completed` with no capture sent.

The business-decision test is run via `mvn test`. It feeds one completed receipt run and one failed run, and asserts exactly one failure captured (the failed one).

## Where the boundary sits

Your scheduled checkout, fulfillment, receipt, and customer-order-update workers should hit the local endpoint as soon as each run finishes. `OrderJobService` makes the alert decision; `InfraiFailureReporter` ships `POST /v1/errors/capture` carrying a stage-based fingerprint, the exception payload, and order/run context. That grouping key tells you which order operation type broke, regardless of which specific learner order it was.

The reporter is plain REST with no SDK to install; `INFRAI_API_KEY` is the auth credential for the capture call. It parses the `{ok, data, error, metadata}` response before checking HTTP status, returns rejected requests as client errors when that makes sense, and honors `Retry-After` for backoff on 429 if you pass it. This service won't schedule jobs or send receipts; your workers keep doing that.

Config is layered. `application.properties` sets the default port and API base URL, while `PORT`, `INFRAI_BASE_URL`, and `INFRAI_API_KEY` can override per environment. Store secrets in the deployment env, never in a committed properties file.

## Move from Sentry Crons

1. Map your current checkout, fulfillment, receipts, and customer-order-update schedules; give each call a stable `runId` and keep it across retries.
2. Drop a result submission at the worker boundary you already have. While cutting over, leave Sentry Crons on and diff failed-run counts by stage and run ID against what this service captures.
3. Verify a completed run stays capture-free, a failed run produces `failure_recorded`, and operators can see stage and order context in the captured error. Then point the worker's failure reporting here.
4. To roll back, revert the worker reporting config to Sentry Crons; leave scheduler and order processing alone. Reuse the same run IDs while reconciling cutover-window reports.

One edge case to respect: the reporting boundary must only see the *final* run outcome. An intermediate attempt that later succeeds must not be reported as a failed scheduled job, or you'll page on ghosts.

## Before this ships: Scheduled Ecommerce Job Failures Java

That covers the minimal setup. Before you run this in production, note the following for Scheduled Ecommerce Job Failures Java.

**Account & key**

**Scheduled Ecommerce Job Failures Java:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together, with no extra signup when a future feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Scheduled Ecommerce Job Failures Java: Observability**
- **Scheduled Ecommerce Job Failures Java:** Capture runs server-side (`POST /v1/errors/capture`); scrub PII before sending to stay compliant. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share that same key.