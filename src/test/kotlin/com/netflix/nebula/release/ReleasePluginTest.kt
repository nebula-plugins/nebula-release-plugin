package com.netflix.nebula.release

import nebula.test.dsl.TestKitAssertions.assertThat
import nebula.test.dsl.plugins
import nebula.test.dsl.properties
import nebula.test.dsl.rootProject
import nebula.test.dsl.run
import nebula.test.dsl.subProject
import nebula.test.dsl.testProject
import nebula.test.dsl.withGradle
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File

internal class ReleasePluginTest {
    @TempDir
    lateinit var projectDir: File

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `test no git`(version: SupportedGradleVersion) {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
                configurationCache(true)
            }
            rootProject {
                plugins {
                    id("com.netflix.nebula.release")
                }
            }
            subProject("sub") {
                plugins {
                    java()
                    id("com.netflix.nebula.release")
                }
            }
        }
        val result = runner.run("assemble") {
            forwardOutput()
            withGradle(version.version)
        }

        assertThat(result)
            .hasNoDeprecationWarnings()
            .hasNoMutableStateWarnings()
        assertThat(result.task(":sub:jar"))
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)
        assertThat(projectDir.resolve("sub/build/libs/sub-0.1.0-dev.0.uncommitted.jar")).exists()
    }
}