/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package utils

import com.cloudbees.groovy.cps.NonCPS

/**
 * Generic validation helpers for named-argument (Map) inputs.
 *
 * Intended for any class or var that accepts a Map of arguments and needs to fail fast on
 * missing required fields or values outside an allowed set. The optional 'context' label is
 * included in error messages to identify the caller (e.g. the class name).
 */
class ArgumentValidator {

    /**
     * Returns args[key], throwing IllegalArgumentException if it is null or empty.
     */
    @NonCPS
    static Object required(Map args, String key, String context = 'ArgumentValidator') {
        def value = args[key]
        if (!value) {
            throw new IllegalArgumentException("${context}: '${key}' is required.")
        }
        return value
    }

    /**
     * Returns a required args[key], throwing if it is missing or not in the allowed list.
     */
    @NonCPS
    static Object requireOneOf(Map args, String key, List allowed, String context = 'ArgumentValidator') {
        def value = required(args, key, context)
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException("${context}: '${key}' must be one of ${allowed}, got '${value}'.")
        }
        return value
    }

    /**
     * Returns args[key] if present, throwing only when a non-null value is not in the allowed list.
     * Returns null when the key is absent.
     */
    @NonCPS
    static Object optionalOneOf(Map args, String key, List allowed, String context = 'ArgumentValidator') {
        def value = args[key]
        if (value != null && !allowed.contains(value)) {
            throw new IllegalArgumentException("${context}: '${key}' must be one of ${allowed}, got '${value}'.")
        }
        return value
    }
}
