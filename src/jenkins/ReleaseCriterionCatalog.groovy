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
 * The full catalog of release criteria tracked in the release issue tables, in one place so the
 * chore and manual paths never drift. Each maps a stable keyword from the criterion's prose to its
 * criterion name and entrance/exit type, and records whether an automated chore verifies it
 * ('chore_check') or the release manager sets it by hand ('issue_table').
 *
 * The product a criterion applies to is the table it sits in (entrance -> both, exit -> per product),
 * so it is resolved by the reader, not stored here.
 */
enum ReleaseCriterionCatalog {

    RELEASE_OWNERS('assigned owner', 'release_owners_assigned', 'entrance', 'chore_check'),
    DOCUMENTATION_DRAFT_PRS('documentation draft prs', 'documentation_draft_prs_up', 'entrance', 'chore_check'),
    CODE_COVERAGE('code coverage has not decreased', 'code_coverage_not_decreased', 'entrance', 'chore_check'),
    RELEASE_NOTES('release notes are ready', 'release_notes_ready', 'entrance', 'chore_check'),
    RELEASE_TICKET('release ticket is cut', 'release_ticket_and_forum_post', 'entrance', 'chore_check'),
    DOCUMENTATION_SIGNED_OFF('documentation has been fully reviewed', 'documentation_reviewed_signed_off', 'exit', 'chore_check'),
    INTEGRATION_TESTS('all integration tests are passing', 'all_integration_tests_passing', 'exit', 'chore_check'),
    UNPATCHED_VULNERABILITIES('no unpatched vulnerabilities', 'no_unpatched_vulnerabilities', 'exit', 'chore_check'),

    SANITY_TESTING('sanity testing is done', 'sanity_testing_done', 'entrance', 'issue_table'),
    ROADMAP('roadmap is up-to-date', 'roadmap_up_to_date', 'entrance', 'issue_table'),
    SECURITY_REVIEWS('security reviews', 'security_reviews_complete', 'entrance', 'issue_table'),
    PERFORMANCE_TESTS('performance tests are run', 'performance_tests_posted', 'exit', 'issue_table'),
    RELEASE_BLOG('release blog is ready', 'release_blog_ready', 'exit', 'issue_table')

    static final String SOURCE_CHORE = 'chore_check'
    static final String SOURCE_ISSUE_TABLE = 'issue_table'

    final String keyword
    final String criterionName
    final String criterionType
    final String source

    ReleaseCriterionCatalog(String keyword, String criterionName, String criterionType, String source) {
        this.keyword = keyword
        this.criterionName = criterionName
        this.criterionType = criterionType
        this.source = source
    }
}
