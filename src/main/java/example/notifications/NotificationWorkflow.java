package example.notifications;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * In-memory order status updates using invocation-time notification snapshots.
 *
 * <p>The supplied maps are shared with the caller, not copied. Each status-change
 * invocation reads the existing order and its owner's notification preference
 * before invoking its callback. That snapshot fixes the owner, previous status,
 * and notification eligibility for the current invocation. Only an explicit
 * {@code true} preference enables notifications; missing and null values disable
 * them.
 *
 * <p>Preference changes or deletions made by the callback cannot change the
 * current invocation's notification decision. The next invocation reads the
 * shared maps again and therefore observes those updated preferences. This
 * workflow is intended for single-threaded use; snapshotting does not provide
 * synchronization for concurrent map access.
 */
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
     * Updates an existing order using a snapshot captured before the callback.
     *
     * <p>After reading the order and its owner's preference, invokes
     * {@code afterSnapshot} exactly once, before writing the new order status.
     * The update preserves the snapshot's owner. A mail is then delivered to
     * {@code sender} exactly once only if the snapshot preference was true,
     * the status changed, and the destination is SHIPPED, DELIVERED, or CANCELLED.
     * Non-notifying requests still update the shared orders map.
     *
     * <p>Enabling, disabling, or removing a preference in the callback affects
     * subsequent calls only, not this call's snapshot-based decision. For
     * example, disabling a true preference in the callback does not suppress
     * this call's otherwise eligible mail, but the next call reads it as disabled.
     *
     * @param orderId identifier of the existing order
     * @param newStatus requested destination status
     * @param afterSnapshot non-null callback executed after snapshot capture and
     *                      before the status update; expected to return normally
     */
    public void changeStatus(String orderId, String newStatus, Runnable afterSnapshot) {
        Order original = Objects.requireNonNull(orders.get(orderId), "Order not found");
        String accountId = original.accountId();
        String previousStatus = original.status();
        boolean enabled = Boolean.TRUE.equals(preferences.get(accountId));

        afterSnapshot.run();

        orders.put(orderId, new Order(accountId, newStatus));
        if (shouldNotify(enabled, previousStatus, newStatus)) {
            sender.accept(new Mail(accountId, orderId, newStatus));
        }
    }

    private static boolean shouldNotify(boolean optedIn, String previousStatus, String newStatus) {
        return optedIn
                && !Objects.equals(previousStatus, newStatus)
                && NOTIFICATION_STATUSES.contains(newStatus);
    }
}
