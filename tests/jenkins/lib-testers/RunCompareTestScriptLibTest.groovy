/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
import static org.hamcrest.CoreMatchers.notNullValue
import static org.hamcrest.MatcherAssert.assertThat

class RunCompareTestScriptLibTester extends LibFunctionTester{

    private String command
    private String baseline
    private String contender
    private String results_format
    private String results_numbers_align
    private String results_file
    private String show_in_results
    private String suffix
    private String bundleManifest

    public RunCompareTestScriptLibTester(command, baseline, contender, results_format,
                                        results_numbers_align, results_file, show_in_results,
                                        suffix, bundleManifest){
        this.command = command
        this.baseline = baseline
        this.contender = contender
        this.results_format = results_format
        this.results_numbers_align = results_numbers_align
        this.results_file = results_file
        this.show_in_results = show_in_results
        this.suffix = suffix
        this.bundleManifest = bundleManifest
    }


    @Override
    String libFunctionName() {
        return 'runBenchmarkTestScript'
    }

    @Override
    void parameterInvariantsAssertions(Object call) {}

    @Override
    boolean expectedParametersMatcher(Object call) {
        if (!this.bundleManifest.isEmpty()) {
            return call.args.bundleManifest.first().toString().equals(this.bundleManifest)
        }
        return call.args.distributionUrl.first().toString().equals(this.distributionUrl)
    }

    @Override
    void configure(Object helper, Object binding) {
        binding.setVariable('COMMAND', command)
        binding.setVariable('SUFFIX', suffix)
        binding.setVariable('BENCHMARK_TEST_CONFIG_LOCATION', 'test_config')
        binding.setVariable('BASELINE', baseline)
        binding.setVariable('CONTENDER', contender)
        binding.setVariable('RESULTS_FORMAT', results_format)
        binding.setVariable('RESULTS_NUMBERS_ALIGN', results_numbers_align)
        binding.setVariable('RESULTS_FILE', results_file)
        binding.setVariable('SHOW_IN_RESULTS', show_in_results)
        binding.setVariable('BUNDLE_MANIFEST', bundleManifest)
        helper.registerAllowedMethod("withAWS", [Map, Closure], {
            args,
            closure ->
                closure.delegate = delegate
                return helper.callClosure(closure)
        })
        helper.registerAllowedMethod("s3Download", [Map])
        helper.registerAllowedMethod("withAWS", [Map, Closure], {
            args,
            closure ->
                closure.delegate = delegate
                return helper.callClosure(closure)
        })
        helper.registerAllowedMethod("withCredentials", [Map])
    }
}
