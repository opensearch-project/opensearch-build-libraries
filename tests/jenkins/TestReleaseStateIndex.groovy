/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package jenkins.tests

import org.junit.Before
import org.junit.Test
import jenkins.ReleaseStateIndex

class TestReleaseStateIndex {
    private final String metricsUrl = 'http://example.com'
    private final String awsAccessKey = 'testAccessKey'
    private final String awsSecretKey = 'testSecretKey'
    private final String awsSessionToken = 'testSessionToken'

    private ReleaseStateIndex releaseStateIndex
    private def script
    private List<String> echoedMessages

    // Tracks the sequence of curl invocations so tests can assert on cluster interactions.
    private List<String> shScripts

    @Before
    void setUp() {
        echoedMessages = []
        shScripts = []
        script = new Expando()
        script.echo = { String message -> echoedMessages.add(message) }
        // createIndex writes the mapping to a temp file before issuing the PUT.
        script.writeFile = { Map args -> }
        releaseStateIndex = new ReleaseStateIndex(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, script)
    }

    /**
     * Stubs script.sh to return HTTP codes keyed by curl verb:
     *  - HEAD (-I)   -> existsCode  (index existence check)
     *  - PUT (-XPUT) -> createCode  (index creation)
     * The temp-file cleanup 'rm' call is ignored.
     */
    private void stubClusterResponses(String existsCode, String createCode) {
        script.sh = { Map args ->
            String s = args.script
            shScripts.add(s)
            if (s.contains('-XPUT')) {
                return createCode
            }
            return existsCode
        }
    }

    @Test
    void testScheduleMappingHasExpectedFields() {
        Map mapping = ReleaseStateIndex.scheduleMapping()
        Map properties = mapping.mappings.properties
        assert properties.keySet() == [
                'version', 'rc_date', 'release_date', 'release_issue',
                'release_manager', 'status', 'registered_at', 'registered_by'
        ] as Set
        assert properties.version.type == 'keyword'
        assert properties.rc_date.type == 'date'
        assert properties.rc_date.format == 'yyyy-MM-dd'
        assert properties.release_date.type == 'date'
    }

    @Test
    void testStateMappingHasExpectedFields() {
        Map mapping = ReleaseStateIndex.stateMapping()
        Map properties = mapping.mappings.properties
        // discriminator + a representative field from each doc type
        assert properties.doc_type.type == 'keyword'
        assert properties.criterion_name.type == 'keyword'
        assert properties.decision.type == 'keyword'
        assert properties.agreed_with_oscar.type == 'boolean'
        assert properties.days_to_release.type == 'integer'
        // criteria_snapshot is stored but not indexed
        assert properties.criteria_snapshot.type == 'object'
        assert properties.criteria_snapshot.enabled == false
        // multi-field: text with a keyword sub-field
        assert properties.details.type == 'text'
        assert properties.details.fields.keyword.type == 'keyword'
        assert properties.details.fields.keyword.ignore_above == 1024
    }

    @Test
    void testCreateIndexIfNotExistCreatesWhenMissing() {
        // Index does not exist (HEAD -> 404), creation succeeds (PUT -> 200)
        stubClusterResponses('404', '200')
        releaseStateIndex.createIndexIfNotExist(ReleaseStateIndex.SCHEDULE_INDEX, ReleaseStateIndex.scheduleMapping())
        assert shScripts.any { it.contains('-XPUT') && it.contains(ReleaseStateIndex.SCHEDULE_INDEX) }
        assert echoedMessages.any { it.contains('Creating index') }
        assert echoedMessages.any { it.contains('created successfully') }
    }

    @Test
    void testCreateIndexIfNotExistSkipsWhenPresent() {
        // Index already exists (HEAD -> 200) -> no PUT should be issued
        stubClusterResponses('200', '200')
        releaseStateIndex.createIndexIfNotExist(ReleaseStateIndex.STATE_INDEX, ReleaseStateIndex.stateMapping())
        assert shScripts.every { !it.contains('-XPUT') }
        assert echoedMessages.any { it.contains('already exists') }
    }

    @Test
    void testCreateIndicesIfNotExistCreatesBoth() {
        stubClusterResponses('404', '200')
        releaseStateIndex.createIndicesIfNotExist()
        assert shScripts.any { it.contains('-XPUT') && it.contains(ReleaseStateIndex.SCHEDULE_INDEX) }
        assert shScripts.any { it.contains('-XPUT') && it.contains(ReleaseStateIndex.STATE_INDEX) }
    }

    @Test
    void testCreateIndexIfNotExistThrowsOnCreateFailure() {
        // Index missing (HEAD -> 404) but creation fails (PUT -> 500)
        stubClusterResponses('404', '500')
        try {
            releaseStateIndex.createIndexIfNotExist(ReleaseStateIndex.STATE_INDEX, ReleaseStateIndex.stateMapping())
            assert false : 'Expected RuntimeException on failed index creation'
        } catch (RuntimeException e) {
            assert e.message.contains('Failed to create index')
        }
    }
}
