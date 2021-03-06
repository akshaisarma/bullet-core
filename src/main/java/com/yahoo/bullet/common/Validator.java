/*
 *  Copyright 2017, Yahoo Inc.
 *  Licensed under the terms of the Apache License, Version 2.0.
 *  See the LICENSE file associated with the project for terms.
 */
package com.yahoo.bullet.common;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * This class validates instances of {@link BulletConfig}. Use {@link Validator.Entry} to define
 * fields and {@link Validator.Relationship} to define relationships between them.
 *
 * It also provides a bunch of useful {@link Predicate} and {@link BiPredicate} for use in the checks for the entries
 * and relationships and type converting {@link Function} for converting types of entries.
 */
@Slf4j
public class Validator {
    private static final Predicate<Object> UNARY_IDENTITY = o -> true;
    private static final BiPredicate<Object, Object> BINARY_IDENTITY = (oA, oB) -> true;

    /**
     * This represents a field in the Validator. It applies a {@link Predicate} to the value of the field and uses a
     * default value (see {@link Entry#defaultTo(Object)} if the predicate fails. It can also apply an arbitrary
     * conversion using {@link Entry#castTo(Function)}. These are all applied when you call
     * {@link Entry#normalize(BulletConfig)} with a {@link BulletConfig} containing a field that matches the Entry.
     */
    public static class Entry {
        private String key;
        private Predicate<Object> validation;
        private Predicate<Object> guard;
        private Object defaultValue;
        private Function<Object, Object> adapter;

        private Entry(String key) {
            this.validation = UNARY_IDENTITY;
            this.guard = UNARY_IDENTITY.negate();
            this.key = key;
        }

        private Entry copy() {
            Entry entry = new Entry(key);
            entry.adapter = adapter;
            entry.defaultValue = defaultValue;
            entry.validation = validation;
            entry.guard = guard;
            return entry;
        }

        /**
         * Add a {@link Predicate} to guard the checks in this entry. This predicate should take the value of the
         * field and return true if you do not want to run the checks added to the entry. This will also not
         * default to the provided defaults.
         *
         * @param guard The non-null guard to use for this Entry.
         * @return This Entry for chaining.
         */
        public Entry unless(Predicate<Object> guard) {
            Objects.requireNonNull(guard);
            this.guard = guard;
            return this;
        }

        /**
         * Add a {@link Predicate} to check for the field represented by the entry. This predicate should take the
         * value of the field and return true to represent a successful validation and false otherwise. You can add
         * more checks by repeatedly calling this method. All your predicates added so far will be ANDed to the latest
         * check.
         *
         * @param validator The non-null validator to use for this Entry.
         * @return This Entry for chaining.
         */
        public Entry checkIf(Predicate<Object> validator) {
            Objects.requireNonNull(validator);
            this.validation = this.validation.and(validator);
            return this;
        }

        /**
         * Use a default value for the field represented by this Entry. This is used if the validation fails. Note that
         * the {@link Entry#castTo(Function)} will be applied to this if present.
         *
         * @param defaultValue The value to use for the field in the {@link BulletConfig} if validation fails.
         * @return This Entry for chaining.
         */
        public Entry defaultTo(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        /**
         * Apply a cast to the value in the {@link BulletConfig} after validation and defaults are applied. Use this to
         * convert values in the config to their final types if you find yourself type-casting or checking for their
         * types repeatedly.
         *
         * @param adapter The function that takes the field (or the default value) represented and converts it to another.
         * @return This Entry for chaining.
         */
        public Entry castTo(Function<Object, Object> adapter) {
            this.adapter = adapter;
            return this;
        }

        /**
         * Get the defaultValue in the Entry after applying the cast, if present.
         *
         * @return The defaultValue after any casts.
         */
        public Object getDefaultValue() {
            if (adapter == null) {
                return defaultValue;
            }
            return adapter.apply(defaultValue);
        }

        /**
         * Normalizes a {@link BulletConfig} by validating, apply defaults and converting the object represented by the
         * field in this Entry.
         *
         * @param config The config to validate.
         */
        void normalize(BulletConfig config) {
            Object value = config.get(key);
            boolean shouldGuard = guard.test(value);
            if (shouldGuard) {
                log.info("Guard satisfied for Key: {}. Using current value: {}", key, value);
                return;
            }
            boolean isValid = validation.test(value);
            if (!isValid) {
                log.warn("Key: {} had an invalid value: {}. Using default: {}", key, value, defaultValue);
                value = defaultValue;
            }
            if (adapter != null) {
                value = adapter.apply(value);
                log.info("Changed the type for {}: {}", key, value);
            }
            config.set(key, value);
        }
    }

    /**
     * This represents a binary relationship between two fields in a {@link BulletConfig}. You should have defined
     * {@link Entry} for these fields before you try to define relationships between them. You can use this to apply a
     * {@link BiPredicate} to these fields and provide or use their defined defaults (defined using
     * {@link Entry#defaultTo(Object)}) if the check fails.
     */
    public static class Relationship {
        private String keyA;
        private String keyB;
        private String description;
        private BiPredicate<Object, Object> binaryRelation;
        private Object defaultA;
        private Object defaultB;

        private Relationship(String description, String keyA, String keyB, Map<String, Entry> entries) {
            this.description = description;
            this.keyA = keyA;
            this.keyB = keyB;
            // These keys in entries are guaranteed to be present.
            this.defaultA = entries.get(keyA).getDefaultValue();
            this.defaultB = entries.get(keyB).getDefaultValue();
            this.binaryRelation = BINARY_IDENTITY;
        }

        private Relationship copy(Map<String, Entry> entries) {
            Relationship relation = new Relationship(description, keyA, keyB, entries);
            relation.binaryRelation = binaryRelation;
            relation.defaultA = defaultA;
            relation.defaultB = defaultB;
            return relation;
        }

        /**
         * Provide the {@link BiPredicate} that acts as the check for this relationship. You can provide more checks
         * and they will be ANDed on the existing checks.
         *
         * @param binaryRelation A check for this relationship.
         * @return This Relationship for chaining.
         */
        public Relationship checkIf(BiPredicate<Object, Object> binaryRelation) {
            this.binaryRelation = this.binaryRelation.and(binaryRelation);
            return this;
        }

        /**
         * Provide custom defaults for this Relationship if you do not want to use the defaults provided in their
         * Entries.
         *
         * @param objectA The default for the first field.
         * @param objectB The default for the second field.
         */
        public void orElseUse(Object objectA, Object objectB) {
            this.defaultA = objectA;
            this.defaultB = objectB;
        }

        /**
         * Normalize the given {@link BulletConfig} for the fields defined by this relationship. This applies the check
         * and uses the defaults (provided using {@link Relationship#orElseUse(Object, Object)} or the Entry defaults
         * for these fields if the check fails.
         *
         * @param config The config to validate.
         */
        void normalize(BulletConfig config) {
            Object objectA = config.get(keyA);
            Object objectB = config.get(keyB);
            boolean isValid = binaryRelation.test(objectA, objectB);
            if (!isValid) {
                log.warn("{}: {} and {}: {} do not satisfy: {}. Using their defaults", keyA, objectA, keyB, objectB, description);
                log.warn("Using default {} for {}", defaultA, keyA);
                log.warn("Using default {} for {}", defaultB, keyB);
                config.set(keyA, defaultA);
                config.set(keyB, defaultB);
            }
        }
    }

    @Getter(AccessLevel.PACKAGE)
    private final Map<String, Entry> entries;
    @Getter(AccessLevel.PACKAGE)
    private final List<Relationship> relations;

    /**
     * Default constructor.
     */
    public Validator() {
        entries = new HashMap<>();
        relations = new ArrayList<>();
    }

    private Validator(Map<String, Entry> entries, List<Relationship> relations) {
        // Copy constructor.
        this();
        entries.forEach((name, entry) -> this.entries.put(name, entry.copy()));
        relations.forEach(relation -> this.relations.add(relation.copy(entries)));
    }

    /**
     * Creates an instance of the Entry using the name of the field. This field by default will pass the
     * {@link Predicate} unless you add a check using {@link Entry#checkIf(Predicate)}.
     *
     * @param key The name of the field.
     * @return The created {@link Entry}.
     */
    public Entry define(String key) {
        Entry entry = new Entry(key);
        entries.put(key, entry);
        return entry;
    }

    /**
     * Create a relationship with a description for it for the given fields. By default, the relationship will
     * hold true unless you provide a custom check using {@link Relationship#checkIf(BiPredicate)}. By default,
     * if the relationship fails to hold, the defaults defined by the Entries for these fields will be used unless
     * you provide new ones using {@link Relationship#orElseUse(Object, Object)}.
     *
     * @param description A string description of this relationship.
     * @param keyA The first field in the relationship.
     * @param keyB The second field in the relationship.
     * @return The created {@link Relationship}.
     */
    public Relationship relate(String description, String keyA, String keyB) {
        Objects.requireNonNull(entries.get(keyA), "You cannot add a relationship for " + keyA + " before defining it");
        Objects.requireNonNull(entries.get(keyB), "You cannot add a relationship for " + keyB + " before defining it");

        Relationship relation = new Relationship(description, keyA, keyB, entries);
        relations.add(relation);
        return relation;
    }

    /**
     * Validate and normalize the provided {@link BulletConfig} for the defined entries and relationships. Then entries
     * are used to validate the config first.
     *
     * @param config The config containing fields to validate.
     */
    public void validate(BulletConfig config) {
        entries.values().forEach(e -> e.normalize(config));
        relations.forEach(r -> r.normalize(config));
    }

    /**
     * Returns a copy of this validator. Note that your various functions that you provided to the your entries
     * and relationships in the validator are not deep copied.
     *
     * @return A copy of this validator with all its defined {@link Entry} and {@link Relationship}.
     */
    public Validator copy() {
        return new Validator(entries, relations);
    }

    // Type Adapters

    /**
     * This casts a {@link Number} Object to an {@link Integer}.
     *
     * @param value The value to cast.
     * @return The converted Integer object.
     */
    public static Object asInt(Object value) {
        return ((Number) value).intValue();
    }

    /**
     * This casts a {@link Number} Object to an {@link Long}.
     *
     * @param value The value to cast.
     * @return The converted Long object.
     */
    public static Object asLong(Object value) {
        return ((Number) value).longValue();
    }

    /**
     * This casts a {@link Number} Object to an {@link Float}.
     *
     * @param value The value to cast.
     * @return The converted Float object.
     */
    public static Object asFloat(Object value) {
        return ((Number) value).floatValue();
    }

    /**
     * This casts a {@link Number} Object to an {@link Double}.
     *
     * @param value The value to cast.
     * @return The converted Double object.
     */
    public static Object asDouble(Object value) {
        return ((Number) value).doubleValue();
    }

    /**
     * This casts an Object to an {@link String}.
     *
     * @param value The value to cast.
     * @return The converted String object.
     */
    public static Object asString(Object value) {
        return value.toString();
    }

    // Unary Predicates

    /**
     * Checks to see if the value is null or not.
     *
     * @param value The object to check.
     * @return A boolean denoting if the value was null.
     */
    public static boolean isNotNull(Object value) {
        return value != null;
    }

    /**
     * Checks to see if the value is of the provided type or not.
     *
     * @param value The object to check type for.
     * @param clazz The supposed class of the value.
     * @return A boolean denoting if the value was of the provided class.
     */
    public static boolean isType(Object value, Class clazz) {
        return isNotNull(value) && clazz.isInstance(value);
    }

    /**
     * Checks to see if the value is a {@link Boolean}.
     *
     * @param value The object to check.
     * @return A boolean denoting if the value was a boolean.
     */
    public static boolean isBoolean(Object value) {
        return isType(value, Boolean.class);
    }

    /**
     * Checks to see if the value is a {@link String}.
     *
     * @param value The object to check.
     * @return A boolean denoting if the value was a String.
     */
    public static boolean isString(Object value) {
        return isType(value, String.class);
    }

    /**
     * Checks to see if the value is a {@link List}.
     *
     * @param value The object to check.
     * @return A boolean denoting if the value was a List.
     */
    public static boolean isList(Object value) {
        return isType(value, List.class);
    }

    /**
     * Checks to see if the value is a {@link Map}.
     *
     * @param value The object to check.
     * @return A boolean denoting if the value was a Map.
     */
    public static boolean isMap(Object value) {
        return isType(value, Map.class);
    }

    /**
     * Checks to see if the value is a {@link Number}.
     *
     * @param value The object to check.
     * @return A boolean denoting if the value was a Number.
     */
    public static boolean isNumber(Object value) {
        return isType(value, Number.class);
    }

    /**
     * Checks to see if the value is an integer type. Both {@link Integer} and {@link Long} qualify.
     *
     * @param value The object to check.
     * @return A boolean denoting if the value was an integer.
     */
    public static boolean isInt(Object value) {
        return isType(value, Long.class) || isType(value, Integer.class);
    }

    /**
     * Checks to see if the value is an floating-point. Both {@link Float} and {@link Double} qualify.
     *
     * @param value The object to check.
     * @return A boolean denoting if the value was a floating-point.
     */
    public static boolean isFloat(Object value) {
        return isType(value, Double.class) || isType(value, Float.class);
    }

    /**
     * Checks to see if the value was positive.
     *
     * @param value The object to check.
     * @return A boolean denoting whether the given number value was positive.
     */
    public static boolean isPositive(Object value) {
        return isNumber(value) && ((Number) value).doubleValue() > 0;
    }

    /**
     * Checks to see if the value was a positive integer ({@link Integer} or {@link Long}).
     *
     * @param value The object to check.
     * @return A boolean denoting whether the given number value was a positive integer type.
     */
    public static boolean isPositiveInt(Object value) {
        return isPositive(value) && isInt(value);
    }

    /**
     * Checks to see if the value was a positive integer ({@link Integer} or {@link Long}) and a power of 2.
     *
     * @param value The object to check.
     * @return A boolean denoting whether the given number value was a positive, power of 2 integer type.
     */
    public static boolean isPowerOfTwo(Object value) {
        if (!isPositiveInt(value)) {
            return false;
        }
        int toCheck = ((Number) value).intValue();
        return (toCheck & toCheck - 1) == 0;
    }

    // Unary Predicate Generators

    /**
     * Creates a {@link Predicate} that checks to see if the given object is in the list of values.
     *
     * @param values The values that the object could be equal to that is being tested.
     * @param <T> The type of the values and the object.
     * @return A predicate that checks to see if the object provided to it is in the given values.
     */
    @SuppressWarnings("unchecked")
    public static <T> Predicate<Object> isIn(T... values) {
        Objects.requireNonNull(values);
        Set<T> set = new HashSet<>(Arrays.asList(values));
        return set::contains;
    }

    /**
     * Creates a {@link Predicate} that checks to see if the given object is a {@link Number} in the proper range.
     *
     * @param min The smallest this number value could be.
     * @param max The largest this number value could be.
     * @param <T> The type of the value, min, and max.
     * @return A predicate that checks to see if the provided object is a Number and is in the proper range.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Number> Predicate<Object> isInRange(T min, T max) {
        Objects.requireNonNull(min);
        Objects.requireNonNull(max);
        double minimum = min.doubleValue();
        double maximum = max.doubleValue();
        return o -> isNumber(o) && ((T) o).doubleValue() >= minimum && ((T) o).doubleValue() <= maximum;
    }

    // Binary Predicates.

    /**
     * Checks to see if the first numeric object is greater than or equal to the second numeric object.
     *
     * @param first The first numeric object.
     * @param second The second numeric object.
     * @return A boolean denoting whether the first object is greater or equal to the second.
     */
    public static boolean isGreaterOrEqual(Object first, Object second) {
        return ((Number) first).doubleValue() >= ((Number) second).doubleValue();
    }
}
