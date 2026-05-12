package com.bct.ngtpa.apiservice.adapter.in.web.sort;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;

/**
 * Core engine that applies {@link ApplySorts} sort configurations to an object graph.
 *
 * <p>Supports sorting lists identified by dot-separated accessor paths on Java records.
 * After sorting, the engine rebuilds every affected record using the canonical constructor so
 * that immutable record graphs are handled correctly without in-place mutation.
 *
 * <p>Path resolution uses public record accessor methods (e.g. {@code report()} for the
 * {@code report} component). Nested field paths in {@link SortBy#field()} are also navigated
 * via accessor methods.
 *
 * <p>Null/blank/unparseable DATE values are treated as {@code null} and placed according to
 * {@link SortBy#nullsLast()} (default: {@code true} → nulls last).
 */
@Component
public class SortEngine {

    /**
     * Applies all {@link SortList} configurations declared in {@code applySorts} to {@code target}
     * and returns the rebuilt, sorted result.
     *
     * @param target     the object to sort (must be a Java record for nested paths)
     * @param applySorts the annotation holding one or more {@link SortList} configurations
     * @return the sorted (and immutably rebuilt) result
     */
    public Object sort(Object target, ApplySorts applySorts) {
        Object result = target;
        for (SortList sortListAnnotation : applySorts.value()) {
            result = applySort(result, sortListAnnotation);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Path navigation and record rebuilding
    // -------------------------------------------------------------------------

    private Object applySort(Object root, SortList sortListAnnotation) {
        String[] parts = sortListAnnotation.path().split("\\.", -1);
        return rebuildWithSort(root, parts, 0, sortListAnnotation.by());
    }

    /**
     * Recursively navigates the path, sorts the target list at the final segment,
     * and rebuilds records on the way back up the call stack.
     */
    private Object rebuildWithSort(Object current, String[] parts, int index, SortBy[] sortBys) {
        String field = parts[index];
        Object fieldValue = readSimpleField(current, field);

        if (index == parts.length - 1) {
            // Leaf: the field value must be a List to sort
            if (!(fieldValue instanceof List<?> list)) {
                throw new IllegalStateException(
                        "SortEngine: path field '" + field + "' on " + current.getClass().getSimpleName()
                        + " does not resolve to a List; got: "
                        + (fieldValue == null ? "null" : fieldValue.getClass().getName()));
            }
            List<?> sorted = applySortComparator(list, sortBys);
            return rebuildRecord(current, field, sorted);
        } else {
            // Non-leaf: navigate deeper, then rebuild on return
            if (fieldValue == null) {
                throw new IllegalStateException(
                        "SortEngine: cannot navigate path – field '" + field
                        + "' resolved to null on " + current.getClass().getSimpleName());
            }
            Object rebuiltNested = rebuildWithSort(fieldValue, parts, index + 1, sortBys);
            return rebuildRecord(current, field, rebuiltNested);
        }
    }

    /** Invokes the public no-arg accessor method named {@code fieldName} on {@code obj}. */
    private Object readSimpleField(Object obj, String fieldName) {
        try {
            var method = obj.getClass().getMethod(fieldName);
            return method.invoke(obj);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    "SortEngine: cannot resolve field '" + fieldName + "' on "
                    + obj.getClass().getSimpleName() + ": no public accessor method found", e);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "SortEngine: failed to read field '" + fieldName + "' on "
                    + obj.getClass().getSimpleName(), e);
        }
    }

    /**
     * Navigates a dot-separated {@code fieldPath} starting from {@code obj}.
     * Returns {@code null} if any intermediate value is {@code null}.
     */
    private Object readFieldPath(Object obj, String fieldPath) {
        Object current = obj;
        for (String part : fieldPath.split("\\.", -1)) {
            if (current == null) return null;
            current = readSimpleField(current, part);
        }
        return current;
    }

    /**
     * Rebuilds the given Java record by replacing the component named {@code fieldToReplace}
     * with {@code newValue}, copying all other component values from the original record.
     * Invokes the canonical constructor to produce an immutable replacement.
     *
     * @throws IllegalStateException if {@code record} is not a Java record, if a component
     *                               cannot be read, or if the canonical constructor cannot be invoked
     */
    private Object rebuildRecord(Object record, String fieldToReplace, Object newValue) {
        var clazz = record.getClass();
        if (!clazz.isRecord()) {
            throw new IllegalStateException(
                    "SortEngine: cannot rebuild non-record type: " + clazz.getName()
                    + ". Only Java records are supported for immutable rebuilding.");
        }
        var components = clazz.getRecordComponents();
        Object[] args = new Object[components.length];
        Class<?>[] types = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            var component = components[i];
            types[i] = component.getType();
            if (component.getName().equals(fieldToReplace)) {
                args[i] = newValue;
            } else {
                try {
                    args[i] = component.getAccessor().invoke(record);
                } catch (Exception e) {
                    throw new IllegalStateException(
                            "SortEngine: failed to read record component '" + component.getName()
                            + "' on " + clazz.getSimpleName(), e);
                }
            }
        }
        try {
            var constructor = clazz.getDeclaredConstructor(types);
            return constructor.newInstance(args);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "SortEngine: failed to rebuild record " + clazz.getSimpleName(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Sorting and comparison
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private <T> List<T> applySortComparator(List<T> list, SortBy[] sortBys) {
        if (list == null || list.isEmpty()) return list;
        Comparator<T> comparator = buildComparator(sortBys);
        return list.stream().sorted(comparator).toList();
    }

    @SuppressWarnings("unchecked")
    private <T> Comparator<T> buildComparator(SortBy[] sortBys) {
        Comparator<T> result = null;
        for (SortBy sortBy : sortBys) {
            Comparator<T> next = (Comparator<T>) buildSingleComparator(sortBy);
            result = result == null ? next : result.thenComparing(next);
        }
        return result != null ? result : (a, b) -> 0;
    }

    private Comparator<Object> buildSingleComparator(SortBy sortBy) {
        return (a, b) -> {
            Object rawA = readFieldPath(a, sortBy.field());
            Object rawB = readFieldPath(b, sortBy.field());
            Comparable<?> keyA = toComparable(rawA, sortBy);
            Comparable<?> keyB = toComparable(rawB, sortBy);
            return compareNullSafe(keyA, keyB, sortBy.nullsLast(), sortBy.direction());
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private int compareNullSafe(Comparable valA, Comparable valB, boolean nullsLast,
                                SortDirection direction) {
        if (valA == null && valB == null) return 0;
        if (valA == null) return nullsLast ? 1 : -1;
        if (valB == null) return nullsLast ? -1 : 1;
        int cmp = valA.compareTo(valB);
        return direction == SortDirection.DESC ? -cmp : cmp;
    }

    private Comparable<?> toComparable(Object rawValue, SortBy sortBy) {
        return switch (sortBy.type()) {
            case STRING -> rawValue == null ? null : rawValue.toString();
            case NUMBER -> toNumber(rawValue);
            case DATE -> toDate(rawValue, sortBy.datePattern());
            case BOOLEAN -> rawValue instanceof Boolean b ? b : null;
        };
    }

    private BigDecimal toNumber(Object rawValue) {
        if (rawValue == null) return null;
        return switch (rawValue) {
            case BigDecimal bd -> bd;
            case Number n -> BigDecimal.valueOf(n.doubleValue());
            default -> {
                try {
                    yield new BigDecimal(rawValue.toString());
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
        };
    }

    private LocalDate toDate(Object rawValue, String pattern) {
        if (rawValue == null) return null;
        String str = rawValue.toString().trim();
        if (str.isEmpty()) return null;
        if (pattern == null || pattern.isEmpty()) {
            try {
                return LocalDate.parse(str);
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        try {
            return LocalDate.parse(str, DateTimeFormatter.ofPattern(pattern));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
