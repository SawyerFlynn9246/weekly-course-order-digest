package learning.store;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class WeeklyDigestServiceTest {
    public static void main(String[] args) {
        LocalDate monday = LocalDate.of(2026, 8, 17);
        CommerceOrder ready = order("ready", monday.plusDays(1), true, true);
        CommerceOrder awaitingAccess = order("waiting", monday.plusDays(2), false, true);
        CommerceOrder old = order("old", monday.minusDays(1), true, true);

        Map<String, Object> digest = new WeeklyDigestService()
                .decideDigest("course-buyers", monday, List.of(ready, awaitingAccess, old));

        check(digest.get("fulfilled_orders").equals(1), "only the fulfilled order belongs in the digest");
        List<?> lines = (List<?>) digest.get("orders");
        check(((Map<?, ?>) lines.get(0)).get("order_id").equals("ready"), "the ready order is selected");
        System.out.println("PASS: one fulfilled, receipted checkout selected from three orders");
    }

    private static CommerceOrder order(String id, LocalDate paidOn, boolean access, boolean hasReceipt) {
        return new CommerceOrder(id, id + "@example.com", "Geometry Foundations",
                new CommerceOrder.Checkout(paidOn, 3900),
                new CommerceOrder.Fulfillment(access),
                hasReceipt ? new CommerceOrder.Receipt("R-" + id) : null,
                new CommerceOrder.CustomerUpdate(access ? "access delivered" : "preparing access"));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
