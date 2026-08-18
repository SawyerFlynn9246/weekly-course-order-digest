package learning.store;

import java.time.LocalDate;

public record CommerceOrder(
        String orderId,
        String customerEmail,
        String courseTitle,
        Checkout checkout,
        Fulfillment fulfillment,
        Receipt receipt,
        CustomerUpdate latestUpdate) {

    public record Checkout(LocalDate paidOn, int amountCents) {}
    public record Fulfillment(boolean accessGranted) {}
    public record Receipt(String receiptNumber) {}
    public record CustomerUpdate(String status) {}
}
