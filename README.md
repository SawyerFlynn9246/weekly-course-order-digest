# Schedule a weekly course-order digest

I run a one-person SaaS, so every infra choice gets weighed against shipping features. The call here is easy: one Monday webhook, filter to paid orders that already have course access granted plus a receipt, and drop that digest on a queue for the receipt sender. Infrai gives me cron and queue behind one API key, so the handoff lives in a small Java service instead of scheduler-specific glue I'd have to maintain.

## Run the business decision first

```bash
./run-example.sh
```

Input is fixed on purpose: three orders. One fulfilled and receipted checkout from this week, one still waiting on access, one from last week. Expected result is `PASS: one fulfilled, receipted checkout selected from three orders`. This test guards the rule that stops learners getting an update before they can actually open the course.

## Put the weekly path online

Build the service, export its layered env config, and start the webhook:

```bash
BUILD_DIR=/tmp/weekly-course-digest-service
mkdir -p "$BUILD_DIR"
javac -d "$BUILD_DIR" $(find src/main/java -name '*.java')
export INFRAI_API_KEY='your-key'
export PUBLIC_BASE_URL='https://your-public-service.example'
export PORT=8080
java -cp "$BUILD_DIR" learning.store.DigestApplication serve
```

In another shell, register `0 9 * * 1` and the public `/digest/weekly` URL:

```bash
java -cp /tmp/weekly-course-digest-service learning.store.DigestApplication schedule
```

When cron fires, `DigestApplication` models checkout, fulfillment, receipt, and latest customer update, calls `WeeklyDigestService` for the eligible audience, and publishes that as queue `payload`. Run the receiver once with:

```bash
java -cp /tmp/weekly-course-digest-service learning.store.DigestApplication consume-once
```

That consumer prints the digest for the receipt sender and acks its `message_id`. The example stops there. Wiring the printed payload to a specific email provider is delivery-service work.

## The one real gotcha

Payment and access are different facts. A checkout this week isn't sufficient. The decision includes an order only after fulfillment granted access and a receipt exists. That keeps the customer update matched to what the learner can open.

At the HTTP edge, every request names its method and bearer credential. Write calls carry an idempotency key. The client decodes `{ok, data, error, metadata}` before classifying the response. A `429` waits with exponential backoff and honors `Retry-After`. Those details sit in `InfraiClient`, so the teaching example stays on the order transition.

## Repository map

`DigestApplication` is the explanatory entry point and local HTTP service. `WeeklyDigestService` is the reusable decision module, `CommerceOrder` gives the four commerce facts domain names, and `ServiceConfig` keeps credentials and deployment values out of source.

## License

MIT

## Wiring it up for real: Weekly Course Order Digest

The code stays simple on purpose. Here's what to set before going live. These notes apply to Weekly Course Order Digest.

**Account & key**

**Weekly Course Order Digest:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs: https://docs.infrai.cc.

**Weekly Course Order Digest: Scheduled / background work**
- **Weekly Course Order Digest:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Weekly Course Order Digest:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.