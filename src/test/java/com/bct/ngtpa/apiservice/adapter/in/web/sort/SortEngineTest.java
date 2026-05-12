package com.bct.ngtpa.apiservice.adapter.in.web.sort;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SortEngine}.
 *
 * <p>Uses lightweight test record types defined as inner classes to cover all supported
 * sort types, path resolution, null handling, and record rebuilding scenarios.
 *
 * <p>Annotation configurations are declared as annotated static placeholder methods so that
 * the tests consume real {@link ApplySorts} annotation instances obtained via reflection.
 */
class SortEngineTest {

    private final SortEngine engine = new SortEngine();

    // ─── Test domain records ──────────────────────────────────────────────────

    record Item(String name, String date, Integer seq, BigDecimal amount, Boolean flag) {}

    record Container(List<Item> items) {}

    record Nested(Container data) {}

    // ─── Annotation holder methods (bodies are irrelevant – only annotations are used) ──

    @ApplySorts({@SortList(path = "items",
            by = @SortBy(field = "name", direction = SortDirection.ASC, type = SortType.STRING))})
    static void nameAsc() {}

    @ApplySorts({@SortList(path = "items",
            by = @SortBy(field = "name", direction = SortDirection.DESC, type = SortType.STRING))})
    static void nameDesc() {}

    @ApplySorts({@SortList(path = "items",
            by = @SortBy(field = "seq", direction = SortDirection.ASC, type = SortType.NUMBER))})
    static void seqAsc() {}

    @ApplySorts({@SortList(path = "items",
            by = @SortBy(field = "seq", direction = SortDirection.DESC, type = SortType.NUMBER))})
    static void seqDesc() {}

    @ApplySorts({@SortList(path = "items",
            by = @SortBy(field = "amount", direction = SortDirection.ASC, type = SortType.NUMBER))})
    static void amountAsc() {}

    @ApplySorts({@SortList(path = "items",
            by = @SortBy(field = "date", direction = SortDirection.DESC,
                    type = SortType.DATE, datePattern = "dd/MM/yyyy"))})
    static void dateDesc() {}

    @ApplySorts({@SortList(path = "items",
            by = @SortBy(field = "date", direction = SortDirection.ASC,
                    type = SortType.DATE, datePattern = "dd/MM/yyyy"))})
    static void dateAsc() {}

    @ApplySorts({@SortList(path = "items", by = {
            @SortBy(field = "date", direction = SortDirection.DESC,
                    type = SortType.DATE, datePattern = "dd/MM/yyyy"),
            @SortBy(field = "name", direction = SortDirection.ASC, type = SortType.STRING)})})
    static void dateDescNameAsc() {}

    @ApplySorts({@SortList(path = "items",
            by = @SortBy(field = "name", direction = SortDirection.ASC, type = SortType.STRING,
                    nullsLast = true))})
    static void nameAscNullsLast() {}

    @ApplySorts({@SortList(path = "items",
            by = @SortBy(field = "date", direction = SortDirection.DESC,
                    type = SortType.DATE, datePattern = "dd/MM/yyyy", nullsLast = true))})
    static void dateDescNullsLast() {}

    @ApplySorts({@SortList(path = "data.items",
            by = @SortBy(field = "name", direction = SortDirection.ASC, type = SortType.STRING))})
    static void nestedDataItems() {}

    @ApplySorts({@SortList(path = "nonExistentField",
            by = @SortBy(field = "name", direction = SortDirection.ASC, type = SortType.STRING))})
    static void badPath() {}

    @ApplySorts({@SortList(path = "items",
            by = @SortBy(field = "unknownField", direction = SortDirection.ASC, type = SortType.STRING))})
    static void badField() {}

    /** Retrieves the real {@link ApplySorts} annotation from a static placeholder method. */
    private ApplySorts annotation(String methodName) {
        try {
            Method m = SortEngineTest.class.getDeclaredMethod(methodName);
            return m.getAnnotation(ApplySorts.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("No placeholder method: " + methodName, e);
        }
    }

    // ─── STRING sorting ───────────────────────────────────────────────────────

    @Test
    void sortsDirectListPathByStringAsc() {
        var container = new Container(List.of(
                new Item("Zebra", null, null, null, null),
                new Item("Apple", null, null, null, null),
                new Item("Mango", null, null, null, null)));

        var sorted = (Container) engine.sort(container, annotation("nameAsc"));

        assertEquals(List.of("Apple", "Mango", "Zebra"),
                sorted.items().stream().map(Item::name).toList());
    }

    @Test
    void sortsDirectListPathByStringDesc() {
        var container = new Container(List.of(
                new Item("Zebra", null, null, null, null),
                new Item("Apple", null, null, null, null),
                new Item("Mango", null, null, null, null)));

        var sorted = (Container) engine.sort(container, annotation("nameDesc"));

        assertEquals(List.of("Zebra", "Mango", "Apple"),
                sorted.items().stream().map(Item::name).toList());
    }

    // ─── NUMBER sorting ───────────────────────────────────────────────────────

    @Test
    void sortsByNumberAsc() {
        var container = new Container(List.of(
                new Item("c", null, 30, null, null),
                new Item("a", null, 10, null, null),
                new Item("b", null, 20, null, null)));

        var sorted = (Container) engine.sort(container, annotation("seqAsc"));

        assertEquals(List.of("a", "b", "c"),
                sorted.items().stream().map(Item::name).toList());
    }

    @Test
    void sortsByNumberDesc() {
        var container = new Container(List.of(
                new Item("c", null, 30, null, null),
                new Item("a", null, 10, null, null),
                new Item("b", null, 20, null, null)));

        var sorted = (Container) engine.sort(container, annotation("seqDesc"));

        assertEquals(List.of("c", "b", "a"),
                sorted.items().stream().map(Item::name).toList());
    }

    @Test
    void sortsByBigDecimalNumberAsc() {
        var container = new Container(List.of(
                new Item("high", null, null, new BigDecimal("999.99"), null),
                new Item("low",  null, null, new BigDecimal("1.01"),   null),
                new Item("mid",  null, null, new BigDecimal("100.00"), null)));

        var sorted = (Container) engine.sort(container, annotation("amountAsc"));

        assertEquals(List.of("low", "mid", "high"),
                sorted.items().stream().map(Item::name).toList());
    }

    // ─── DATE sorting ─────────────────────────────────────────────────────────

    @Test
    void sortsByDateDescUsingDatePattern() {
        var container = new Container(List.of(
                new Item("mar", "01/03/2026", null, null, null),
                new Item("jan", "01/01/2026", null, null, null),
                new Item("apr", "01/04/2026", null, null, null)));

        var sorted = (Container) engine.sort(container, annotation("dateDesc"));

        assertEquals(List.of("apr", "mar", "jan"),
                sorted.items().stream().map(Item::name).toList());
    }

    @Test
    void sortsByDateAscUsingDatePattern() {
        var container = new Container(List.of(
                new Item("dec", "01/12/2025", null, null, null),
                new Item("apr", "01/04/2026", null, null, null),
                new Item("jan", "01/01/2026", null, null, null)));

        var sorted = (Container) engine.sort(container, annotation("dateAsc"));

        assertEquals(List.of("dec", "jan", "apr"),
                sorted.items().stream().map(Item::name).toList());
    }

    // ─── Multi-field sorting ──────────────────────────────────────────────────

    @Test
    void sortsByMultipleFieldsInDeclarationOrder() {
        // Primary: date DESC; secondary: name ASC for ties on the same date
        var container = new Container(List.of(
                new Item("Zebra", "01/03/2026", null, null, null),
                new Item("Apple", "01/04/2026", null, null, null),
                new Item("Mango", "01/03/2026", null, null, null)));

        var sorted = (Container) engine.sort(container, annotation("dateDescNameAsc"));

        // Apple (Apr) first, then Mango and Zebra (both Mar) in name order
        assertEquals(List.of("Apple", "Mango", "Zebra"),
                sorted.items().stream().map(Item::name).toList());
    }

    // ─── Null handling ────────────────────────────────────────────────────────

    @Test
    void placesNullValuesLastByDefault() {
        var containerWithNulls = new Container(List.of(
                new Item(null, null, null, null, null),
                new Item("b",  null, null, null, null),
                new Item(null, null, null, null, null),
                new Item("a",  null, null, null, null)));

        var sorted = (Container) engine.sort(containerWithNulls, annotation("nameAscNullsLast"));

        var names = sorted.items().stream().map(Item::name).toList();
        assertEquals("a", names.get(0));
        assertEquals("b", names.get(1));
        assertTrue(names.get(2) == null);
        assertTrue(names.get(3) == null);
    }

    @Test
    void placesBlankDateValuesLast() {
        var container = new Container(List.of(
                new Item("blank", "",           null, null, null),
                new Item("valid", "01/03/2026", null, null, null),
                new Item("nulld", null,          null, null, null)));

        var sorted = (Container) engine.sort(container, annotation("dateDescNullsLast"));

        var names = sorted.items().stream().map(Item::name).toList();
        assertEquals("valid", names.get(0));
        // blank and null-date go last (relative order between them may vary)
        assertTrue(names.subList(1, 3).containsAll(List.of("blank", "nulld")));
    }

    @Test
    void placesUnparseableDateValuesLast() {
        var container = new Container(List.of(
                new Item("bad",   "not-a-date", null, null, null),
                new Item("valid", "01/04/2026", null, null, null),
                new Item("also",  "INVALID",    null, null, null)));

        var sorted = (Container) engine.sort(container, annotation("dateDescNullsLast"));

        var names = sorted.items().stream().map(Item::name).toList();
        assertEquals("valid", names.get(0));
        assertTrue(names.subList(1, 3).containsAll(List.of("bad", "also")));
    }

    // ─── Record accessor and rebuilding ───────────────────────────────────────

    @Test
    void supportsJavaRecordAccessorMethods() {
        // seq() is the Java record accessor; verifies accessor-based field resolution
        var container = new Container(List.of(
                new Item("third",  null, 3, null, null),
                new Item("first",  null, 1, null, null),
                new Item("second", null, 2, null, null)));

        var sorted = (Container) engine.sort(container, annotation("seqAsc"));

        assertEquals(List.of("first", "second", "third"),
                sorted.items().stream().map(Item::name).toList());
    }

    @Test
    void rebuildsImmutableRecordsInsteadOfMutating() {
        var original = new Container(List.of(
                new Item("b", null, null, null, null),
                new Item("a", null, null, null, null)));
        var originalItems = original.items(); // capture reference before sort

        var sorted = (Container) engine.sort(original, annotation("nameAsc"));

        // Original list must be unchanged
        assertEquals(List.of("b", "a"),
                originalItems.stream().map(Item::name).toList());
        // Sorted container has the reordered list
        assertEquals(List.of("a", "b"),
                sorted.items().stream().map(Item::name).toList());
    }

    @Test
    void rebuildsNestedRecordsCorrectly() {
        var nested = new Nested(new Container(List.of(
                new Item("c", null, null, null, null),
                new Item("a", null, null, null, null),
                new Item("b", null, null, null, null))));

        var sorted = (Nested) engine.sort(nested, annotation("nestedDataItems"));

        assertEquals(List.of("a", "b", "c"),
                sorted.data().items().stream().map(Item::name).toList());
    }

    @Test
    void handlesEmptyListWithoutError() {
        var container = new Container(List.of());
        var sorted = (Container) engine.sort(container, annotation("nameAsc"));
        assertTrue(sorted.items().isEmpty());
    }

    // ─── Error handling ───────────────────────────────────────────────────────

    @Test
    void failsFastWithClearErrorWhenPathCannotBeResolved() {
        var container = new Container(List.of());
        var ex = assertThrows(IllegalStateException.class, () ->
                engine.sort(container, annotation("badPath")));

        assertTrue(ex.getMessage().contains("nonExistentField"),
                "Error message should mention the unresolvable field name");
    }

    @Test
    void failsFastWithClearErrorWhenFieldCannotBeResolved() {
        // Need at least 2 items so the comparator is actually invoked by stream().sorted()
        var container = new Container(List.of(
                new Item("alpha", null, null, null, null),
                new Item("beta",  null, null, null, null)));

        var ex = assertThrows(IllegalStateException.class, () ->
                engine.sort(container, annotation("badField")));

        assertTrue(ex.getMessage().contains("unknownField"),
                "Error message should mention the unresolvable sort field name");
    }

    // ─── Multiple SortList declarations ──────────────────────────────────────

    @ApplySorts({
        @SortList(path = "items",
                by = @SortBy(field = "name", direction = SortDirection.ASC, type = SortType.STRING)),
        @SortList(path = "data.items",
                by = @SortBy(field = "name", direction = SortDirection.ASC, type = SortType.STRING))
    })
    static void twoSortLists() {}

    record ItemPair(List<Item> items, Container data) {}

    @Test
    void appliesMultipleSortListsInOrder() {
        var obj = new ItemPair(
                List.of(new Item("B", null, null, null, null),
                        new Item("A", null, null, null, null)),
                new Container(
                        List.of(new Item("Y", null, null, null, null),
                                new Item("X", null, null, null, null))));

        var sorted = (ItemPair) engine.sort(obj, annotation("twoSortLists"));

        assertEquals(List.of("A", "B"), sorted.items().stream().map(Item::name).toList());
        assertEquals(List.of("X", "Y"), sorted.data().items().stream().map(Item::name).toList());
    }
}
