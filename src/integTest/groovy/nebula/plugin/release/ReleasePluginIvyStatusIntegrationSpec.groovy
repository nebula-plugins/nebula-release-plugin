/*
 * Copyright 2015 Netflix, Inc.
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
import nebula.test.functional.ExecutionResult
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.ivy.plugins.IvyPublishPlugin

class ReleasePluginIvyStatusIntegrationSpec extends GitVersioningIntegrationSpec {

    public static final String REPO_LOCATION = 'build/ivytest'

    @Override
    def setupBuild() {
        buildFile << """
            ext.dryRun = true
            group = 'test'
            ${applyPlugin(ReleasePlugin)}
            ${applyPlugin(JavaPlugin)}
            ${applyPlugin(IvyPublishPlugin)}

            publishing {
                repositories {
                    ivy {
                        name 'ivytest'
                        url '$REPO_LOCATION'
                    }
                }
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }

            tasks.release.dependsOn 'publishIvyPublicationToIvytestRepository'
        """.stripIndent()

        settingsFile << '''\
            rootProject.name='statuscheck'
        '''.stripIndent()

        git.add(patterns: ['build.gradle', '.gitignore', 'settings.gradle'] as Set)
    }

    def loadIvyFileViaVersionLookup(ExecutionResult result) {
        loadIvyFile(inferredVersion(result.standardOutput, 'statuscheck').toString())
    }

    def loadIvyFile(String version) {
        new XmlSlurper().parse(new File(projectDir, "${REPO_LOCATION}/test/statuscheck/${version}/ivy-${version}.xml"))
    }

    def 'snapshot leaves integration status'() {
        when:
        def result = runTasksSuccessfully('snapshot')

        then:
        def xml = loadIvyFileViaVersionLookup(result)
        xml.info.@status == 'integration'
    }

    def 'devSnapshot leaves integration status'() {
        when:
        def result = runTasksSuccessfully('devSnapshot')

        then:
        def xml = loadIvyFileViaVersionLookup(result)
        xml.info.@status == 'integration'
    }

    def 'candidate sets candidate status'() {
        when:
        def result = runTasksSuccessfully('candidate')

        then:
        def xml = loadIvyFileViaVersionLookup(result)
        xml.info.@status == 'candidate'
    }

    def 'final sets release status'() {
        when:
        def result = runTasksSuccessfully('final')

        then:
        def xml = loadIvyFileViaVersionLookup(result)
        xml.info.@status == 'release'
    }
}
