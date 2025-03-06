/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package jenkins.tests

import static org.hamcrest.CoreMatchers.notNullValue
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.CoreMatchers.NullValue

class PublishToCratesLibTester extends LibFunctionTester {

    private String repository
    private String tag
    private String packageToPublish = ''

    public PublishToCratesLibTester(String repository, String tag , String packageToPublish) {
        this.repository = repository
        this.tag = tag
        this.packageToPublish = packageToPublish
    }

    public PublishToCratesLibTester(String repository, String tag) {
        this.repository = repository
        this.tag = tag
    }

    void configure(helper, binding) {
        helper.registerAllowedMethod('withCredentials', [Map])
        helper.registerAllowedMethod('checkout', [Map], { })
    }

    void parameterInvariantsAssertions(call) {
        assertThat(call.args.tag.toString(), notNullValue())
        assertThat(call.args.repository.toString(), notNullValue())
        if (call.args.packageToPublish.toString()) {
            assertThat(call.args.packageToPublish.toString(), notNullValue())
            }
    }

    boolean expectedParametersMatcher(call) {
        if (call.args.packageToPublish.isEmpty()) {
            return (this.packageToPublish.equals('')
                && call.args.repository.first().toString().equals(this.repository)
                && call.args.tag.first().toString().equals(this.tag))
        }
        return call.args.repository.first().toString().equals(this.repository)
            && call.args.tag.first().toString().equals(this.tag)
    }

    String libFunctionName() {
        return 'publishToCrates'
    }

}
