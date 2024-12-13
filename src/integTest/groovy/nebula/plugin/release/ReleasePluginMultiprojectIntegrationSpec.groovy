/*
 * Copyright 2014-2016 Netflix, Inc.
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

import spock.lang.Ignore
import spock.lang.Unroll

class ReleasePluginMultiprojectIntegrationSpec extends GitVersioningIntegrationTestKitSpec {
    @Override
    def setupBuild() {
        buildFile << """\
            plugins {
                id 'com.netflix.nebula.release'
            }
            allprojects {
                apply plugin: 'com.netflix.nebula.release'
            }

            subprojects {
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

    def 'choose release version'() {
        when:
        def results = runTasks('final')

        then:
        inferredVersion(results.output) == normal('0.1.0')
        new File(projectDir, 'test-release-common/build/libs/test-release-common-0.1.0.jar').exists()
        new File(projectDir, 'test-release-client/build/libs/test-release-client-0.1.0.jar').exists()
    }

    def 'choose candidate version'() {
        when:
        def results = runTasks('candidate')

        then:
        inferredVersion(results.output) == normal('0.1.0-rc.1')
        new File(projectDir, 'test-release-common/build/libs/test-release-common-0.1.0-rc.1.jar').exists()
        new File(projectDir, 'test-release-client/build/libs/test-release-client-0.1.0-rc.1.jar').exists()
    }

    def 'build defaults to dev version'() {
        when:
        def results = runTasks('build')

        then:
        inferredVersion(results.output) == dev('0.1.0-dev.2+')
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
        def results = runTasks('build')

        then:
        inferredVersion(results.output) == dev('0.1.0-dev.2+')
        new File(projectDir, 'test-release-common/build/libs').list().find {
            it =~ /test-release-common-0\.1\.0-dev\.2\+testexample\./
        } != null
        new File(projectDir, 'test-release-client/build/libs').list().find {
            it =~ /test-release-client-0\.1\.0-dev\.2\+testexample\./
        } != null
    }

    def 'tasks does not fail'() {
        given:
        buildFile << """\
            allprojects {
                apply plugin: 'org.gradle.publishing'
                apply plugin: 'java'
            }
        """.stripIndent()
        when:
        runTasks('tasks', '--all')

        then:
        noExceptionThrown()
    }

    @Ignore("Revisit this once publihsing plugin is configuration cache ready")
    def 'tasks task does not fail with our publishing plugin'() {
        buildFile << """
            buildscript {
                repositories { mavenCentral() }
                dependencies {
                    classpath 'com.netflix.nebula:nebula-publishing-plugin:20.3.0'
                }
            }

            allprojects {
                apply plugin: 'org.gradle.publishing'
                apply plugin: 'java'
            }

            subprojects { sub ->
                apply plugin: 'com.netflix.nebula.ivy-publish'
                apply plugin: 'com.netflix.nebula.javadoc-jar'
                apply plugin: 'com.netflix.nebula.source-jar'


                publishing {
                    repositories {
                        ivy {
                            name 'localIvy'
                            url = 'build/localivy'
                        }
                    }
                }
            }
        """.stripIndent()

        when:
        runTasks('tasks', '--all', '--warning-mode', 'all')

        then:
        noExceptionThrown()

        when:
        def r = runTasks('snapshot', '-m')

        then:
        noExceptionThrown()
        def output = r.standardOutput
        output.contains(':release SKIPPED')
        output.contains(':test-release-common:generateDescriptorFileForNebulaIvyPublication SKIPPED')
    }

    @Unroll('multiproject release task does not push for #task')
    def 'multiproject release task does not push'() {
        given:
        String originalRemoteHeadCommit = originGit.head().abbreviatedId

        buildFile << '// add a comment'
        git.add(patterns: ['build.gradle'])
        git.commit(message: 'commenting build.gradle')

        when:
        def results = runTasks(task)

        then:
        originalRemoteHeadCommit == originGit.head().abbreviatedId

        where:
        task << ['devSnapshot', 'snapshot']
    }
}
