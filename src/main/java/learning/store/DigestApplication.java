package learning.store;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

public final class DigestApplication {
    private final ServiceConfig config;
    private final InfraiClient infrai;
    private final WeeklyDigestService digests = new WeeklyDigestService();

    private DigestApplication(ServiceConfig config) {
        this.config = config;
        this.infrai = new InfraiClient(config.apiKey());
    }

    public static void main(String[] args) throws IOException {
        ServiceConfig config = ServiceConfig.fromEnvironment();
        DigestApplication application = new DigestApplication(config);
        String command = args.length == 0 ? "serve" : args[0];
        switch (command) {
            case "schedule" -> application.schedule();
            case "consume-once" -> application.consumeOnce();
            case "serve" -> application.serve();
            default -> throw new IllegalArgumentException("Use schedule, serve, or consume-once");
        }
    }

    private void schedule() {
        String task = config.publicBaseUrl() + "/digest/weekly";
        String jobId = infrai.createWeeklyCron("0 9 * * 1", task);
        System.out.println("Weekly course-order digest scheduled: " + jobId);
    }

    private void serve() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.createContext("/digest/weekly", this::handleWeeklyDigest);
        server.start();
        System.out.println("Digest webhook listening on port " + config.port());
    }

    private void handleWeeklyDigest(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            reply(exchange, 405, Map.of("message", "POST required"));
            return;
        }
        try {
            LocalDate weekStart = LocalDate.now(ZoneOffset.UTC)
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            Map<String, Object> digest = digests.decideDigest("course-buyers", weekStart, sampleOrders(weekStart));
            infrai.publishDigest(digest, "weekly-course-digest-" + weekStart);
            reply(exchange, 202, Map.of("queued", true, "week_start", weekStart.toString()));
        } catch (InfraiException error) {
            int status = error.status() >= 400 && error.status() < 500 ? error.status() : 502;
            reply(exchange, status, Map.of("code", error.code(), "message", error.getMessage()));
        }
    }

    private void consumeOnce() {
        for (Map<String, Object> message : infrai.consume(10, 60)) {
            System.out.println("Digest ready for receipt sender: " + Json.write(message.get("payload")));
            Object id = message.get("message_id");
            if (id instanceof String messageId) infrai.acknowledge(messageId);
        }
    }

    private static List<CommerceOrder> sampleOrders(LocalDate weekStart) {
        return List.of(new CommerceOrder(
                "order-course-1042", "learner@example.com", "Practical Algebra",
                new CommerceOrder.Checkout(weekStart.plusDays(1), 4900),
                new CommerceOrder.Fulfillment(true),
                new CommerceOrder.Receipt("R-1042"),
                new CommerceOrder.CustomerUpdate("course access delivered")));
    }

    private static void reply(HttpExchange exchange, int status, Map<String, Object> body) throws IOException {
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
