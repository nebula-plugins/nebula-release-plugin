package nebula.plugin.release
import nebula.test.IntegrationTestKitSpec
import org.ajoberstar.grgit.Grgit

/**
 * Verify the behavior of nebula-release under various states for a new project (e.g. no git repo yet initialized, no
 * initial commit)
 */
class ReleasePluginNewProjectIntegrationSpec extends IntegrationTestKitSpec {
    def 'release tasks unavailable when git repository has no commits'() {
        setup: // equivalent of having completed `git init` but no initial commit
        def origin = new File(projectDir.parent, "${projectDir.name}.git")
        origin.mkdirs()
        Grgit.init(dir: origin)

        when:
        buildFile << """
            plugins {
                id 'com.netflix.nebula.release'
                id 'java'
            }
        """

        then:
        runTasks('build')
        runTasksAndFail('snapshot')
    }
}
