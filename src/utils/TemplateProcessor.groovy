/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package utils

import groovy.text.GStringTemplateEngine

class TemplateProcessor implements Serializable {
    def script

    TemplateProcessor(script) {
        this.script = script
    }

    def process(String pathToTemplate, def bindings, String outputDir) {
        try {
            def randomName = getRandomName()
            def content = this.script.libraryResource pathToTemplate
            String result = new GStringTemplateEngine().createTemplate(content).make(bindings).toString()
            String processedTemplatePath = "${outputDir}/${randomName}.md"
            this.script.writeFile(file: processedTemplatePath , text: result.toString())
            this.script.println("Wrote file to ${processedTemplatePath}")
            return processedTemplatePath
        } catch (Exception e) {
            this.script.error("Failed to process template: ${e.getMessage()}")
        }
    }

    private static getRandomName(){
        def random = new Random()
        def randomName = (1..10).collect { ('A'..'Z')[random.nextInt(26)] }.join("")
        return randomName
    }
}
