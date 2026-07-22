/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package jenkins

/**
 * Names of the release state indices on the OpenSearch metrics cluster.
 *
 * Kept as a small constants-only holder so that classes needing the index names
 * (ReleaseStateIndex, ReleaseStateData) can share them without referencing each other's
 * static fields. Cross-class static-constant references between full classes can trigger a
 * re-entrant GroovyClassLoader recompile deadlock under the Jenkins pipeline unit runtime;
 * a dependency-free holder avoids that.
 */
class ReleaseIndices {
    public static final String SCHEDULE = 'opensearch_release_schedule'
    public static final String STATE = 'opensearch_release_state'
}
