/*
 * Copyright 2014-2015 Netflix, Inc.
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

import nebula.test.ProjectSpec
import nebula.plugin.release.git.base.ReleasePluginExtension

class OverrideStrategiesSpec extends ProjectSpec {
    def 'able to set via gradle property'() {
        setup:
        project.ext.set('release.version', '42.5.0')

        when:
        project.plugins.apply(ReleasePlugin)
        def releaseExtension = project.extensions.create('release', ReleasePluginExtension, project)
        releaseExtension.with {
            versionStrategy new OverrideStrategies.GradlePropertyStrategy(project, 'release.version')
        }

        then:
        project.version.toString() == '42.5.0'
    }

    def 'able to set via gradle property and sanitize'() {
        setup:
        project.ext.set('release.version', '42.5.0-rc.1+feature')
        project.ext.set('release.sanitizeVersion', true)

        when:
        project.plugins.apply(ReleasePlugin)
        def releaseExtension = project.extensions.create('release', ReleasePluginExtension, project)
        releaseExtension.with {
            versionStrategy new OverrideStrategies.GradlePropertyStrategy(project, 'release.version')
        }

        then:
        project.version.toString() == '42.5.0-rc.1.feature'
    }
}
