package nebula.plugin.release

import java.nio.file.Files
import nebula.test.IntegrationSpec
import org.ajoberstar.grgit.Grgit
import org.gradle.api.plugins.JavaPlugin

class ReleasePluginIntegrationSpec extends IntegrationSpec {

    Grgit grgit
    Grgit originGit

    def setup() {
        def origin = new File(projectDir.parent, "${projectDir.name}.git")
        origin.mkdirs()

        ['build.gradle', 'settings.gradle'].each {
            Files.move(new File(projectDir, it).toPath(), new File(origin, it).toPath())
        }

        originGit = Grgit.init(dir: origin)
        originGit.add(patterns: ['build.gradle', 'settings.gradle', '.gitignore'] as Set)
        originGit.commit(message: 'Initial checkout')

        grgit = Grgit.clone(dir: projectDir, uri: origin.absolutePath)

        new File(projectDir, '.gitignore') << '''
            .gradle-test-kit
            .gradle
            build/
        '''.stripIndent()

        buildFile << """
            ext.dryRun = true
            group = 'test'
            ${applyPlugin(ReleasePlugin)}
            ${applyPlugin(JavaPlugin)}

            task showVersion << {
                logger.lifecycle "Version in task: \${version.toString()}"
            }
        """.stripIndent()

        grgit.add(patterns: ['build.gradle', '.gitignore'] as Set)
        grgit.commit(message: 'Setup')
        grgit.push()
    }

    def cleanup() {
        if (grgit) grgit.close()
        if (originGit) originGit.close()
    }

    def 'build defaults to dev version string'() {
        when:
        def results = runTasksSuccessfully('build')

        then:
        results.standardOutput.contains '0.1.0-dev.2+'
    }

    def 'build on non standard branch appends name to dev version string'() {
        grgit.checkout(branch: 'testexample', createBranch: true)

        when:
        def results = runTasksSuccessfully('build')

        then:
        results.standardOutput.contains '0.1.0-dev.2+testexample.'
    }

    def 'choose devSnapshot version'() {
        when:
        def results = runTasksSuccessfully('devSnapshot')

        then:
        results.standardOutput.contains '0.1.0-dev.2+'
    }

    def 'use maven style snapshot string'() {
        when:
        def results = runTasksSuccessfully('snapshot')

        then:
        results.standardOutput.contains '0.1.0-SNAPSHOT'
    }

    def 'choose candidate version'() {
        when:
        def results = runTasksSuccessfully('candidate')

        then:
        results.standardOutput.contains '0.1.0-rc.1'
    }

    def 'multiple candidate releases will increment rc number'() {
        runTasksSuccessfully('candidate')

        when:
        def results = runTasksSuccessfully('candidate')

        then:
        results.standardOutput.contains '0.1.0-rc.2'
    }

    def 'candidate release creates tag'() {
        when:
        def results = runTasksSuccessfully('candidate')

        then:
        originGit.tag.list()*.name.contains('v0.1.0-rc.1')
    }

    def 'choose release version'() {
        when:
        def results = runTasksSuccessfully('final')

        then:
        results.standardOutput.contains 'Inferred version: 0.1.0\n'
    }

    def 'choose release version, update patch'() {
        when:
        def results = runTasksSuccessfully('final', '-Prelease.scope=patch')

        then:
        results.standardOutput.contains 'Inferred version: 0.0.1\n'
    }

    def 'choose release version, update major'() {
        when:
        def results = runTasksSuccessfully('final', '-Prelease.scope=major')

        then:
        results.standardOutput.contains 'Inferred version: 1.0.0\n'
    }

    def 'multiple final releases with defaults will increment minor number'() {
        runTasksSuccessfully('final')

        when:
        def results = runTasksSuccessfully('final')

        then:
        results.standardOutput.contains 'Inferred version: 0.2.0\n'
    }

    def 'final release creates tag'() {
        when:
        def results = runTasksSuccessfully('final')

        then:
        originGit.tag.list()*.name.contains('v0.1.0')
    }

    def 'final release log'() {
        when:
        def results = runTasksSuccessfully('final')

        then:
        String message = originGit.tag.list().find { it.name == 'v0.1.0' }.fullMessage
        message.contains 'Release of 0.1.0'
        message.find(/- [a-f0-9]{40}: Setup/)
        message.find(/- [a-f0-9]{40}: Initial checkout/)
    }

    def 'create new major release branch have branch name respected on version'() {
        def oneX = '1.x'
        grgit.branch.add(name: oneX)
        grgit.push(all: true)
        grgit.branch.change(name: oneX, startPoint: "origin/${oneX}")
        grgit.checkout(branch: oneX)

        when:
        def results = runTasksSuccessfully('devSnapshot')

        then:
        results.standardOutput.contains '1.0.0-dev.2+'
    }

    def 'create new major release branch in git-flow style and have branch name respected on version'() {
        def oneX = 'release/1.x'
        grgit.branch.add(name: oneX)
        grgit.push(all: true)
        grgit.branch.change(name: oneX, startPoint: "origin/${oneX}")
        grgit.checkout(branch: oneX)

        when:
        def results = runTasksSuccessfully('devSnapshot')

        then:
        results.standardOutput.contains '1.0.0-dev.2+'
    }

    def 'create release on git-flow style branch'() {
        def twoX = 'release/2.x'
        grgit.tag.add(name: 'v1.0.0')
        grgit.branch.add(name: twoX)
        grgit.push(all: true, tags: true)
        grgit.branch.change(name: twoX, startPoint: "origin/${twoX}")
        grgit.checkout(branch: twoX)

        when:
        def results = runTasksSuccessfully('final')

        then:
        originGit.tag.list()*.name.contains('v2.0.0')
    }

    def 'task dependency configuration is read from extension'() {
        buildFile << '''
            task placeholderTask

            tasks.release.dependsOn placeholderTask
        '''.stripIndent()

        grgit.add(patterns: ['build.gradle'] as Set)
        grgit.commit(message: 'Setup')
        grgit.push()

        when:
        def results = runTasksSuccessfully('final')

        then:
        results.wasExecuted('placeholderTask')
    }

    def 'fail final release on non release branch'() {
        grgit.checkout(branch: 'testexample', createBranch: true)

        when:
        def result = runTasksWithFailure('final')

        then:
        result.failure != null
        result.standardError.contains 'testexample does not match one of the included patterns: [master, (release(-|/))?\\d+\\.x]'
    }

    def 'version includes branch name on devSnapshot of non release branch'() {
        grgit.branch.add(name: 'testexample')
        grgit.push(all: true)
        grgit.branch.change(name: 'testexample', startPoint: 'origin/testexample')
        grgit.checkout(branch: 'testexample')

        when:
        def result = runTasksSuccessfully('devSnapshot')

        then:
        result.standardOutput.contains '0.1.0-dev.2+testexample.'
    }

    def 'version includes branch name with stripped off patterns on devSnapshot of non release branch'() {
        grgit.branch.add(name: 'feature/testexample')
        grgit.push(all: true)
        grgit.branch.change(name: 'feature/testexample', startPoint: 'origin/feature/testexample')
        grgit.checkout(branch: 'feature/testexample')

        when:
        def result = runTasksSuccessfully('devSnapshot')

        then:
        result.standardOutput.contains '0.1.0-dev.2+testexample.'
    }

    def 'fail build on excluded master branch'() {
        buildFile << '''\
            nebulaRelease {
                addExcludeBranchPattern(/^master\$/)
            }
        '''.stripIndent()

        grgit.add(patterns: ['build.gradle'] as Set)
        grgit.commit(message: 'Setup')
        grgit.push()

        when:
        def result = runTasksWithFailure('final')

        then:
        result.failure != null
        result.standardError.contains 'master matched an excluded pattern: [^master\$]'
    }

    def 'use last tag'() {
        grgit.tag.add(name: 'v42.5.3')

        when:
        def results = runTasksSuccessfully('final', '-Prelease.useLastTag=true')

        then:
        new File(projectDir, "build/libs/${moduleName}-42.5.3.jar").exists()
    }

    def 'skip useLastTag if false'() {
        when:
        def results = runTasksSuccessfully('final', '-Prelease.useLastTag=345')

        then:
        new File(projectDir, "build/libs/${moduleName}-0.1.0.jar").exists()
    }

    def 'able to release with the override of version calculation'() {
        when:
        def results = runTasksSuccessfully('final', '-Prelease.version=42.5.0')

        then:
        new File(projectDir, "build/libs/${moduleName}-42.5.0.jar").exists()
        originGit.tag.list()*.name.contains('v42.5.0')
    }
}
