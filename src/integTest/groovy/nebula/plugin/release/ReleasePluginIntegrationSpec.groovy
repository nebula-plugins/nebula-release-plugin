package nebula.plugin.release

import org.gradle.api.plugins.JavaPlugin

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

    def 'final release log'() {
        when:
        inferredVersionForTask('final')

        then:
        String message = originGit.tag.list().find { it.name == 'v0.1.0' }.fullMessage
        message.contains 'Release of 0.1.0'
        message.find(/- [a-f0-9]{40}: Setup/)
        message.find(/- [a-f0-9]{40}: Initial checkout/)
    }

    def 'create new major release branch have branch name respected on version'() {
        def oneX = '1.x'
        git.branch.add(name: oneX)
        git.push(all: true)
        git.branch.change(name: oneX, startPoint: "origin/${oneX}")
        git.checkout(branch: oneX)

        when:
        def version = inferredVersionForTask('devSnapshot')

        then:
        version == dev('1.0.0-dev.2+')
    }

    def 'create new major release branch in git-flow style and have branch name respected on version'() {
        def oneX = 'release/1.x'
        git.branch.add(name: oneX)
        git.push(all: true)
        git.branch.change(name: oneX, startPoint: "origin/${oneX}")
        git.checkout(branch: oneX)

        when:
        def version = inferredVersionForTask('devSnapshot')

        then:
        version == dev('1.0.0-dev.2+')
    }

    def 'create release on git-flow style branch'() {
        def twoX = 'release/2.x'
        git.tag.add(name: 'v1.0.0')
        git.branch.add(name: twoX)
        git.push(all: true, tags: true)
        git.branch.change(name: twoX, startPoint: "origin/${twoX}")
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
        git.branch.change(name: oneThreeX, startPoint: "origin/${oneThreeX}")
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
        git.branch.change(name: oneThreeX, startPoint: "origin/${oneThreeX}")
        git.checkout(branch: oneThreeX)

        when:
        def version = inferredVersionForTask('final')

        then:
        version == normal('1.3.0')
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
        result.standardError.contains 'testexample does not match one of the included patterns: [master, (release(-|/))?\\d+(\\.\\d+)?\\.x]'
    }

    def 'version includes branch name on devSnapshot of non release branch'() {
        git.branch.add(name: 'testexample')
        git.push(all: true)
        git.branch.change(name: 'testexample', startPoint: 'origin/testexample')
        git.checkout(branch: 'testexample')

        when:
        def version = inferredVersionForTask('devSnapshot')

        then:
        version == dev('0.1.0-dev.2+testexample.')
    }

    def 'version includes branch name with stripped off patterns on devSnapshot of non release branch'() {
        git.branch.add(name: 'feature/testexample')
        git.push(all: true)
        git.branch.change(name: 'feature/testexample', startPoint: 'origin/feature/testexample')
        git.checkout(branch: 'feature/testexample')

        when:
        def version = inferredVersionForTask('devSnapshot')

        then:
        version == dev('0.1.0-dev.2+testexample.')
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
        version == dev('0.1.0-dev.3+')
    }
}
