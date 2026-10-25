package example.notifications;

import example.notifications.NotificationWorkflow.Mail;
import example.notifications.NotificationWorkflow.Order;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

class NotificationWorkflowTest {
    private static final List<String> TARGETS = List.of("SHIPPED", "DELIVERED", "CANCELLED");
    private static final List<String> NON_TARGETS = List.of("CREATED", "PAID", "PROCESSING");
    private static final List<String> ALL_STATUSES = List.of(
            "CREATED", "PAID", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED");
    private enum Preference { TRUE, FALSE, MISSING, NULL }

    // Fixture wiring only: all behavior under test belongs to the real workflow.
    private static final class Fixture {
        final HashMap<String, Boolean> preferences = new HashMap<>();
        final HashMap<String, Order> orders = new HashMap<>();
        final ArrayList<Mail> mails = new ArrayList<>();
        final NotificationWorkflow workflow;

        Fixture(Preference preference, String initialStatus) {
            switch (preference) {
                case TRUE -> preferences.put("A", true);
                case FALSE -> preferences.put("A", false);
                case NULL -> preferences.put("A", null);
                case MISSING -> { }
            }
            orders.put("order_a", new Order("A", initialStatus));
            workflow = new NotificationWorkflow(preferences, orders, mails::add);
        }
    }

    private static void assertOrder(Fixture f, String status) {
        assertEquals(new Order("A", status), f.orders.get("order_a"));
    }

    private static void assertOnlyMail(Fixture f, String status) {
        assertEquals(List.of(new Mail("A", "order_a", status)), f.mails);
    }

    // tc_public_api_contract
    @Test
    void publicApiContract() {
        HashMap<String, Boolean> preferences = new HashMap<>();
        HashMap<String, Order> orders = new HashMap<>();
        ArrayList<Mail> mails = new ArrayList<>();
        preferences.put("A", true);
        orders.put("order_a", new Order("A", "PAID"));
        NotificationWorkflow workflow = new NotificationWorkflow(preferences, orders, mails::add);

        workflow.changeStatus("order_a", "SHIPPED", () -> { });

        assertEquals(new Order("A", "SHIPPED"), orders.get("order_a"));
        assertEquals(List.of(new Mail("A", "order_a", "SHIPPED")), mails);
    }

    // tc_send_each_target_status
    @TestFactory
    List<DynamicTest> sendsEachTargetStatus() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String target : TARGETS) {
            tests.add(dynamicTest(target, () -> {
                Fixture f = new Fixture(Preference.TRUE, "PAID");
                f.workflow.changeStatus("order_a", target, () -> { });
                assertOrder(f, target);
                assertOnlyMail(f, target);
            }));
        }
        return tests;
    }

    // tc_disabled_preference_matrix
    @TestFactory
    List<DynamicTest> disabledPreferencesSuppressEveryTarget() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Preference preference : List.of(Preference.MISSING, Preference.NULL, Preference.FALSE)) {
            for (String target : TARGETS) {
                tests.add(dynamicTest(preference + " -> " + target, () -> {
                    Fixture f = new Fixture(preference, "PAID");
                    f.workflow.changeStatus("order_a", target, () -> { });
                    assertOrder(f, target);
                    assertEquals(List.of(), f.mails);
                }));
            }
        }
        return tests;
    }

    // tc_same_status_no_mail
    @TestFactory
    List<DynamicTest> sameTargetStatusDoesNotSend() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String target : TARGETS) {
            tests.add(dynamicTest(target, () -> {
                Fixture f = new Fixture(Preference.TRUE, target);
                f.workflow.changeStatus("order_a", target, () -> { });
                assertOrder(f, target);
                assertEquals(List.of(), f.mails);
            }));
        }
        return tests;
    }

    // tc_valid_non_target_status
    @TestFactory
    List<DynamicTest> validNonTargetsUpdateWithoutMail() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String target : NON_TARGETS) {
            tests.add(dynamicTest(target, () -> {
                Fixture f = new Fixture(Preference.TRUE, "SHIPPED");
                f.workflow.changeStatus("order_a", target, () -> { });
                assertOrder(f, target);
                assertEquals(List.of(), f.mails);
            }));
        }
        return tests;
    }

    // tc_callback_once_before_update: 4 preferences x 6 destinations x 2 prior-state cases.
    @TestFactory
    List<DynamicTest> callbackRunsExactlyOnceBeforeUpdateForEveryCondition() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Preference preference : Preference.values()) {
            for (String target : ALL_STATUSES) {
                for (boolean same : List.of(false, true)) {
                    String initial = same ? target : (target.equals("PAID") ? "CREATED" : "PAID");
                    tests.add(dynamicTest(preference + " " + initial + " -> " + target, () -> {
                        Fixture f = new Fixture(preference, initial);
                        Order original = new Order("A", initial);
                        AtomicInteger callbacks = new AtomicInteger();

                        f.workflow.changeStatus("order_a", target, () -> {
                            assertEquals(1, callbacks.incrementAndGet(), "callback invocation ordinal");
                            assertEquals(original, f.orders.get("order_a"), "update must follow callback");
                        });

                        assertEquals(1, callbacks.get());
                        assertOrder(f, target);
                        if (preference == Preference.TRUE && !same && TARGETS.contains(target)) {
                            assertOnlyMail(f, target);
                        } else {
                            assertEquals(List.of(), f.mails);
                        }
                    }));
                }
            }
        }
        return tests;
    }

    // tc_snapshot_true_to_false
    @Test
    void trueSnapshotSurvivesDisablingAndNextCallReadsFalse() {
        Fixture f = new Fixture(Preference.TRUE, "PAID");
        f.workflow.changeStatus("order_a", "SHIPPED", () -> f.preferences.put("A", false));

        assertEquals(Boolean.FALSE, f.preferences.get("A"));
        assertOrder(f, "SHIPPED");
        assertOnlyMail(f, "SHIPPED");

        f.workflow.changeStatus("order_a", "DELIVERED", () -> { });

        assertOrder(f, "DELIVERED");
        assertOnlyMail(f, "SHIPPED");
    }

    // tc_snapshot_disabled_to_true
    @TestFactory
    List<DynamicTest> disabledSnapshotSurvivesEnablingAndNextCallReadsTrue() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Preference preference : List.of(Preference.FALSE, Preference.MISSING, Preference.NULL)) {
            tests.add(dynamicTest(preference.toString(), () -> {
                Fixture f = new Fixture(preference, "PAID");
                f.workflow.changeStatus("order_a", "SHIPPED", () -> f.preferences.put("A", true));

                assertEquals(Boolean.TRUE, f.preferences.get("A"));
                assertOrder(f, "SHIPPED");
                assertEquals(List.of(), f.mails);

                f.workflow.changeStatus("order_a", "DELIVERED", () -> { });

                assertOrder(f, "DELIVERED");
                assertOnlyMail(f, "DELIVERED");
            }));
        }
        return tests;
    }

    // tc_account_delivery_isolation
    @TestFactory
    List<DynamicTest> deliveryIsIsolatedToEnabledOwner() {
        List<DynamicTest> tests = new ArrayList<>();
        for (boolean aEnabled : List.of(true, false)) {
            for (String target : TARGETS) {
                tests.add(dynamicTest("A enabled=" + aEnabled + " -> " + target, () -> {
                    HashMap<String, Boolean> preferences = new HashMap<>();
                    preferences.put("A", aEnabled);
                    preferences.put("B", !aEnabled);
                    HashMap<String, Order> orders = new HashMap<>();
                    orders.put("order_a", new Order("A", "PAID"));
                    orders.put("order_b", new Order("B", "PAID"));
                    ArrayList<Mail> mails = new ArrayList<>();
                    NotificationWorkflow workflow = new NotificationWorkflow(preferences, orders, mails::add);

                    workflow.changeStatus("order_a", target, () -> { });
                    assertEquals(new Order("A", target), orders.get("order_a"));
                    assertEquals(new Order("B", "PAID"), orders.get("order_b"));
                    assertEquals(aEnabled ? List.of(new Mail("A", "order_a", target)) : List.of(), mails);

                    workflow.changeStatus("order_b", target, () -> { });

                    assertEquals(new Order("A", target), orders.get("order_a"));
                    assertEquals(new Order("B", target), orders.get("order_b"));
                    Mail expected = aEnabled
                            ? new Mail("A", "order_a", target)
                            : new Mail("B", "order_b", target);
                    assertEquals(List.of(expected), mails);
                }));
            }
        }
        return tests;
    }

    // tc_repeat_target_request
    @TestFactory
    List<DynamicTest> repeatedTargetRequestDoesNotSendAgain() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String target : TARGETS) {
            tests.add(dynamicTest(target, () -> {
                Fixture f = new Fixture(Preference.TRUE, "PAID");
                f.workflow.changeStatus("order_a", target, () -> { });
                assertOrder(f, target);
                assertOnlyMail(f, target);
                List<Mail> afterFirst = List.copyOf(f.mails);
                AtomicInteger secondCallbacks = new AtomicInteger();

                f.workflow.changeStatus("order_a", target, () -> {
                    assertEquals(1, secondCallbacks.incrementAndGet());
                    assertOrder(f, target);
                });

                assertOrder(f, target);
                assertEquals(afterFirst, f.mails);
                assertEquals(1, secondCallbacks.get());
            }));
        }
        return tests;
    }

    // tc_snapshot_true_then_delete
    @Test
    void trueSnapshotSurvivesDeletionAndNextCallReadsMissingPreference() {
        Fixture f = new Fixture(Preference.TRUE, "PAID");
        f.workflow.changeStatus("order_a", "SHIPPED", () -> f.preferences.remove("A"));

        assertFalse(f.preferences.containsKey("A"));
        assertOrder(f, "SHIPPED");
        assertOnlyMail(f, "SHIPPED");

        f.workflow.changeStatus("order_a", "DELIVERED", () -> { });

        assertOrder(f, "DELIVERED");
        assertOnlyMail(f, "SHIPPED");
    }

    // tc_other_true_owner_disabled
    @TestFactory
    List<DynamicTest> anotherEnabledAccountCannotEnableOrReceiveOwnersMail() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Preference preference : List.of(Preference.MISSING, Preference.NULL, Preference.FALSE)) {
            for (String target : TARGETS) {
                tests.add(dynamicTest(preference + " with B enabled -> " + target, () -> {
                    Fixture f = new Fixture(preference, "PAID");
                    // Shared preferences must be read on invocation, not captured at construction.
                    f.preferences.put("B", true);

                    f.workflow.changeStatus("order_a", target, () -> { });

                    assertOrder(f, target);
                    assertEquals(Boolean.TRUE, f.preferences.get("B"));
                    assertEquals(List.of(), f.mails);
                }));
            }
        }
        return tests;
    }
}
