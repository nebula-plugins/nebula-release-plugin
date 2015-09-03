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

import org.gradle.api.plugins.JavaPlugin

class ReleasePluginMultiprojectIntegrationSpec extends GitVersioningIntegrationSpec {
    @Override
    def setupBuild() {
        buildFile << """\
            allprojects {
                ${applyPlugin(ReleasePlugin)}
            }

            subprojects {
                ext.dryRun = true
                group = 'test'
                ${applyPlugin(JavaPlugin)}
            }
        """.stripIndent()

        addSubproject('test-release-common', '// hello')
        addSubproject('test-release-client', '''\
            dependencies {
                compile project(':test-release-common')
            }
        '''.stripIndent())

        git.add(patterns: ['build.gradle', '.gitignore', 'settings.gradle',
                           'test-release-common/build.gradle', 'test-release-client/build.gradle'] as Set)
    }

    def 'choose release version'() {
        when:
        def results = runTasksSuccessfully('final')

        then:
        inferredVersion(results.standardOutput) == normal('0.1.0')
        new File(projectDir, 'test-release-common/build/libs/test-release-common-0.1.0.jar').exists()
        new File(projectDir, 'test-release-client/build/libs/test-release-client-0.1.0.jar').exists()
    }

    def 'choose candidate version'() {
        when:
        def results = runTasksSuccessfully('candidate')

        then:
        inferredVersion(results.standardOutput) == normal('0.1.0-rc.1')
        new File(projectDir, 'test-release-common/build/libs/test-release-common-0.1.0-rc.1.jar').exists()
        new File(projectDir, 'test-release-client/build/libs/test-release-client-0.1.0-rc.1.jar').exists()
    }

    def 'build defaults to dev version'() {
        when:
        def results = runTasksSuccessfully('build')

        then:
        inferredVersion(results.standardOutput) == dev('0.1.0-dev.2+')
        new File(projectDir, 'test-release-common/build/libs').list().find {
            it =~ /test-release-common-0\.1\.0-dev\.2\+/
        } != null
        new File(projectDir, 'test-release-client/build/libs').list().find {
            it =~ /test-release-client-0\.1\.0-dev\.2\+/
        } != null
    }

    def 'build defaults to dev version, non-standard branch name included in version string'() {
        git.checkout(branch: 'testexample', createBranch: true)

        when:
        def results = runTasksSuccessfully('build')

        then:
        inferredVersion(results.standardOutput) == dev('0.1.0-dev.2+')
        new File(projectDir, 'test-release-common/build/libs').list().find {
            it =~ /test-release-common-0\.1\.0-dev\.2\+testexample\./
        } != null
        new File(projectDir, 'test-release-client/build/libs').list().find {
            it =~ /test-release-client-0\.1\.0-dev\.2\+testexample\./
        } != null
    }
}
