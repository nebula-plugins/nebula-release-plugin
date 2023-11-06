/*
 * Copyright 2020 Netflix, Inc.
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

import org.gradle.api.plugins.JavaPlugin
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Unroll

@Ignore("We need to visit the configuration/evaluation of extension")
@Issue("Inconsistent versioning for SNAPSHOT stage https://github.com/nebula-plugins/nebula-release-plugin/issues/187")
class Issue187IntegrationSpec extends GitVersioningIntegrationTestKitSpec {
    @Override
    def setupBuild() {
        buildFile << """
            plugins {
                id 'com.netflix.nebula.release'
                id 'java'
            }
            ext.dryRun = true
            group = 'test'
        
        """.stripIndent()

        git.add(patterns: ['build.gradle', '.gitignore'] as Set)
        git.tag.add(name: 'v0.2.2')
    }

    def 'should infer same version for SNAPSHOT when using build and snapshot task without scope'() {
        buildFile << """
release {
  defaultVersionStrategy = nebula.plugin.release.git.opinion.Strategies.SNAPSHOT
}
"""

        when:
        def resultBuild = runTasks('build')

        then:
        resultBuild.output.contains('version: 0.3.0-SNAPSHOT')

        when:
        def resultSnapshot = runTasks('snapshot')

        then:
        resultSnapshot.output.contains('version: 0.3.0-SNAPSHOT')
    }

    @Unroll
    def 'should infer same version for SNAPSHOT when using build and snapshot task with scope #scope'() {
        buildFile << """
release {
  defaultVersionStrategy = nebula.plugin.release.git.opinion.Strategies.SNAPSHOT
}
"""

        when:
        def resultBuild = runTasks('build', "-Prelease.scope=${scope}")

        then:
        resultBuild.output.contains("version: ${expectedVersion}")

        when:
        def resultSnapshot = runTasks('snapshot', "-Prelease.scope=${scope}")

        then:
        resultSnapshot.output.contains("version: ${expectedVersion}")

        where:
        scope   | expectedVersion
        'major' | '1.0.0-SNAPSHOT'
        'minor' | '0.3.0-SNAPSHOT'
        'patch' | '0.2.3-SNAPSHOT'
    }

    @Unroll
    def 'infer #expectedVersion for #task task when not using snapshot strategy'() {
        when:
        def resultBuild = runTasks('build')

        then:
        resultBuild.output.contains('version: 0.3.0-dev')

        when:
        def resultSnapshot = runTasks(task)

        then:
        resultSnapshot.output.contains("version: $expectedVersion")

        where:
        task          | expectedVersion
        'devSnapshot' | '0.3.0-dev'
        'candidate'   | '0.3.0-rc.1'
        'final'       | '0.3.0'
    }

    @Unroll
    def 'infer release version #expectedReleaseVersion and build  version #expectedBuildVersion for #task task when not using snapshot strategy with scope #scope'() {
        when:
        def resultBuild = runTasks('build', "-Prelease.scope=${scope}")

        then:
        resultBuild.output.contains("version: $expectedBuildVersion")

        when:
        def resultSnapshot = runTasks(task, "-Prelease.scope=${scope}")

        then:
        resultSnapshot.output.contains("version: $expectedReleaseVersion")

        where:
        task          | scope   | expectedReleaseVersion | expectedBuildVersion
        'devSnapshot' | 'patch' | '0.2.3-dev'            | '0.2.3-dev'
        'devSnapshot' | 'minor' | '0.3.0-dev'            | '0.3.0-dev'
        'devSnapshot' | 'major' | '1.0.0-dev'            | '1.0.0-dev'
        'candidate'   | 'patch' | '0.2.3-rc.1'           | '0.2.3-dev'
        'candidate'   | 'minor' | '0.3.0-rc.1'           | '0.3.0-dev'
        'candidate'   | 'major' | '1.0.0-rc.1'           | '1.0.0-dev'
        'final'       | 'patch' | '0.2.3'                | '0.2.3-dev'
        'final'       | 'minor' | '0.3.0'                | '0.3.0-dev'
        'final'       | 'major' | '1.0.0'                | '1.0.0-dev'
    }
}
