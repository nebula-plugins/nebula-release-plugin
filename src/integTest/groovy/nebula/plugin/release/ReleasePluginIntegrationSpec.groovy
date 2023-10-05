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
import nebula.test.functional.ExecutionResult
import org.ajoberstar.grgit.Tag
import org.gradle.api.plugins.JavaPlugin
import org.gradle.internal.impldep.com.amazonaws.util.Throwables
import spock.lang.Ignore
import spock.lang.Unroll

class ReleasePluginIntegrationSpec extends GitVersioningIntegrationSpec {
    @Override
    def setupBuild() {
        buildFile << """
            ext.dryRun = true
            group = 'test'
            ${applyPlugin(ReleasePlugin)}
            ${applyPlugin(JavaPlugin)}

            task showVersion {
                doLast {
                    logger.lifecycle "Version in task: \${version.toString()}"
                }
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

    def 'choose immutableSnapshot version if devSnapshot is replaced and devSnapshot is executed'() {
        given:
        replaceDevWithImmutableSnapshot()

        when:
        def version = inferredVersionForTask('devSnapshot')

        then:
        version.toString().startsWith("0.1.0-snapshot." + getUtcDateForComparison())
    }


    def 'choose immutableSnapshot version'() {
        when:
        def version = inferredVersionForTask('immutableSnapshot')

        then:
        version.toString().startsWith("0.1.0-snapshot." + getUtcDateForComparison())
    }

    def 'choose immutableSnapshot version with optional colon (:)'() {
        when:
        def version = inferredVersionForTask(':immutableSnapshot')

        then:
        version.toString().startsWith("0.1.0-snapshot." + getUtcDateForComparison())
    }

    def 'choose devSnapshot uncommitted version'() {
        given:
        new File(projectDir, 'newfile').createNewFile()

        when:
        def version = inferredVersionForTask('build')

        then:
        version == dev('0.1.0-dev.2.uncommitted+')
    }

    def 'choose immutableSnapshot uncommitted version if devSnapshot is replaced and devSnapshot is executed'() {
        given:
        replaceDevWithImmutableSnapshot()
        new File(projectDir, 'newfile').createNewFile()

        when:
        def version = inferredVersionForTask('build')

        then:
        version.toString().startsWith("0.1.0-snapshot." + getUtcDateForComparison())
        version.toString().contains('uncommitted')
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

    def 'choose candidate version with optional colon (:) for root project'() {
        when:
        def version = inferredVersionForTask(':candidate')

        then:
        version == normal('0.1.0-rc.1')
    }

    def 'choose candidate version with optional colon (:) for root project - multi-module'() {
        setup:
        addSubproject('sub1')
        addSubproject('sub2')
        git.add(patterns: ['.'] as Set)
        git.commit(message: "Update file")
        git.push(all: true)

        when:
        def version = inferredVersionForTask(':candidate')

        then:
        version == normal('0.1.0-rc.1')
    }

    def 'choose candidate development version'() {
        git.tag.add(name: 'v2.2.0-rc.1')

        when:
        def version = inferredVersionForTask('devSnapshot')

        then:
        version == dev('2.2.0-dev.0+')
    }

    def 'choose no rc in snapshot version'() {
        git.tag.add(name: 'v2.2.0-rc.1')

        when:
        def version = inferredVersionForTask('immutableSnapshot')

        then:
        version.toString().startsWith("2.2.0-snapshot." + getUtcDateForComparison())
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

    def 'choose release version with optional colon (:) for root project'() {
        when:
        def version = inferredVersionForTask(':final')

        then:
        version == normal('0.1.0')
    }

    def 'choose release version with optional colon (:) for root project - multi-module'() {
        setup:
        addSubproject('sub1')
        addSubproject('sub2')
        git.add(patterns: ['.'] as Set)
        git.commit(message: "Update file")
        git.push(all: true)

        when:
        def version = inferredVersionForTask(':final')

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
            repositories { mavenCentral() }
            dependencies {
                testImplementation 'junit:junit:4.12'
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

    def 'create new major release branch have branch name respected on version - immutableSnapshot'() {
        def oneX = '1.x'
        git.branch.add(name: oneX)
        git.push(all: true)
        git.branch.change(name: oneX, startPoint: "origin/${oneX}".toString())
        git.checkout(branch: oneX)

        when:
        def version = inferredVersionForTask('immutableSnapshot')

        then:
        version.toString().startsWith("1.0.0-snapshot." + getUtcDateForComparison())
    }

    def 'allow create final from a commit when some of its candidates are before the commit'() {
        given:
        def file = new File(projectDir, "test_file.txt")
        file.text = "DUMMY"
        git.add(patterns: ['.'] as Set)
        git.commit(message: "Add file")
        git.push(all: true)
        runTasksSuccessfully('candidate')
        git.branch.add(name: "0.1.x")
        file.text = "Updated dummy"
        git.add(patterns: ['.'] as Set)
        git.commit(message: "Update file")
        git.push(all: true)
        runTasksSuccessfully('candidate')

        when:
        git.checkout(branch: '0.1.x')
        def version = inferredVersionForTask('final')

        then:
        version.toString() == normal('0.1.0').toString()

        when:
        git.checkout(branch: 'master')
        version = inferredVersionForTask('candidate')

        then:
        version.toString() == normal('0.2.0-rc.1').toString()
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

    def 'create new major release branch in git-flow style and have branch name respected on version - immutableSnapshot'() {
        def oneX = 'release/1.x'
        git.branch.add(name: oneX)
        git.push(all: true)
        git.branch.change(name: oneX, startPoint: "origin/${oneX}".toString())
        git.checkout(branch: oneX)

        when:
        def version = inferredVersionForTask('immutableSnapshot')

        then:
        version.toString().startsWith("1.0.0-snapshot." + getUtcDateForComparison())
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

    def 'create release on git-flow style branch from within travis context'() {
        def twoX = 'release/2.x'
        git.tag.add(name: 'v1.0.0')
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

    def 'create new major_minor release branch and have version respected - immutableSnapshot'() {
        def oneThreeX = '1.3.x'
        git.tag.add(name: 'v1.2.2')
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
        outputContains(results, 'Branches with pattern release/<version> are used to calculate versions. The version must be of form: <major>.x, <major>.<minor>.x, or <major>.<minor>.<patch>')
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
        outputContains(result, 'testexample does not match one of the included patterns: [master, HEAD, main, (release(-|/))?\\d+(\\.\\d+)?\\.x, v?\\d+\\.\\d+\\.\\d+]')
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

    def 'version includes branch name on immutableSnapshot of non release branch'() {
        git.branch.add(name: 'testexample')
        git.push(all: true)
        git.branch.change(name: 'testexample', startPoint: 'origin/testexample')
        git.checkout(branch: 'testexample')

        when:
        def version = inferredVersionForTask('immutableSnapshot')

        then:
        version.toString().startsWith("0.1.0-snapshot." + getUtcDateForComparison())
        version.toString().contains('+testexample.')
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

    def 'version includes branch name with stripped off patterns on immutableSnapshot of non release branch'() {
        git.branch.add(name: 'feature/testexample')
        git.push(all: true)
        git.branch.change(name: 'feature/testexample', startPoint: 'origin/feature/testexample')
        git.checkout(branch: 'feature/testexample')

        when:
        def version = inferredVersionForTask('immutableSnapshot')

        then:
        version.toString().startsWith("0.1.0-snapshot." + getUtcDateForComparison())
        version.toString().contains('+testexample.')
        !version.toString().contains('+feature/testexample.')
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

    def 'version includes branch name with underscores on immutableSnapshot of non release branch'() {
        git.branch.add(name: 'feature/test_example')
        git.push(all: true)
        git.branch.change(name: 'feature/test_example', startPoint: 'origin/feature/test_example')
        git.checkout(branch: 'feature/test_example')

        when:
        def version = inferredVersionForTask('immutableSnapshot')

        then:
        version.toString().startsWith("0.1.0-snapshot." + getUtcDateForComparison())
        version.toString().contains('+test.example.')
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
        outputContains(result, 'master matched an excluded pattern: [^master\$]')
    }

    def 'use last tag'() {
        git.tag.add(name: 'v42.5.3')

        when:
        runTasksSuccessfully('final', '-Prelease.useLastTag=true')

        then:
        new File(projectDir, "build/libs/${moduleName}-42.5.3.jar").exists()
    }

    def 'useLastTag errors out if there is another commit since tag'() {
        git.tag.add(name: 'v42.5.3')
        new File(projectDir, "foo").text = "Hi"
        git.add(patterns: ['foo'] as Set)
        git.commit(message: 'Something got committed')

        when:
        def result = runTasksWithFailure('final', '-Prelease.useLastTag=true')

        then:
        result.standardError.contains 'Current commit does not have a tag'
        !new File(projectDir, "build/libs/${moduleName}-42.5.3.jar").exists()
    }

    def 'useLastTag errors out if there is a tag in incorrect format'() {
        git.tag.add(name: 'v42.5.3')
        new File(projectDir, "foo").text = "Hi"
        git.add(patterns: ['foo'] as Set)
        git.commit(message: 'Something got committed')
        git.tag.add(name: 'v42.5.4-rc.01')

        when:
        def result = runTasksWithFailure('candidate', '-Prelease.useLastTag=true')

        then:
        result.standardError.contains 'Current commit has following tags: [v42.5.4-rc.01] but they were not recognized as valid versions'
    }

    def 'use last tag for rc'() {
        git.tag.add(name: 'v3.1.2-rc.1')

        when:
        runTasksSuccessfully('candidate', '-Prelease.useLastTag=true')

        then:
        new File(projectDir, "build/libs/${moduleName}-3.1.2-rc.1.jar").exists()
    }

    def 'using tag v3.1.2-rc.1 fails when running final'() {
        git.tag.add(name: "v3.1.2-rc.1")

        when:
        def result = runTasksWithFailure('final', '-Prelease.useLastTag=true')

        then:
        result.standardError.contains "Current tag (3.1.2-rc.1) does not appear to be a final version"
        !new File(projectDir, "build/libs/${moduleName}-3.1.2-rc.1.jar").exists()
    }

    def 'using v as tag fails when running tasks'() {
        git.tag.add(name: "v3.1.2")
        git.tag.add(name: "v")

        when:
        def result = runTasksWithFailure('final', '-Prelease.useLastTag=true')

        then:
        result.standardError.contains "Tag name 'v' is invalid. 'v' should be use as prefix for semver versions only, example: v1.0.0"
        !new File(projectDir, "build/libs/${moduleName}-3.1.2.jar").exists()
    }

    def 'useLastTag uses release tag when running "final"'() {
        git.tag.add(name: "v3.1.2-rc.1")
        git.tag.add(name: "v3.1.2")

        when:
        def result = runTasksSuccessfully('final', '-Prelease.useLastTag=true')

        then:
        !new File(projectDir, "build/libs/${moduleName}-3.1.2-rc.1.jar").exists()
        new File(projectDir, "build/libs/${moduleName}-3.1.2.jar").exists()
    }

    def 'useLastTag ignores rc tag when there is a release tag on the commit and running "candidate"'() {
        git.tag.add(name: "v3.1.2-rc.1")
        git.tag.add(name: "v3.1.2")

        when:
        def result = runTasksWithFailure('candidate', '-Prelease.useLastTag=true')

        then:
        result.standardError.contains "Current tag (3.1.2) does not appear to be a pre-release version. A pre-release version MAY be denoted by appending a hyphen and a series of dot separated identifiers immediately following the patch version. For more information, please refer to https://semver.org/"
        !new File(projectDir, "build/libs/${moduleName}-3.1.2-rc.1.jar").exists()
        !new File(projectDir, "build/libs/${moduleName}-3.1.2.jar").exists()
    }

    def 'fails when running devSnapshot With useLastTag'() {
        when:
        def result = runTasksWithFailure('devSnapshot', '-Prelease.useLastTag=true')

        then:
        result.standardError.contains "Cannot use useLastTag with snapshot, immutableSnapshot and devSnapshot tasks"
        !new File(projectDir, "build/libs/${moduleName}-3.1.2-rc.1.jar").exists()
    }

    def 'succeeds when running devSnapshot With useLastTag false'() {
        expect:
        runTasksSuccessfully('devSnapshot', '-Prelease.useLastTag=false')
    }

    def 'fails when running immutableSnapshot With useLastTag'() {
        when:
        def result = runTasksWithFailure('immutableSnapshot', '-Prelease.useLastTag=true')

        then:
        result.standardError.contains "Cannot use useLastTag with snapshot, immutableSnapshot and devSnapshot tasks"
        !new File(projectDir, "build/libs/${moduleName}-3.1.2-rc.1.jar").exists()
    }

    def 'succeeds when running immutableSnapshot With useLastTag false'() {
        expect:
        runTasksSuccessfully('immutableSnapshot', '-Prelease.useLastTag=false')
    }

    def 'useLastTag succeeds when release stage is not supplied for final tag'() {
        git.tag.add(name: 'v42.5.3')

        when:
        def result = runTasksSuccessfully('assemble', '-Prelease.useLastTag=true')

        then:
        new File(projectDir, "build/libs/${moduleName}-42.5.3.jar").exists()
    }

    def 'useLastTag succeeds when release stage is not supplied for rc tag'() {
        git.tag.add(name: 'v3.1.2-rc.1')

        when:
        def result = runTasksSuccessfully('assemble', '-Prelease.useLastTag=true')

        then:
        new File(projectDir, "build/libs/${moduleName}-3.1.2-rc.1.jar").exists()
    }

    def 'useLastTag succeeds when release stage is not supplied for rc tag and devSnapshot tag'() {
        git.tag.add(name: 'v3.1.2-rc.1')
        git.tag.add(name: "${dev('0.1.0-dev.3+').toString()}")

        when:
        def result = runTasksSuccessfully('assemble', '-Prelease.useLastTag=true')

        then:
        new File(projectDir, "build/libs/${moduleName}-3.1.2-rc.1.jar").exists()
    }

    def 'useLastTag fails when release stage is not supplied for devSnapshot tag'() {
        git.tag.add(name: "${dev('0.1.0-dev.3+').toString()}")

        when:
        def result = runTasksWithFailure('assemble', '-Prelease.useLastTag=true')

        then:
        result.standardError.contains "Current commit has a snapshot, immutableSnapshot or devSnapshot tag. 'useLastTag' requires a prerelease or final tag."
        !new File(projectDir, "build/libs/${moduleName}-${dev('0.1.0-dev.3+').toString()}.jar").exists()
    }


    def 'useLastTag fails when release stage is not supplied for immutableSnapshot tag'() {
        git.tag.add(name: "${dev('0.1.0-snapshot.220190705103502+').toString()}")

        when:
        def result = runTasksWithFailure('assemble', '-Prelease.useLastTag=true')

        then:
        result.standardError.contains "Current commit has a snapshot, immutableSnapshot or devSnapshot tag. 'useLastTag' requires a prerelease or final tag."
        !new File(projectDir, "build/libs/${moduleName}-${dev('0.1.0-snapshot.220190705103502+').toString()}.jar").exists()
    }

    def 'useLastTag fails when release stage is not supplied for snapshot tag'() {
        git.tag.add(name: "1.2.3-SNAPSHOT")

        when:
        def result = runTasksWithFailure('assemble', '-Prelease.useLastTag=true')

        then:
        result.standardError.contains "Current commit has a snapshot, immutableSnapshot or devSnapshot tag. 'useLastTag' requires a prerelease or final tag."
        !new File(projectDir, "build/libs/${moduleName}-1.2.3-SNAPSHOT.jar").exists()
    }

    def 'useLastTag should release with semantically most significant tag'() {
        git.tag.add(name: 'v3.1.2-rc.1')
        git.tag.add(name: 'v3.1.2-rc.2')
        git.tag.add(name: 'v3.1.2')

        when:
        def result = runTasksSuccessfully('assemble', '-Prelease.useLastTag=true')

        then:
        new File(projectDir, "build/libs/${moduleName}-3.1.2.jar").exists()
    }

    def 'useLastTag should release with semantically most significant tag for RCs'() {
        git.tag.add(name: 'v3.1.2-rc.1')
        git.tag.add(name: 'v3.1.2-rc.2')

        when:
        def result = runTasksSuccessfully('assemble', '-Prelease.useLastTag=true')

        then:
        new File(projectDir, "build/libs/${moduleName}-3.1.2-rc.2.jar").exists()
    }

    def 'useLastTag shows version selection with debug enabled'() {
        git.tag.add(name: 'v42.5.3')

        when:
        def result = runTasksSuccessfully('final', '-Prelease.useLastTag=true', '--debug')

        then:
        new File(projectDir, "build/libs/${moduleName}-42.5.3.jar").exists()
        result.standardOutput.contains('Using version 42.5.3 with final release strategy')
    }

    def 'useLastTag shows version selection with debug enabled - no release strategy selected'() {
        git.tag.add(name: 'v42.5.3')

        when:
        def result = runTasksSuccessfully('assemble', '-Prelease.useLastTag=true', '--debug')

        then:
        new File(projectDir, "build/libs/${moduleName}-42.5.3.jar").exists()
        result.standardOutput.contains("Note: It is recommended to supply a release strategy of <snapshot|immutableSnapshot|devSnapshot|candidate|final> to make 'useLastTag' most explicit. Please add one to your list of tasks.")
        result.standardOutput.contains('Using version 42.5.3 with a non-supplied release strategy')
    }

    def 'succeeds when running snapshot With useLastTag false'() {
        expect:
        runTasksSuccessfully('snapshot', '-Prelease.useLastTag=false')
    }

    def 'fails when running snapshot With useLastTag'() {
        when:
        def result = runTasksWithFailure('snapshot', '-Prelease.useLastTag=true')

        then:
        result.standardError.contains "Cannot use useLastTag with snapshot, immutableSnapshot and devSnapshot tasks"
        !new File(projectDir, "build/libs/${moduleName}-3.1.2-rc.1.jar").exists()
    }

    def 'using tag v3.1.2 fails when running candidate'() {
        git.tag.add(name: "v3.1.2")

        when:
        def result = runTasksWithFailure('candidate', '-Prelease.useLastTag=true')

        then:
        result.standardError.contains "Current tag (3.1.2) does not appear to be a pre-release version. A pre-release version MAY be denoted by appending a hyphen and a series of dot separated identifiers immediately following the patch version. For more information, please refer to https://semver.org/"
        !new File(projectDir, "build/libs/${moduleName}-3.1.2.jar").exists()
    }

    def 'skip useLastTag if false'() {
        when:
        runTasksSuccessfully('final', '-Prelease.useLastTag=345')

        then:
        new File(projectDir, "build/libs/${moduleName}-0.1.0.jar").exists()
    }

    @Ignore("We need to visit the configuration/evaluation of extension")
    def 'use last tag with custom tag strategy'() {
        buildFile << '''\
            release {
              tagStrategy {
                toTagString = { vs -> "version${vs}" }
                parseTag = { tag ->
                  try { com.github.zafarkhaja.semver.Version.valueOf(tag.name[7..-1]) } catch (Exception e) { null }
                }
              }
            }
        '''.stripIndent()
        git.add(patterns: ['build.gradle'] as Set)
        git.commit(message: 'setting tag strategy')

        git.tag.add(name: 'version19.71.1')

        when:
        runTasksSuccessfully('final', '-Prelease.useLastTag=true')

        then:
        new File(projectDir, "build/libs/${moduleName}-19.71.1.jar").exists()
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
                defaultVersionStrategy = nebula.plugin.release.NetflixOssStrategies.SNAPSHOT(project)
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

    def 'able to release from hash and push tag'() {
        given:
        buildFile << '''\
            nebulaRelease {
                allowReleaseFromDetached = true    
            }
            '''.stripIndent()
        git.add(patterns: ['build.gradle'])
        git.commit(message: 'configure skip branch checks')
        git.tag.add(name: 'v0.1.0')
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
        originGit.tag.list()*.name.contains('v0.2.0')

        Tag tag = originGit.tag.list().find { it.commit == commit }
        tag.commit.abbreviatedId == commit.abbreviatedId

        originGit.branch.list().size() == 1
    }

    def 'branches with slashes that do not match specified patterns do not fail builds'() {
        git.checkout(branch: 'dev/robtest', createBranch: true)

        when:
        def version = inferredVersionForTask('devSnapshot')

        then:
        version == dev('0.1.0-dev.2+dev.robtest.')
    }

    def 'branches with dashes that do not match specified patterns do not fail builds'() {
        git.checkout(branch: 'dev-robtest', createBranch: true)

        when:
        def version = inferredVersionForTask('devSnapshot')

        then:
        version == dev('0.1.0-dev.2+dev.robtest.')
    }

    def 'branches with dashes that do not match specified patterns do not fail builds - immutableSnapshot'() {
        git.checkout(branch: 'dev-robtest', createBranch: true)

        when:
        def version = inferredVersionForTask('immutableSnapshot')

        then:
        version.toString().startsWith('0.1.0-snapshot.' + getUtcDateForComparison())
        version.toString().contains('+dev.robtest.')
    }

    def 'Can release final version with + not considered as pre-release'() {
        buildFile << '''\
            nebulaRelease {
                allowReleaseFromDetached = true    
            }
            '''.stripIndent()
        git.add(patterns: ['build.gradle'])
        git.commit(message: 'configure skip branch checks')
        git.tag.add(name: "v3.1.2")
        new File(projectDir, 'test.txt').text = 'test'
        git.add(patterns: ['test.txt'])
        git.commit(message: 'Add file')
        git.push(all: true)
        git.tag.add(name: "v3.1.2+release2")

        when:
        def result = runTasksSuccessfully('final', '-Prelease.useLastTag=true')

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
        git.tag.add(name: "v3.1.2")
        new File(projectDir, 'test.txt').text = 'test'
        git.add(patterns: ['test.txt'])
        git.commit(message: 'Add file')
        git.push(all: true)
        git.tag.add(name: "v3.1.2-release2")

        when:
        def result = runTasksWithFailure('final', '-Prelease.useLastTag=true')

        then:
        result.standardError.contains 'Current tag (3.1.2-release2) does not appear to be a final version'
    }


    @Unroll('release task does not push for #task')
    def 'release task does not push'() {
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

    def 'can release devSnapshot with sanitized version'() {
        when:
        def version = inferredVersionForTask('devSnapshot', '-Prelease.sanitizeVersion=true')

        then:
        version.toString().startsWith('0.1.0-dev.2.')
    }

    @Ignore("We need to visit the configuration/evaluation of extension")
    def 'immutableSnapshot works as default when changed'() {
        buildFile << '''\
            release {
                defaultVersionStrategy = nebula.plugin.release.NetflixOssStrategies.IMMUTABLE_SNAPSHOT(project)
            }
        '''.stripIndent()
        git.add(patterns: ['build.gradle'] as Set)
        git.commit(message: 'Setup')
        git.push()

        when:
        def version = inferredVersionForTask('build')

        then:
        version.toString().startsWith('0.1.0-snapshot.' + getUtcDateForComparison())
    }

    def 'immutableSnapshot works as default when dev is replaced with it'() {
        replaceDevWithImmutableSnapshot()
        git.add(patterns: ['build.gradle'] as Set)
        git.commit(message: 'Setup')
        git.push()

        when:
        def version = inferredVersionForTask('build')

        then:
        version.toString().startsWith('0.1.0-snapshot.' + getUtcDateForComparison())
    }

    def 'does not run prepare as default when releasing'() {
        given:
        def file = new File(projectDir, "test_file.txt")
        file.text = "DUMMY"
        git.add(patterns: ['.'] as Set)
        git.commit(message: "Add file")
        git.push(all: true)
        runTasksSuccessfully('candidate')
        git.branch.add(name: "0.1.x")
        file.text = "Updated dummy"
        git.add(patterns: ['.'] as Set)
        git.commit(message: "Update file")
        git.push(all: true)

        when:
        def result = runTasksSuccessfully('candidate')

        then:
        result.wasSkipped(':prepare')
    }

    def 'executes prepare if checkRemoteBranchOnRelease when releasing'() {
        given:
        buildFile << """
            ext.dryRun = true
            group = 'test'
            ${applyPlugin(ReleasePlugin)}
            ${applyPlugin(JavaPlugin)}

            nebulaRelease { 
                checkRemoteBranchOnRelease = true
            }
        """.stripIndent()
        def file = new File(projectDir, "test_file.txt")
        file.text = "DUMMY"
        git.add(patterns: ['.'] as Set)
        git.commit(message: "Add file")
        git.push(all: true)
        runTasksSuccessfully('candidate')
        git.branch.add(name: "0.1.x")
        file.text = "Updated dummy"
        git.add(patterns: ['.'] as Set)
        git.commit(message: "Update file")
        git.push(all: true)

        when:
        def result = runTasksSuccessfully('candidate', '-Drelease.configurePrepareTaskEnabled=true')

        then:
        result.wasExecuted(':prepare')
        !result.wasSkipped(':prepare')
    }


    def 'fails when branch ends with dash'() {
        given:
        buildFile << """
            ext.dryRun = true
            group = 'test'
            ${applyPlugin(ReleasePlugin)}
            ${applyPlugin(JavaPlugin)}

            nebulaRelease { 
                checkRemoteBranchOnRelease = true
            }
        """.stripIndent()
        def file = new File(projectDir, "test_file.txt")
        file.text = "DUMMY"
        git.add(patterns: ['.'] as Set)
        git.commit(message: "Add file")
        git.push(all: true)
        git.checkout(branch: 'my-branch-with-dash-', createBranch: true)

        when:
        def result = runTasksWithFailure('candidate', '-Drelease.configurePrepareTaskEnabled=true')

        then:
        result.standardError.contains('Nebula Release plugin does not support branches that end with dash (-)')
    }

    @Unroll
    def 'useLastTag errors out if version has invalid number of digits - #version'() {
        git.tag.add(name: "v$version")

        when:
        def result = runTasksWithFailure('final', '-Prelease.useLastTag=true')

        then:
        result.standardError.contains "Current commit has following tags: [v${version}] but they were not recognized as valid versions"
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
        def result = runTasksWithFailure('final', "-Prelease.version=${version}")

        then:
        result.standardError.contains "Supplied release.version ($version) is not valid per semver spec. For more information, please refer to https://semver.org/"
        !new File(projectDir, "build/libs/${moduleName}-${version}.jar").exists()
        !originGit.tag.list()*.name.contains("v${version}")

        where:
        version << [
                '42.5.3.5',
                '42.5.3.5.3',
                '42.5.3.1-rc.1',
                '42.5.3.1.4-rc.1',
        ]
    }

    @Unroll
    def 'release with the override of version calculation does not errors out if version has invalid number of digits but verification is off - #version'() {
        when:
        def result = runTasksSuccessfully('final', "-Prelease.version=${version}", "-Prelease.ignoreSuppliedVersionVerification=true")

        then:
        !result.standardError.contains("Supplied release.version ($version) is not valid per semver spec. For more information, please refer to https://semver.org/")
        new File(projectDir, "build/libs/${moduleName}-${version}.jar").exists()

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
        def result = runTasksSuccessfully('final')

        then:
        result.wasExecuted('final')
        result.standardOutput.contains('Tagging repository as v0.1.0')
        result.standardOutput.contains('Pushing changes in [v0.1.0] to origin')
    }

    def 'Can create devSnapshot with scope patch if candidate for next minor is present'() {
        git.tag.add(name: 'v3.1.2')
        git.tag.add(name: 'v3.2.0-rc.1')
        git.tag.add(name: 'v3.2.0-rc.2')

        when:
        def version =  inferredVersionForTask('devSnapshot', '-Prelease.scope=patch')

        then:
        version == dev('3.1.3-dev.0+')
    }

    def 'Can create devSnapshot with scope patch if candidate for next minor is present - immutable snapshot'() {
        replaceDevWithImmutableSnapshot()
        git.tag.add(name: 'v3.1.2')
        git.tag.add(name: 'v3.2.0-rc.1')
        git.tag.add(name: 'v3.2.0-rc.2')

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

    static outputContains(ExecutionResult result, String substring) {
        return result.standardError.contains(substring) || result.standardOutput.contains(substring)
    }

    private String getUtcDateForComparison() {
        return TimestampUtil.getUTCFormattedTimestamp().take(8)
    }
}
