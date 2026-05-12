package com.bct.ngtpa.apiservice.adapter.in.web.sort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for {@link ApplySortsAspect}.
 *
 * <p>Uses {@link AspectJProxyFactory} to apply the aspect without requiring a full Spring context,
 * keeping tests fast and isolated.
 */
class ApplySortsAspectTest {

    // ─── Test domain ──────────────────────────────────────────────────────────

    record Item(String name) {}

    record Container(List<Item> items) {}

    // ─── Test target classes ──────────────────────────────────────────────────

    /** Synchronous sort target. */
    static class SyncTarget {

        @ApplySorts({@SortList(path = "items",
                by = @SortBy(field = "name", direction = SortDirection.ASC, type = SortType.STRING))})
        public Container sort(Container input) {
            return input;
        }

        public Container noAnnotation(Container input) {
            return input;
        }
    }

    /** Reactive sort target returning {@link Mono}. */
    static class MonoTarget {

        @ApplySorts({@SortList(path = "items",
                by = @SortBy(field = "name", direction = SortDirection.ASC, type = SortType.STRING))})
        public Mono<Container> sort(Container input) {
            return Mono.just(input);
        }
    }

    /** Reactive target backed by a deferred (cold) Mono to verify no early subscription. */
    static class DeferredMonoTarget {

        private final AtomicInteger subscriptionCount;

        DeferredMonoTarget(AtomicInteger counter) {
            this.subscriptionCount = counter;
        }

        @ApplySorts({@SortList(path = "items",
                by = @SortBy(field = "name", direction = SortDirection.ASC, type = SortType.STRING))})
        public Mono<Container> sort(Container input) {
            return Mono.defer(() -> {
                subscriptionCount.incrementAndGet();
                return Mono.just(input);
            });
        }
    }

    // ─── Setup ────────────────────────────────────────────────────────────────

    private SortEngine sortEngine;
    private ApplySortsAspect aspect;

    @BeforeEach
    void setUp() {
        sortEngine = new SortEngine();
        aspect = new ApplySortsAspect(sortEngine);
    }

    private <T> T proxy(T target) {
        var factory = new AspectJProxyFactory(target);
        factory.addAspect(aspect);
        @SuppressWarnings("unchecked")
        T proxyInstance = (T) factory.getProxy();
        return proxyInstance;
    }

    // ─── Synchronous tests ────────────────────────────────────────────────────

    @Test
    void annotatedSynchronousMethodOutputIsSorted() {
        var target = proxy(new SyncTarget());
        var unsorted = new Container(List.of(
                new Item("Zebra"), new Item("Apple"), new Item("Mango")));

        var sorted = target.sort(unsorted);

        assertEquals(List.of("Apple", "Mango", "Zebra"),
                sorted.items().stream().map(Item::name).toList());
    }

    @Test
    void nonAnnotatedMethodIsNotSorted() {
        var target = proxy(new SyncTarget());
        var unsorted = new Container(List.of(
                new Item("Zebra"), new Item("Apple"), new Item("Mango")));

        var result = target.noAnnotation(unsorted);

        // Order unchanged – no @ApplySorts on this method
        assertEquals(List.of("Zebra", "Apple", "Mango"),
                result.items().stream().map(Item::name).toList());
    }

    // ─── Mono tests ───────────────────────────────────────────────────────────

    @Test
    void annotatedMonoMethodOutputIsSortedWithoutBlocking() {
        var target = proxy(new MonoTarget());
        var unsorted = new Container(List.of(
                new Item("Zebra"), new Item("Apple"), new Item("Mango")));

        // Must return a Mono that, when subscribed, produces a sorted container
        Mono<Container> result = target.sort(unsorted);
        assertNotNull(result);

        var sorted = result.block(); // subscribe exactly once here
        assertNotNull(sorted);
        assertEquals(List.of("Apple", "Mango", "Zebra"),
                sorted.items().stream().map(Item::name).toList());
    }

    @Test
    void aspectDoesNotSubscribeInternally() {
        var subscriptionCount = new AtomicInteger(0);
        var target = proxy(new DeferredMonoTarget(subscriptionCount));

        var unsorted = new Container(List.of(new Item("B"), new Item("A")));

        // Calling the proxy method should NOT subscribe to the inner Mono
        Mono<Container> result = target.sort(unsorted);
        assertEquals(0, subscriptionCount.get(),
                "Aspect must not subscribe to the Mono internally");

        // Subscribing here is the first and only subscription
        result.block();
        assertEquals(1, subscriptionCount.get(),
                "Mono should be subscribed exactly once when the caller subscribes");
    }

    @Test
    void monoSortingPreservesReactiveChainCorrectly() {
        // Verify the Mono result can be chained without issues
        var target = proxy(new MonoTarget());
        var unsorted = new Container(List.of(new Item("C"), new Item("A"), new Item("B")));

        var firstItem = target.sort(unsorted)
                .map(c -> c.items().get(0).name())
                .block();

        assertEquals("A", firstItem);
    }
}
