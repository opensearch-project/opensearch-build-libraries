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
 * The release criteria that no automated chore verifies and are read from the release issue's
 * criteria tables instead. Each maps a stable keyword from the criterion's prose to its criterion
 * name and type. Which product a criterion applies to depends on the table it appears in (entrance
 * applies to both products, the exit tables are per product), so that is resolved by the parser.
 */
enum ManualReleaseCriterion {

    SANITY_TESTING('sanity testing is done', 'sanity_testing_done', 'entrance'),
    ROADMAP('roadmap is up-to-date', 'roadmap_up_to_date', 'entrance'),
    SECURITY_REVIEWS('security reviews', 'security_reviews_complete', 'entrance'),
    PERFORMANCE_TESTS('performance tests are run', 'performance_tests_posted', 'exit'),
    RELEASE_BLOG('release blog is ready', 'release_blog_ready', 'exit')

    final String keyword
    final String criterionName
    final String criterionType

    ManualReleaseCriterion(String keyword, String criterionName, String criterionType) {
        this.keyword = keyword
        this.criterionName = criterionName
        this.criterionType = criterionType
    }
}
