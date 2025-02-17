/*
 * Copyright 2014-2025 Netflix, Inc.
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

class ReleasePluginMultiprojectNoRootPluginApplyIntegrationSpec extends GitVersioningIntegrationTestKitSpec {
    @Override
    def setupBuild() {
        buildFile << """\
            plugins {
                id('com.netflix.nebula.release').apply(false)
            }
            subprojects {
                apply plugin: 'com.netflix.nebula.release'
                ext.dryRun = true
                group = 'test'
                apply plugin: 'java'
            }
        """.stripIndent()

        addSubproject('test-release-common', '// hello')
        addSubproject('test-release-client', '''\
            dependencies {
                implementation project(':test-release-common')
            }
        '''.stripIndent())

        git.tag.add(name: 'v0.0.1')
        git.commit(message: 'Another commit')
        git.add(patterns: ['build.gradle', '.gitignore', 'settings.gradle',
                           'test-release-common/build.gradle', 'test-release-client/build.gradle'] as Set)
    }

    def 'fail if com.netflix.nebula.release plugin is not applied at rootProject level'() {
        expect:
        runTasksAndFail('final').output.contains("com.netflix.nebula.release plugin should always be applied at the rootProject level")
    }
}
