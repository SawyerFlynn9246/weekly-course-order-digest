package learning.store;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WeeklyDigestService {
    public Map<String, Object> decideDigest(String audience, LocalDate weekStart, List<CommerceOrder> orders) {
        LocalDate weekEnd = weekStart.plusDays(7);
        List<Map<String, Object>> included = orders.stream()
                .filter(order -> !order.checkout().paidOn().isBefore(weekStart))
                .filter(order -> order.checkout().paidOn().isBefore(weekEnd))
                .filter(order -> order.fulfillment().accessGranted())
                .filter(order -> order.receipt() != null)
                .map(this::digestLine)
                .toList();

        Map<String, Object> digest = new LinkedHashMap<>();
        digest.put("audience", audience);
        digest.put("week_start", weekStart.toString());
        digest.put("fulfilled_orders", included.size());
        digest.put("orders", included);
        return digest;
    }

    private Map<String, Object> digestLine(CommerceOrder order) {
        return Map.of(
                "order_id", order.orderId(),
                "customer_email", order.customerEmail(),
                "course_title", order.courseTitle(),
                "amount_cents", order.checkout().amountCents(),
                "receipt_number", order.receipt().receiptNumber(),
                "customer_update", order.latestUpdate().status());
    }
}
