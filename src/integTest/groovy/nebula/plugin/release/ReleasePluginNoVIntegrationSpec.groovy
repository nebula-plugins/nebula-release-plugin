/*
 * Copyright 2014-2019 Netflix, Inc.
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

import com.github.zafarkhaja.semver.Version
import nebula.plugin.release.git.opinion.TimestampUtil
import org.ajoberstar.grgit.Tag
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Unroll

class ReleasePluginNoVIntegrationSpec extends GitVersioningIntegrationTestKitSpec {
    @Override
    def setupBuild() {
        buildFile << """
            plugins {
                id 'com.netflix.nebula.release'
                id 'java'
            }

            ext.dryRun = true
            group = 'test'
            
            release {
                tagStrategy{
                    setPrefixNameWithV(false)
                }
            }

            task showVersion {
                doLast {
                    logger.lifecycle "Version in task: \${version.toString()}"
                }
            }
        """.stripIndent()

        git.add(patterns: ['build.gradle', '.gitignore'] as Set)
    }

    def 'choose candidate development version'() {
        git.tag.add(name: '2.2.0-rc.1')

        when:
        def version = inferredVersionForTask('devSnapshot')

        then:
        version == dev('2.2.0-dev.0+')
    }

    def 'choose no rc in snapshot version'() {
        git.tag.add(name: '2.2.0-rc.1')

        when:
        def version = inferredVersionForTask('immutableSnapshot')

        then:
        version.toString().startsWith("2.2.0-snapshot." + getUtcDateForComparison())
    }

    def 'candidate release creates tag'() {
        when:
        inferredVersionForTask('candidate')

        then:
        originGit.tag.list()*.name.contains('0.1.0-rc.1')
    }

    def 'final release creates tag'() {
        when:
        inferredVersionForTask('final')

        then:
        originGit.tag.list()*.name.contains('0.1.0')
    }

    def 'final release log'() {
        when:
        inferredVersionForTask('final')

        then:
        originGit.tag.list().find { it.name == '0.1.0' }.fullMessage
    }

    def 'create release on git-flow style branch'() {
        def twoX = 'release/2.x'
        git.tag.add(name: '1.0.0')
        git.branch.add(name: twoX)
        git.push(all: true, tags: true)
        git.branch.change(name: twoX, startPoint: "origin/${twoX}".toString())
        git.checkout(branch: twoX)

        when:
        inferredVersionForTask('final')

        then:
        originGit.tag.list()*.name.contains('2.0.0')
    }

    def 'create release on git-flow style branch from within travis context'() {
        def twoX = 'release/2.x'
        git.tag.add(name: '1.0.0')
        git.branch.add(name: twoX)
        git.push(all: true, tags: true)
        git.branch.change(name: twoX, startPoint: "origin/${twoX}".toString())
        git.checkout(branch: twoX)
        def commit = git.head()
        git.checkout(branch: '2.x', startPoint: commit.id, createBranch: true)

        when:
        Version version = inferredVersionForTask('snapshot', '-Prelease.travisci=true', '-Prelease.travisBranch=2.x')

        then:
        version.toString() == '2.0.0-SNAPSHOT'
    }

    def 'create new major_minor release branch and have version respected'() {
        def oneThreeX = '1.3.x'
        git.tag.add(name: '1.2.2')
        git.branch.add(name: oneThreeX)
        git.push(all: true)
        git.branch.change(name: oneThreeX, startPoint: "origin/${oneThreeX}".toString())
        git.checkout(branch: oneThreeX)

        when:
        def version = inferredVersionForTask('devSnapshot')

        then:
        version == dev('1.3.0-dev.0+')
    }

    def 'create new major_minor release branch and have version respected - immutableSnapshot'() {
        def oneThreeX = '1.3.x'
        git.tag.add(name: '1.2.2')
        git.branch.add(name: oneThreeX)
        git.push(all: true)
        git.branch.change(name: oneThreeX, startPoint: "origin/${oneThreeX}".toString())
        git.checkout(branch: oneThreeX)

        when:
        def version = inferredVersionForTask('immutableSnapshot')

        then:
        version.toString().startsWith("1.3.0-snapshot." + getUtcDateForComparison())
    }

    def 'release a final from new major_minor release branch and have version respected'() {
        def oneThreeX = 'release/1.3.x'
        git.tag.add(name: '1.2.2')
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
        git.tag.add(name: '1.2.2')
        git.branch.add(name: oneThreeX)
        git.push(all: true)
        git.branch.change(name: oneThreeX, startPoint: "origin/${oneThreeX}".toString())
        git.checkout(branch: oneThreeX)
        git.tag.add(name: '1.3.0')
        git.push(all: true)

        when:
        def version = inferredVersionForTask('final')

        then:
        version == normal('1.3.1')
    }

    def 'have a good error message for specific non-semantic versions'() {
        def oneThree = 'release/1.3'
        git.tag.add(name: '1.2.2')
        git.branch.add(name: oneThree)
        git.push(all: true)
        git.branch.change(name: oneThree, startPoint: "origin/${oneThree}".toString())
        git.checkout(branch: oneThree)

        when:
        def results = runTasksAndFail('build')

        then:
        outputContains(results, 'Branches with pattern release/<version> are used to calculate versions. The version must be of form: <major>.x, <major>.<minor>.x, or <major>.<minor>.<patch>')
    }

    def 'use last tag'() {
        git.tag.add(name: '42.5.3')

        when:
        runTasks('final', '-Prelease.useLastTag=true')

        then:
        new File(projectDir, "build/libs/${moduleName}-42.5.3.jar").exists()
    }

    def 'useLastTag errors out if there is another commit since tag'() {
        git.tag.add(name: '42.5.3')
        new File(projectDir, "foo").text = "Hi"
        git.add(patterns: ['foo'] as Set)
        git.commit(message: 'Something got committed')

        when:
        def result = runTasksAndFail('final', '-Prelease.useLastTag=true')

        then:
        result.output.contains 'Current commit does not have a tag'
        !new File(projectDir, "build/libs/${moduleName}-42.5.3.jar").exists()
    }

    def 'useLastTag errors out if there is a tag in incorrect format'() {
        git.tag.add(name: '42.5.3')
        new File(projectDir, "foo").text = "Hi"
        git.add(patterns: ['foo'] as Set)
        git.commit(message: 'Something got committed')
        git.tag.add(name: '42.5.4-rc.01')

        when:
        def result = runTasksAndFail('candidate', '-Prelease.useLastTag=true')

        then:
        result.output.contains 'Current commit has following tags: [42.5.4-rc.01] but they were not recognized as valid versions'
    }

    def 'use last tag for rc'() {
        git.tag.add(name: '3.1.2-rc.1')

        when:
        runTasks('candidate', '-Prelease.useLastTag=true')

        then:
        new File(projectDir, "build/libs/${moduleName}-3.1.2-rc.1.jar").exists()
    }

    def 'using tag 3.1.2-rc.1 fails when running final'() {
        git.tag.add(name: "3.1.2-rc.1")

        when:
        def result = runTasksAndFail('final', '-Prelease.useLastTag=true')

        then:
        result.output.contains "Current tag (3.1.2-rc.1) does not appear to be a final version"
        !new File(projectDir, "build/libs/${moduleName}-3.1.2-rc.1.jar").exists()
    }

    def 'useLastTag uses release tag when running "final"'() {
        git.tag.add(name: "3.1.2-rc.1")
        git.tag.add(name: "3.1.2")

        when:
        runTasks('final', '-Prelease.useLastTag=true')

        then:
        !new File(projectDir, "build/libs/${moduleName}-3.1.2-rc.1.jar").exists()
        new File(projectDir, "build/libs/${moduleName}-3.1.2.jar").exists()
    }

    def 'useLastTag ignores rc tag when there is a release tag on the commit and running "candidate"'() {
        git.tag.add(name: "3.1.2-rc.1")
        git.tag.add(name: "3.1.2")

        when:
        def result = runTasksAndFail('candidate', '-Prelease.useLastTag=true')

        then:
        result.output.contains "Current tag (3.1.2) does not appear to be a pre-release version. A pre-release version MAY be denoted by appending a hyphen and a series of dot separated identifiers immediately following the patch version. For more information, please refer to https://semver.org/"
        !new File(projectDir, "build/libs/${moduleName}-3.1.2-rc.1.jar").exists()
        !new File(projectDir, "build/libs/${moduleName}-3.1.2.jar").exists()
    }

    def 'useLastTag succeeds when release stage is not supplied for final tag'() {
        git.tag.add(name: '42.5.3')

        when:
        runTasks('assemble', '-Prelease.useLastTag=true')

        then:
        new File(projectDir, "build/libs/${moduleName}-42.5.3.jar").exists()
    }

    def 'useLastTag succeeds when release stage is not supplied for rc tag'() {
        git.tag.add(name: '3.1.2-rc.1')

        when:
        runTasks('assemble', '-Prelease.useLastTag=true')

        then:
        new File(projectDir, "build/libs/${moduleName}-3.1.2-rc.1.jar").exists()
    }

    def 'useLastTag succeeds when release stage is not supplied for rc tag and devSnapshot tag'() {
        git.tag.add(name: '3.1.2-rc.1')
        git.tag.add(name: "${dev('0.1.0-dev.3+').toString()}")

        when:
        runTasks('assemble', '-Prelease.useLastTag=true')

        then:
        new File(projectDir, "build/libs/${moduleName}-3.1.2-rc.1.jar").exists()
    }

    def 'useLastTag should release with semantically most significant tag'() {
        git.tag.add(name: '3.1.2-rc.1')
        git.tag.add(name: '3.1.2-rc.2')
        git.tag.add(name: '3.1.2')

        when:
        runTasks('assemble', '-Prelease.useLastTag=true')

        then:
        new File(projectDir, "build/libs/${moduleName}-3.1.2.jar").exists()
    }

    def 'useLastTag should release with semantically most significant tag for RCs'() {
        git.tag.add(name: '3.1.2-rc.1')
        git.tag.add(name: '3.1.2-rc.2')

        when:
        runTasks('assemble', '-Prelease.useLastTag=true')

        then:
        new File(projectDir, "build/libs/${moduleName}-3.1.2-rc.2.jar").exists()
    }

    def 'useLastTag shows version selection with debug enabled'() {
        git.tag.add(name: '42.5.3')

        when:
        def result = runTasks('final', '-Prelease.useLastTag=true', '--debug')

        then:
        new File(projectDir, "build/libs/${moduleName}-42.5.3.jar").exists()
        result.output.contains('Using version 42.5.3 with final release strategy')
    }

    def 'useLastTag shows version selection with debug enabled - no release strategy selected'() {
        git.tag.add(name: '42.5.3')

        when:
        def result = runTasks('assemble', '-Prelease.useLastTag=true', '--debug')

        then:
        new File(projectDir, "build/libs/${moduleName}-42.5.3.jar").exists()
        result.output.contains("Note: It is recommended to supply a release strategy of <snapshot|immutableSnapshot|devSnapshot|candidate|final> to make 'useLastTag' most explicit. Please add one to your list of tasks.")
        result.output.contains('Using version 42.5.3 with a non-supplied release strategy')
    }

    def 'using tag 3.1.2 fails when running candidate'() {
        git.tag.add(name: "3.1.2")

        when:
        def result = runTasksAndFail('candidate', '-Prelease.useLastTag=true')

        then:
        result.output.contains "Current tag (3.1.2) does not appear to be a pre-release version. A pre-release version MAY be denoted by appending a hyphen and a series of dot separated identifiers immediately following the patch version. For more information, please refer to https://semver.org/"
        !new File(projectDir, "build/libs/${moduleName}-3.1.2.jar").exists()
    }

    def 'able to release with the override of version calculation'() {
        when:
        runTasks('final', '-Prelease.version=42.5.0')

        then:
        new File(projectDir, "build/libs/${moduleName}-42.5.0.jar").exists()
        originGit.tag.list()*.name.contains('42.5.0')
    }

    def 'able to release from hash and push tag'() {
        given:
        buildFile << '''\
            nebulaRelease {
                allowReleaseFromDetached = true    
            }
            '''.stripIndent()
        git.add(patterns: ['build.gradle'])
        git.commit(message: 'configure skip branch checks')
        git.tag.add(name: '0.1.0')
        new File(projectDir, 'test.txt').text = 'test'
        git.add(patterns: ['test.txt'])
        git.commit(message: 'Add file')
        git.push(all: true)

        def commit = git.head()
        git.checkout(branch: 'release', startPoint: commit, createBranch: true)

        when:
        def version = inferredVersionForTask('final')

        then:
        version == normal('0.2.0')
        originGit.tag.list()*.name.contains('0.2.0')

        Tag tag = originGit.tag.list().find { it.commit == commit }
        tag.commit.abbreviatedId == commit.abbreviatedId

        originGit.branch.list().size() == 1
    }

    def 'Can release final version with + not considered as pre-release'() {
        buildFile << '''\
            nebulaRelease {
                allowReleaseFromDetached = true    
            }
            '''.stripIndent()
        git.add(patterns: ['build.gradle'])
        git.commit(message: 'configure skip branch checks')
        git.tag.add(name: "3.1.2")
        new File(projectDir, 'test.txt').text = 'test'
        git.add(patterns: ['test.txt'])
        git.commit(message: 'Add file')
        git.push(all: true)
        git.tag.add(name: "3.1.2+release2")

        when:
        runTasks('final', '-Prelease.useLastTag=true')

        then:
        !new File(projectDir, "build/libs/${moduleName}-3.1.2.jar").exists()
        new File(projectDir, "build/libs/${moduleName}-3.1.2+release2.jar").exists()
    }

    def 'Can not release final version with - because it is a pre-release'() {
        buildFile << '''\
            nebulaRelease {
                allowReleaseFromDetached = true    
            }
            '''.stripIndent()
        git.add(patterns: ['build.gradle'])
        git.commit(message: 'configure skip branch checks')
        git.tag.add(name: "3.1.2")
        new File(projectDir, 'test.txt').text = 'test'
        git.add(patterns: ['test.txt'])
        git.commit(message: 'Add file')
        git.push(all: true)
        git.tag.add(name: "3.1.2-release2")

        when:
        def result = runTasksAndFail('final', '-Prelease.useLastTag=true')

        then:
        result.output.contains 'Current tag (3.1.2-release2) does not appear to be a final version'
    }

    @Unroll
    def 'useLastTag errors out if version has invalid number of digits - #version'() {
        git.tag.add(name: "$version")

        when:
        def result = runTasksAndFail('final', '-Prelease.useLastTag=true')

        then:
        result.output.contains "Current commit has following tags: [${version}] but they were not recognized as valid versions"
        !new File(projectDir, "build/libs/${moduleName}-${version}.jar").exists()

        where:
        version << [
                '42.5.3.5',
                '42.5.3.5.3',
                '42.5.3.1-rc.1',
                '42.5.3.1.4-rc.1',
        ]
    }

    @Unroll
    def 'release with the override of version calculation errors out if version has invalid number of digits - #version'() {
        when:
        def result = runTasksAndFail('final', "-Prelease.version=${version}")

        then:
        result.output.contains "Supplied release.version ($version) is not valid per semver spec. For more information, please refer to https://semver.org/"
        !new File(projectDir, "build/libs/${moduleName}-${version}.jar").exists()
        !originGit.tag.list()*.name.contains("${version}")

        where:
        version << [
                '42.5.3.5',
                '42.5.3.5.3',
                '42.5.3.1-rc.1',
                '42.5.3.1.4-rc.1',
        ]
    }

    def 'final release using main branch'() {
        git.checkout(branch: 'main', createBranch: true)

        when:
        def result = runTasks('final', '-i')

        then:
        result.task(':final').outcome == TaskOutcome.SUCCESS
        result.output.contains('Tagging repository as 0.1.0')
        result.output.contains('Pushing changes in 0.1.0 to origin')
    }

    def 'Can create devSnapshot with scope patch if candidate for next minor is present'() {
        git.tag.add(name: '3.1.2')
        git.tag.add(name: '3.2.0-rc.1')
        git.tag.add(name: '3.2.0-rc.2')

        when:
        def version =  inferredVersionForTask('devSnapshot', '-Prelease.scope=patch')

        then:
        version == dev('3.1.3-dev.0+')
    }

    def 'Can create devSnapshot with scope patch if candidate for next minor is present - immutable snapshot'() {
        replaceDevWithImmutableSnapshot()
        git.tag.add(name: '3.1.2')
        git.tag.add(name: '3.2.0-rc.1')
        git.tag.add(name: '3.2.0-rc.2')

        when:
        def version =  inferredVersionForTask('devSnapshot', '-Prelease.scope=patch')

        then:
        version.toString().startsWith("3.1.3-snapshot." + getUtcDateForComparison())
    }

    private void replaceDevWithImmutableSnapshot() {
        new File(buildFile.parentFile, "gradle.properties").text = """
nebula.release.features.replaceDevWithImmutableSnapshot=true
"""
    }

    static outputContains(BuildResult result, String substring) {
        return result.output.contains(substring)
    }

    private static String getUtcDateForComparison() {
        return TimestampUtil.getUTCFormattedTimestamp().take(8)
    }
}
