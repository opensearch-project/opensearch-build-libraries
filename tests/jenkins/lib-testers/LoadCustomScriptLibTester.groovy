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

class LoadCustomScriptLibTester extends LibFunctionTester {
    private String scriptPath
    private String scriptName

    public LoadCustomScriptLibTester(scriptPath, scriptName){
        this.scriptPath = scriptPath
        this.scriptName = scriptName
    }

    @Override
    void configure(helper, binding) {
        helper.registerAllowedMethod('libraryResource', [Map])

    }

    @Override
    void parameterInvariantsAssertions(call) {
        assertThat(call.args.scriptPath.first(), notNullValue())
        assertThat(call.args.scriptName.first(), notNullValue())
    }

    @Override
    boolean expectedParametersMatcher(call) {
        return call.args.scriptPath.first().equals(this.scriptPath)
    }

    String libFunctionName() {
        return 'loadCustomScript'
    }
}
