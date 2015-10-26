package nebula.plugin.release
import nebula.test.IntegrationSpec
import org.ajoberstar.grgit.Grgit
import org.gradle.api.plugins.JavaPlugin
/**
 * Verify the behavior of nebula-release under various states for a new project (e.g. no git repo yet initialized, no
 * initial commit)
 */
class ReleasePluginNewProjectIntegrationSpec extends IntegrationSpec {
    def 'release tasks unavailable when no git repository has been initialized'() {
        when:
        buildFile << """
            ${applyPlugin(ReleasePlugin)}
            ${applyPlugin(JavaPlugin)}
        """

        then:
        runTasksSuccessfully('build')
        runTasksWithFailure('snapshot')
    }

    def 'release tasks unavailable when git repository has no commits'() {
        setup: // equivalent of having completed `git init` but no initial commit
        def origin = new File(projectDir.parent, "${projectDir.name}.git")
        origin.mkdirs()
        Grgit.init(dir: origin)

        when:
        buildFile << """
            ${applyPlugin(ReleasePlugin)}
            ${applyPlugin(JavaPlugin)}
        """

        then:
        runTasksSuccessfully('build')
        runTasksWithFailure('snapshot')
    }
}
