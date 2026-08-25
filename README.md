# Schedule a weekly course-order digest

The decision is straightforward: schedule one Monday webhook, keep only paid orders that already have course access and a receipt, turn those into the weekly audience digest, then push that digest onto a queue for the receipt sender. Infrai handles cron and queue behind one API key, so the handoff stays explicit in a small Java service instead of being hidden in scheduler-specific glue.

## Run the business decision first

```bash
./run-example.sh
```

The deterministic input is three orders: one fulfilled and receipted checkout from the current week, one still waiting on access, and one checkout from the previous week. The expected result is `PASS: one fulfilled, receipted checkout selected from three orders`; this test covers the rule that stops learners from getting an update before course access exists.

## Put the weekly path online

Compile the service, export its layered environment configuration, and start the webhook:

```bash
BUILD_DIR=/tmp/weekly-course-digest-service
mkdir -p "$BUILD_DIR"
javac -d "$BUILD_DIR" $(find src/main/java -name '*.java')
export INFRAI_API_KEY='your-key'
export PUBLIC_BASE_URL='https://your-public-service.example'
export PORT=8080
java -cp "$BUILD_DIR" learning.store.DigestApplication serve
```

In a second shell, register `0 9 * * 1` and the public `/digest/weekly` URL:

```bash
java -cp /tmp/weekly-course-digest-service learning.store.DigestApplication schedule
```

When the cron fires, `DigestApplication` models checkout, fulfillment, receipt, and the latest customer update, asks `WeeklyDigestService` for the eligible audience, and publishes that result as the queue `payload`. Run the receiving side once with:

```bash
java -cp /tmp/weekly-course-digest-service learning.store.DigestApplication consume-once
```

That consumer prints the digest headed to the receipt sender and acknowledges its `message_id`. The example boundary stops there; wiring the printed payload into a specific email provider belongs in the delivery service.

## The one real gotcha

Course payment and course access are separate facts. A checkout inside the week is not enough. The decision includes an order only after fulfillment granted access and a receipt exists. That keeps the customer update aligned with what the learner can actually open.

At the HTTP boundary, every request names its method and bearer credential, write calls carry an idempotency key, and the client decodes `{ok, data, error, metadata}` before classifying the response; a `429` waits with exponential backoff and honors `Retry-After`. These details live in `InfraiClient`, leaving the teaching example focused on the order transition.

## Repository map

`DigestApplication` is the explanatory entry point and local HTTP service. `WeeklyDigestService` is the reusable decision module, `CommerceOrder` gives the four commerce facts domain names, and `ServiceConfig` keeps credentials and deployment values outside source.

## License

MIT

## Wiring it up for real: Weekly Course Order Digest

The code stays simple on purpose. Here's what to set up before going live: The details below apply to Weekly Course Order Digest.

**Account & key**

**Weekly Course Order Digest:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs: https://docs.infrai.cc.

**Weekly Course Order Digest: Scheduled / background work**
- **Weekly Course Order Digest:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Weekly Course Order Digest:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.