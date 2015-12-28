package nebula.plugin.release

import org.gradle.api.plugins.JavaPlugin

import static org.codehaus.groovy.runtime.StackTraceUtils.extractRootCause

class ReleasePluginGitStateIntegrationSpec extends GitVersioningIntegrationSpec {

    @Override
    def setupBuild() {
        buildFile << """
            ${applyPlugin(ReleasePlugin)}
            ${applyPlugin(JavaPlugin)}
        """
    }

    def 'test that final and candidate do not work with uncommitted changes'() {
        setup:
        buildFile << "// force an uncommitted change to file"

        when:
        def finalFail = runTasksWithFailure("final")

        then:
        extractRootCause(finalFail.failure).message.contains('require all changes to be committed into Git')

        when:
        def candidateFail = runTasksWithFailure("candidate")

        then:
        extractRootCause(candidateFail.failure).message.contains('require all changes to be committed into Git')
    }

    def 'ensure plugin throws error regarding missing tag'() {
        setup:
        ['v1', '1.0.0', 'v0.0'].each { git.tag.add(name: it) }

        when:
        def failure = runTasksWithFailure("devSnapshot")

        then:
        extractRootCause(failure.failure).message.contains('requires a Git tag to indicate initial version')
    }

    def 'ensure plugin does NOT throw an error when a good init tag is present'() {
        setup:
        ['my-feature-branch', 'super-duper', 'v1.0', 'v0.1.0'].each { git.tag.add(name: it) }

        expect:
        runTasksSuccessfully("devSnapshot")
    }

}
