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

import nebula.plugin.bintray.BintrayPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.plugins.PublishingPlugin
import spock.lang.Unroll

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

        git.tag.add(name: 'v0.0.1')
        git.commit(message: 'Another commit')
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

    def 'tasks does not fail'() {
        given:
        buildFile << """\
            allprojects {
                ${applyPlugin(PublishingPlugin)}
                ${applyPlugin(BintrayPlugin)}
            }
        """.stripIndent()
        when:
        runTasksSuccessfully('tasks', '--all')

        then:
        noExceptionThrown()
    }

    def 'tasks task does not fail with our publishing plugin'() {
        buildFile << """
            buildscript {
                repositories { jcenter() }
                dependencies {
                    classpath 'com.netflix.nebula:nebula-publishing-plugin:4.4.4'
                }
            }

            allprojects {
                ${applyPlugin(PublishingPlugin)}
                ${applyPlugin(BintrayPlugin)}
            }

            subprojects { sub ->
                ${applyPlugin(BintrayPlugin)}
                apply plugin: 'nebula.ivy-publish'
                apply plugin: 'nebula.javadoc-jar'
                apply plugin: 'nebula.source-jar'


                publishing {
                    repositories {
                        ivy {
                            name 'localIvy'
                            url 'build/localivy'
                        }
                    }
                }

                sub.tasks.artifactoryPublish.dependsOn ":\${sub.name}:generateDescriptorFileForNebulaIvyPublication"
            }
        """.stripIndent()

        when:
        runTasksSuccessfully('tasks', '--all')

        then:
        noExceptionThrown()

        when:
        def r = runTasksSuccessfully('snapshot', '-m')

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
        def results = runTasksSuccessfully(task)

        then:
        originalRemoteHeadCommit == originGit.head().abbreviatedId

        where:
        task << ['devSnapshot', 'snapshot']
    }
}
