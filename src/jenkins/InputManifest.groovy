/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package jenkins

class InputManifest {
    class Ci implements Serializable {
        static class Image implements Serializable {
            String name
            String args

            Image(Map data) {
                this.name = data.name

                if (this.name == null) {
                    error("Missing ci.image.<platform>.<distribution>.name")
                }

                this.args = data.args
            }
        }

        Image image  // schema-version < 1.2
        Map<String, Map<String, Image>> images = [:]  // schema-version >= 1.2

        // schemaVersion < 1.2:
        // data.image = [ name: 'opensearchstaging/ci-runner:...', args: ... ]
        // this.image holds a single Image instance:
        // this.image = new InputManifest.Ci.Image(data.image)

        // schemaVersion >= 1.2:
        // data.image = [
        //     'linux': [
        //         'ubuntu': [ name: '...', args: ... ],
        //         'centos': [ name: '...', args: ... ]
        //     ],
        //     'windows': [
        //         '2019': [ name: '...', args: ... ]
        //     ]
        // ]
        // this.images is a Map<String, Map<String, Image>> holds multiple Image instances:
        // this.images = [
        //    "linux": [
        //        "centos7": new InputManifest.Ci.Image(...),
        //        "ubuntu22": new InputManifest.Ci.Image(...)
        //    ],
        //    "windows": [
        //        "2019": new InputManifest.Ci.Image(...)
        //    ]
        //]
        Ci(Map data) {
            if (this.schemaVersion < 1.2) {
                this.image = new InputManifest.Ci.Image(data.image)
            }
            else {
                Map imageData = data.image
                imageData.each { plat, dists ->
                    Map<String, Image> distImageMap = [:]
                    dists.each { dist, img ->
                        distImageMap[dist] = new InputManifest.Ci.Image(img)
                    }
                    this.images[plat] = distImageMap
                }
            }
        }
    }

    class Build implements Serializable {
        String name
        String version
        String qualifier
        String platform
        String architecture

        Build(Map data) {
            this.name = data.name
            this.version = data.version
            this.qualifier = data.qualifier
            this.platform = data.platform
            this.architecture = data.architecture
        }

        String getFilename() {
            return this.name.toLowerCase().replaceAll(' ', '-')
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
        String ref
        String repository

        Component(Map data) {
            this.name = data.name
            this.ref = data.ref
            this.repository = data.repository
        }

    }

    Build build
    Ci ci
    Components components
    Double schemaVersion

    InputManifest(Map data) {
        this.schemaVersion = data."schema-version".toDouble()
        this.build = new InputManifest.Build(data.build)
        this.ci = data.ci ? new InputManifest.Ci(data.ci) : null
        this.components = new InputManifest.Components(data.components)
    }

    String getSHAsRoot(String jobName) {
        return [
            jobName,
            this.build.version,
            'shas'
        ].join("/")
    }

    public ArrayList getNames() {
        def componentsName = []
        this.components.each { key, value -> componentsName.add(key) }
        return componentsName
    }

    public String getRepo(String name) {
        return this.components.get(name).repository
    }

}
