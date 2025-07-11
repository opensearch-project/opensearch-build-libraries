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
        static class Image implements Serializable {

            String name
            String args

            Image(Map data) {
                this.name = data.name
                this.args = data.args
            }

        }

        Image image  // schema-version < 1.1
        Map<String, Map<String, Image>> images = [:]  // schema-version >= 1.1

        // schemaVersion < 1.1:
        // data.image = [ name: 'opensearchstaging/ci-runner:...', args: ... ]
        // this.image holds a single Image instance:
        // this.image = new InputManifest.Ci.Image(data.image)

        // schemaVersion >= 1.1:
        // data.image = [
        //     'linux': [
        //         'tar': [ name: '...', args: ... ],
        //         'rpm': [ name: '...', args: ... ]
        //     ],
        //     'windows': [
        //         'zip': [ name: '...', args: ... ]
        //     ]
        // ]
        // this.images is a Map<String, Map<String, Image>> holds multiple Image instances:
        // this.images = [
        //    "linux": [
        //        "tar": new InputManifest.Ci.Image(...),
        //        "rpm": new InputManifest.Ci.Image(...)
        //    ],
        //    "windows": [
        //        "zip": new InputManifest.Ci.Image(...)
        //    ]
        //]

        Ci(Map data) {
            if (this.schemaVersion < 1.1) {
                this.image = new TestManifest.Ci.Image(data.image)
            }
            else {
                Map imageData = data.image
                imageData.each { plat, dists ->
                    Map<String, Image> distImageMap = [:]
                    dists.each { dist, img ->
                        distImageMap[dist] = new TestManifest.Ci.Image(img)
                    }
                    this.images[plat] = distImageMap
                }
            }
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
    Double schemaVersion

    TestManifest(Map data) {
        this.schemaVersion = data."schema-version".toDouble()
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
