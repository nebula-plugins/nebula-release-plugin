package nebula.plugin.release.git.base

import nebula.test.dsl.TestKitAssertions.assertThat
import nebula.test.dsl.plugins
import nebula.test.dsl.properties
import nebula.test.dsl.rootProject
import nebula.test.dsl.testProject
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testutil.GitHelpers.withRemoteGit
import testutil.release
import java.io.File

internal class BaseReleasePluginTest {
    @TempDir
    lateinit var projectDir: File
    @TempDir
    lateinit var gitRemote: File

    @Test
    fun `test with latest tag`() {
        withRemoteGit(gitRemote, projectDir ) { remote, local ->
            val runner = testProject(projectDir) {
                properties {
                    buildCache(true)
                    configurationCache(true)
                    property("release.defaultVersioningStrategy","use-last-tag")
                    property("release.useLastTag","true")
                }
                rootProject {
                    plugins {
                        id("com.netflix.nebula.release-base")
                    }
                    release {
                        versionStrategy("nebula.plugin.release.OverrideStrategies.ReleaseLastTagStrategy(project)")
                    }
                    rawBuildScript("""afterEvaluate { println("version: " + project.version) }""")
                }
            }
            local.tag().setName("v0.0.1").call()
            val result = runner.run("release")
            assertThat(result)
                .hasNoMutableStateWarnings()
                .hasNoDeprecationWarnings()
                .hasNoProblemsReport()
            assertThat(result.task(":prepare"))
                .hasOutcome(TaskOutcome.SUCCESS)
            assertThat(result.task(":release"))
                .`as`("release is skipped when last tag is used")
                .hasOutcome(TaskOutcome.SKIPPED)
            assertThat(result.output)
                .contains("version: 0.0.1")
        }
    }
}