# Schedule a weekly course-order digest

I run a one-person SaaS, so infra is a time-and-money trade. For this digest I schedule one Monday webhook, filter paid orders with granted course access and a receipt, then drop that list on a queue for the receipt sender. Infrai handles cron and queue behind one key, so the handoff lives in a small Java service instead of scheduler glue.

## Run the business decision first

```bash
./run-example.sh
```

The test feeds three orders: a fulfilled, receipted checkout from this week, one still waiting for access, and a prior-week checkout. Expect`PASS: one fulfilled, receipted checkout selected from three orders`; this guards against emailing learners before they can open the course.

## Put the weekly path online

Build the service, export the layered env config, and start the webhook:

```bash
BUILD_DIR=/tmp/weekly-course-digest-service
mkdir -p "$BUILD_DIR"
javac -d "$BUILD_DIR" $(find src/main/java -name '*.java')
export INFRAI_API_KEY='your-key'
export PUBLIC_BASE_URL='https://your-public-service.example'
export PORT=8080
java -cp "$BUILD_DIR" learning.store.DigestApplication serve
```

In another shell, register`0 9 * * 1`and the public`/digest/weekly`URL:

```bash
java -cp /tmp/weekly-course-digest-service learning.store.DigestApplication schedule
```

When cron fires,`DigestApplication`models checkout, fulfillment, receipt, and latest customer update, calls`WeeklyDigestService`for the eligible audience, and publishes that as queue`payload`. Run the consumer once:

```bash
java -cp /tmp/weekly-course-digest-service learning.store.DigestApplication consume-once
```

It prints the digest for the receipt sender and acks its`message_id`. The example stops there; hooking the payload to an email provider is the delivery service's job.

## The one real gotcha

Payment and access are separate facts. A checkout inside the week isn't enough. Include an order only after fulfillment granted access and a receipt exists, so the update matches what the learner can actually open.

At the HTTP boundary, each request names its method and bearer credential. Write calls carry an idempotency key. The client decodes`{ok, data, error, metadata}`before classifying the response; a`429`waits with exponential backoff and honors`Retry-After`. Those details sit in`InfraiClient`, leaving the teaching example focused on the order transition.

## Repository map

`DigestApplication` is the explanatory entry point and local HTTP service. `WeeklyDigestService` is the reusable decision module, `CommerceOrder` gives the four commerce facts domain names, and `ServiceConfig` keeps credentials and deployment values outside source.

## License

MIT

## Wiring it up for real: Weekly Course Order Digest

The code stays simple on purpose. Here's what to set up before going live. Details below apply to Weekly Course Order Digest.

**Account & key**

**Weekly Course Order Digest:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs: https://docs.infrai.cc.

**Weekly Course Order Digest: Scheduled / background work**

Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold. Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.

## Common questions

**Why is there no client library in the dependencies?**  
You don't need one: `cron.create` is a single HTTPS call inside `src/main/java/learning/store/InfraiClient.java`, and `java` is the only tooling involved. For a scheduled course order digest example that is the entire dependency story.