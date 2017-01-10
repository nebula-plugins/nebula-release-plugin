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

import nebula.plugin.bintray.NebulaBintrayPublishingPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.internal.impldep.com.amazonaws.util.Throwables

class ReleasePluginIntegrationSpec extends GitVersioningIntegrationSpec {
    @Override def setupBuild() {
        buildFile << """
            ext.dryRun = true
            group = 'test'
            ${applyPlugin(ReleasePlugin)}
            ${applyPlugin(JavaPlugin)}

            task showVersion << {
                logger.lifecycle "Version in task: \${version.toString()}"
            }
        """.stripIndent()

        git.add(patterns: ['build.gradle', '.gitignore'] as Set)
    }

    def 'build defaults to dev version string'() {
        when:
        def version = inferredVersionForTask('build')

        then:
        version == dev('0.1.0-dev.2+')
    }

    def 'build on non standard branch appends name to dev version string'() {
        git.checkout(branch: 'testexample', createBranch: true)

        when:
        def version = inferredVersionForTask('build')

        then:
        version == dev('0.1.0-dev.2+testexample.')
    }

    def 'choose devSnapshot version'() {
        when:
        def version = inferredVersionForTask('devSnapshot')

        then:
        version == dev('0.1.0-dev.2+')
    }

    def 'choose devSnapshot uncommitted version'() {
        given:
        new File(projectDir, 'newfile').createNewFile()

        when:
        def version = inferredVersionForTask('build')

        then:
        version == dev('0.1.0-dev.2.uncommitted+')
    }

    def 'use maven style snapshot string'() {
        when:
        def version = inferredVersionForTask('snapshot')

        then:
        version == normal('0.1.0-SNAPSHOT')
    }

    def 'choose candidate version'() {
        when:
        def version = inferredVersionForTask('candidate')

        then:
        version == normal('0.1.0-rc.1')
    }

    def 'choose candidate development version'() {
        git.tag.add(name: 'v2.2.0-rc.1')

        when:
        def version = inferredVersionForTask('build')

        then:
        version == dev('2.2.0-rc.1.dev.0+')
    }

    def 'multiple candidate releases will increment rc number'() {
        runTasksSuccessfully('candidate')

        when:
        def version = inferredVersionForTask('candidate')

        then:
        version == normal('0.1.0-rc.2')
    }

    def 'candidate release creates tag'() {
        when:
        inferredVersionForTask('candidate')

        then:
        originGit.tag.list()*.name.contains('v0.1.0-rc.1')
    }

    def 'choose release version'() {
        when:
        def version = inferredVersionForTask('final')

        then:
        version == normal('0.1.0')
    }

    def 'choose release version, update patch'() {
        when:
        def version = inferredVersionForTask('final', '-Prelease.scope=patch')

        then:
        version == normal('0.0.1')
    }

    def 'choose release version, update major'() {
        when:
        def version = inferredVersionForTask('final', '-Prelease.scope=major')

        then:
        version == normal('1.0.0')
    }

    def 'multiple final releases with defaults will increment minor number'() {
        runTasksSuccessfully('final')

        when:
        def version = inferredVersionForTask('final')

        then:
        version == normal('0.2.0')
    }

    def 'final release creates tag'() {
        when:
        inferredVersionForTask('final')

        then:
        originGit.tag.list()*.name.contains('v0.1.0')
    }

    def 'release does not run if tests fail'() {
        buildFile << """
            ${applyPlugin(NebulaBintrayPublishingPlugin)}

            repositories { jcenter() }
            dependencies {
                testCompile 'junit:junit:4.12'
            }
        """.stripIndent()

        writeUnitTest(true)

        git.add(patterns: ['build.gradle', 'src/test/java/nebula/HelloWorldTest.java'])
        git.commit(message: 'Add breaking test')

        when:
        def results = runTasksWithFailure('final')

        then:
        results.wasExecuted('test')
        !results.wasExecuted('release')
    }

    def 'final release log'() {
        when:
        inferredVersionForTask('final')

        then:
        String message = originGit.tag.list().find { it.name == 'v0.1.0' }.fullMessage
        message.contains 'Release of 0.1.0'
        message.find(/- [a-f0-9]{40}: Setup/)
    }

    def 'create new major release branch have branch name respected on version'() {
        def oneX = '1.x'
        git.branch.add(name: oneX)
        git.push(all: true)
        git.branch.change(name: oneX, startPoint: "origin/${oneX}".toString())
        git.checkout(branch: oneX)

        when:
        def version = inferredVersionForTask('devSnapshot')

        then:
        version.toString() == dev('1.0.0-dev.2+').toString()
    }

    def 'create new major release branch in git-flow style and have branch name respected on version'() {
        def oneX = 'release/1.x'
        git.branch.add(name: oneX)
        git.push(all: true)
        git.branch.change(name: oneX, startPoint: "origin/${oneX}".toString())
        git.checkout(branch: oneX)

        when:
        def version = inferredVersionForTask('devSnapshot')

        then:
        version.toString() == dev('1.0.0-dev.2+').toString()
    }

    def 'create release on git-flow style branch'() {
        def twoX = 'release/2.x'
        git.tag.add(name: 'v1.0.0')
        git.branch.add(name: twoX)
        git.push(all: true, tags: true)
        git.branch.change(name: twoX, startPoint: "origin/${twoX}".toString())
        git.checkout(branch: twoX)

        when:
        inferredVersionForTask('final')

        then:
        originGit.tag.list()*.name.contains('v2.0.0')
    }

    def 'create new major_minor release branch and have version respected'() {
        def oneThreeX = '1.3.x'
        git.tag.add(name: 'v1.2.2')
        git.branch.add(name: oneThreeX)
        git.push(all: true)
        git.branch.change(name: oneThreeX, startPoint: "origin/${oneThreeX}".toString())
        git.checkout(branch: oneThreeX)

        when:
        def version = inferredVersionForTask('devSnapshot')

        then:
        version == dev('1.3.0-dev.0+')
    }

    def 'release a final from new major_minor release branch and have version respected'() {
        def oneThreeX = 'release/1.3.x'
        git.tag.add(name: 'v1.2.2')
        git.branch.add(name: oneThreeX)
        git.push(all: true)
        git.branch.change(name: oneThreeX, startPoint: "origin/${oneThreeX}".toString())
        git.checkout(branch: oneThreeX)

        when:
        def version = inferredVersionForTask('final')

        then:
        version == normal('1.3.0')
    }

    def 'release a final existing new major_minor release branch and bump patch as expected'() {
        def oneThreeX = 'release/1.3.x'
        git.tag.add(name: 'v1.2.2')
        git.branch.add(name: oneThreeX)
        git.push(all: true)
        git.branch.change(name: oneThreeX, startPoint: "origin/${oneThreeX}".toString())
        git.checkout(branch: oneThreeX)
        git.tag.add(name: 'v1.3.0')
        git.push(all: true)

        when:
        def version = inferredVersionForTask('final')

        then:
        version == normal('1.3.1')
    }

    def 'have a good error message for specific non-semantic versions'() {
        def oneThree = 'release/1.3'
        git.tag.add(name: 'v1.2.2')
        git.branch.add(name: oneThree)
        git.push(all: true)
        git.branch.change(name: oneThree, startPoint: "origin/${oneThree}".toString())
        git.checkout(branch: oneThree)

        when:
        def results = runTasksWithFailure('build')

        then:
        results.standardError.contains 'Branches with pattern release/<version> are used to calculate versions. The version must be of form: <major>.x, <major>.<minor>.x, or <major>.<minor>.<patch>'
    }


    def 'task dependency configuration is read from extension'() {
        buildFile << '''
            task placeholderTask

            tasks.release.dependsOn placeholderTask
        '''.stripIndent()

        git.add(patterns: ['build.gradle'] as Set)
        git.commit(message: 'Setup')
        git.push()

        when:
        def results = runTasksSuccessfully('final')

        then:
        results.wasExecuted('placeholderTask')
    }

    def 'fail final release on non release branch'() {
        git.checkout(branch: 'testexample', createBranch: true)

        when:
        def result = runTasksWithFailure('final')

        then:
        result.failure != null
        result.standardError.contains 'testexample does not match one of the included patterns: [master, HEAD, (release(-|/))?\\d+(\\.\\d+)?\\.x, v?\\d+\\.\\d+\\.\\d+]'
    }

    def 'version includes branch name on devSnapshot of non release branch'() {
        git.branch.add(name: 'testexample')
        git.push(all: true)
        git.branch.change(name: 'testexample', startPoint: 'origin/testexample')
        git.checkout(branch: 'testexample')

        when:
        def version = inferredVersionForTask('devSnapshot')

        then:
        version.toString() == dev('0.1.0-dev.2+testexample.').toString()
    }

    def 'version includes branch name with stripped off patterns on devSnapshot of non release branch'() {
        git.branch.add(name: 'feature/testexample')
        git.push(all: true)
        git.branch.change(name: 'feature/testexample', startPoint: 'origin/feature/testexample')
        git.checkout(branch: 'feature/testexample')

        when:
        def version = inferredVersionForTask('devSnapshot')

        then:
        version.toString() == dev('0.1.0-dev.2+testexample.').toString()
    }

    def 'version includes branch name with underscores on devSnapshot of non release branch'() {
        git.branch.add(name: 'feature/test_example')
        git.push(all: true)
        git.branch.change(name: 'feature/test_example', startPoint: 'origin/feature/test_example')
        git.checkout(branch: 'feature/test_example')

        when:
        def version = inferredVersionForTask('devSnapshot')

        then:
        version.toString() == dev('0.1.0-dev.2+test.example.').toString()
    }

    def 'fail build on excluded master branch'() {
        buildFile << '''\
            nebulaRelease {
                addExcludeBranchPattern(/^master\$/)
            }
        '''.stripIndent()

        git.add(patterns: ['build.gradle'] as Set)
        git.commit(message: 'Setup')
        git.push()

        when:
        def result = runTasksWithFailure('final')

        then:
        result.failure != null
        result.standardError.contains 'master matched an excluded pattern: [^master\$]'
    }

    def 'use last tag'() {
        git.tag.add(name: 'v42.5.3')

        when:
        runTasksSuccessfully('final', '-Prelease.useLastTag=true')

        then:
        new File(projectDir, "build/libs/${moduleName}-42.5.3.jar").exists()
    }

    def 'use last tag for rc'() {
        git.tag.add(name: 'v3.1.2-rc.1')

        when:
        runTasksSuccessfully('candidate', '-Prelease.useLastTag=true')

        then:
        new File(projectDir, "build/libs/${moduleName}-3.1.2-rc.1.jar").exists()
    }

    def 'skip useLastTag if false'() {
        when:
        runTasksSuccessfully('final', '-Prelease.useLastTag=345')

        then:
        new File(projectDir, "build/libs/${moduleName}-0.1.0.jar").exists()
    }

    def 'able to release with the override of version calculation'() {
        when:
        runTasksSuccessfully('final', '-Prelease.version=42.5.0')

        then:
        new File(projectDir, "build/libs/${moduleName}-42.5.0.jar").exists()
        originGit.tag.list()*.name.contains('v42.5.0')
    }

    def 'error if release.version is set to an empty string'() {
        when:
        def result = runTasksWithFailure('build', '-Prelease.version=')

        then:
        Throwables.getRootCause(result.failure).message == 'Supplied release.version is empty'
    }

    def 'devSnapshot works if default is changed'() {
        buildFile << '''\
            release {
                defaultVersionStrategy = nebula.plugin.release.NetflixOssStrategies.SNAPSHOT
            }
        '''.stripIndent()
        git.add(patterns: ['build.gradle'] as Set)
        git.commit(message: 'Setup')
        git.push()

        when:
        def version = inferredVersionForTask('devSnapshot')

        then:
        version.toString() == dev('0.1.0-dev.3+').toString()
    }

    def 'bintray tasks are wired in'() {
        buildFile << '''\
            buildscript {
                repositories { jcenter() }
                dependencies {
                    classpath 'com.netflix.nebula:nebula-bintray-plugin:3.1.0'
                    classpath 'com.netflix.nebula:nebula-publishing-plugin:4.0.1'
                }
            }
            apply plugin: 'nebula.maven-publish'
            apply plugin: 'nebula.nebula-bintray'
        '''.stripIndent()

        when:
        def results = runTasksSuccessfully('devSnapshot', '-m')

        then:
        results.standardOutput.contains 'bintrayUpload'
        results.standardOutput.contains 'artifactoryPublish'
    }
}
