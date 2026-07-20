/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package utils.tests

import org.junit.Test
import utils.ArgumentValidator

class TestArgumentValidator {

    // ---- required ----

    @Test
    void testRequiredReturnsValueWhenPresent() {
        assert ArgumentValidator.required([name: 'oscar'], 'name') == 'oscar'
    }

    @Test(expected = IllegalArgumentException)
    void testRequiredThrowsWhenMissing() {
        ArgumentValidator.required([:], 'name')
    }

    @Test(expected = IllegalArgumentException)
    void testRequiredThrowsWhenEmpty() {
        ArgumentValidator.required([name: ''], 'name')
    }

    @Test
    void testRequiredIncludesContextInMessage() {
        try {
            ArgumentValidator.required([:], 'name', 'MyClass')
            assert false : 'Expected IllegalArgumentException'
        } catch (IllegalArgumentException e) {
            assert e.message == "MyClass: 'name' is required."
        }
    }

    // ---- requireOneOf ----

    @Test
    void testRequireOneOfReturnsValueWhenAllowed() {
        assert ArgumentValidator.requireOneOf([status: 'active'], 'status', ['active', 'inactive']) == 'active'
    }

    @Test(expected = IllegalArgumentException)
    void testRequireOneOfThrowsWhenNotAllowed() {
        ArgumentValidator.requireOneOf([status: 'paused'], 'status', ['active', 'inactive'])
    }

    @Test(expected = IllegalArgumentException)
    void testRequireOneOfThrowsWhenMissing() {
        ArgumentValidator.requireOneOf([:], 'status', ['active'])
    }

    // ---- optionalOneOf ----

    @Test
    void testOptionalOneOfReturnsNullWhenAbsent() {
        assert ArgumentValidator.optionalOneOf([:], 'product', ['opensearch']) == null
    }

    @Test
    void testOptionalOneOfReturnsValueWhenAllowed() {
        assert ArgumentValidator.optionalOneOf([product: 'opensearch'], 'product', ['opensearch', 'both']) == 'opensearch'
    }

    @Test(expected = IllegalArgumentException)
    void testOptionalOneOfThrowsWhenPresentButNotAllowed() {
        ArgumentValidator.optionalOneOf([product: 'logstash'], 'product', ['opensearch', 'both'])
    }
}
