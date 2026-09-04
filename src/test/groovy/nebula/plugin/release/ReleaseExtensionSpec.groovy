/*
 * Copyright 2014-2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nebula.plugin.release

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class ReleaseExtensionSpec extends Specification {
    def 'adding a release branch pattern preserves the default patterns'() {
        given:
        Project project = ProjectBuilder.builder().build()
        ReleaseExtension extension = project.extensions.create('nebulaRelease', ReleaseExtension)

        when:
        extension.addReleaseBranchPattern(/develop/)

        then:
        extension.releaseBranchPatterns.get() == [
            /master/,
            /HEAD/,
            /main/,
            /(release(-|\/))?\d+(\.\d+)?\.x/,
            /v?\d+\.\d+\.\d+/,
            /develop/
        ] as Set<String>
    }

    def 'setting release branch patterns replaces the default patterns'() {
        given:
        Project project = ProjectBuilder.builder().build()
        ReleaseExtension extension = project.extensions.create('nebulaRelease', ReleaseExtension)

        when:
        extension.releaseBranchPatterns.set([/develop/])

        then:
        extension.releaseBranchPatterns.get() == [/develop/] as Set<String>
    }
}
