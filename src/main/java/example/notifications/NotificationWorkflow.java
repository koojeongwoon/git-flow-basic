package example.notifications;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** In-memory order status updates with invocation-time notification preferences. */
public final class NotificationWorkflow {
    public record Order(String accountId, String status) {}

    public record Mail(String accountId, String orderId, String status) {}

    private static final Set<String> NOTIFICATION_STATUSES =
            Set.of("SHIPPED", "DELIVERED", "CANCELLED");

    private final Map<String, Boolean> preferences;
    private final Map<String, Order> orders;
    private final Consumer<Mail> sender;

    public NotificationWorkflow(Map<String, Boolean> preferences,
                                Map<String, Order> orders,
                                Consumer<Mail> sender) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.orders = Objects.requireNonNull(orders, "orders");
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    /**
     * Captures the order and its owner's preference before invoking the callback.
     * Preference changes made by the callback take effect on subsequent calls only.
     */
    public void changeStatus(String orderId, String newStatus, Runnable afterSnapshot) {
        Order original = Objects.requireNonNull(orders.get(orderId), "Order not found");
        String accountId = original.accountId();
        String previousStatus = original.status();
        boolean enabled = Boolean.TRUE.equals(preferences.get(accountId));

        afterSnapshot.run();

        orders.put(orderId, new Order(accountId, newStatus));
        if (enabled
                && !Objects.equals(previousStatus, newStatus)
                && NOTIFICATION_STATUSES.contains(newStatus)) {
            sender.accept(new Mail(accountId, orderId, newStatus));
        }
    }
}
