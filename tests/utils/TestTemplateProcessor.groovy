/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package utils.tests


import org.junit.Before
import org.junit.Test
import utils.TemplateProcessor
import static org.junit.Assert.assertEquals
<<<<<<< HEAD
import static org.junit.Assert.assertThrows
=======
>>>>>>> 4e96a439 (Add library to check the code-coverage status)

class TestTemplateProcessor {
    def script
    def writtenFiles =[:]

    @Before
    void setUp() {
        script = new Expando()
        script.libraryResource = { path ->
            // Return a sample template content for testing
            return "This is a test template checking values for \$BRANCH and \$VERSION"
        }
        script.writeFile = { Map params ->
            // Mock implementation to verify the file is written correctly
            writtenFiles["Content"] = ["${params.text}"]
            return true
        }

        script.println = { message ->
            // Mock implementation for println
            println(message)
        }

        script.error = { message ->
            // Mock implementation for error
            throw new Exception(message)
        }
    }

    @Test
    void testProcessor() {
<<<<<<< HEAD
        Random.metaClass.nextInt = { int max -> 1 }
=======

>>>>>>> 4e96a439 (Add library to check the code-coverage status)
        def bindings = [
                BRANCH: 'main',
                VERSION: '3.0'
        ]
        def templateProcessor = new TemplateProcessor(script)
        def result  = templateProcessor.process("release/missing-code-coverage.md", bindings, '/tmp/workspace')
<<<<<<< HEAD
        assertEquals (result, "/tmp/workspace/BBBBBBBBBB.md")
        assertEquals(writtenFiles["Content"].toString(),"[This is a test template checking values for main and 3.0]" )
    }

    @Test
    void testProcessorException() {
        def bindings = [
                BRANCH: '',
                VERSION: '3.0'
        ]
        script.libraryResource = { path ->
            // Return a sample template content for testing
            throw new IOException("Resource not found, ${path}")
        }
        def templateProcessor = new TemplateProcessor(script)
        try {
            templateProcessor.process("/tmp", bindings, '/tmp/workspace')
            fail("Expected an exception to be thrown")
        } catch (Exception e) {
            assertEquals("Failed to process template: Resource not found, /tmp", e.getMessage())
        }
    }
=======
        assert result.startsWith("/tmp/workspace/")
        assertEquals(writtenFiles["Content"].toString(),"[This is a test template checking values for main and 3.0]" )
    }
>>>>>>> 4e96a439 (Add library to check the code-coverage status)
}

