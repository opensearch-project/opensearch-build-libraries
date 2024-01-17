/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package jenkins

class TestManifest {

    class Ci implements Serializable {
        class Image implements Serializable {

            String name
            String args

            Image(Map data) {
                this.name = data.name
                this.args = data.args
            }

        }

        Image image

        Ci(Map data) {
            this.image = new TestManifest.Ci.Image(data.image)
        }

    }

    class Components extends HashMap<String, Component> {

        Components(ArrayList data) {
            data.each { item ->
                Component component = new Component(item)
                this[component.name] = component
            }
        }

    }

    class Component implements Serializable {

        String name

        Component(Map data) {
            this.name = data.name
        }

    }

    String name

    Ci ci
    Components components

    TestManifest(Map data) {
        this.name = data.name
        this.ci = data.ci ? new TestManifest.Ci(data.ci) : null
        this.components = data.components ? new TestManifest.Components(data.components) : null
    }

    public ArrayList getComponentNames() {
        def componentsName = []
        this.components.each { key, value -> componentsName.add(key) }
        return componentsName
    }

}
