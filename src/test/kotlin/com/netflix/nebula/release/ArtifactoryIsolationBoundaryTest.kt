/*
 * Copyright 2026 the original author or authors.
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
package com.netflix.nebula.release

import nebula.test.dsl.withGradle
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jgit.api.Git
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File

internal class ArtifactoryIsolationBoundaryTest {
    @TempDir
    lateinit var directory: File

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `real Artifactory plugin fails isolation independently of release`(version: SupportedGradleVersion) {
        for (artifactory in listOf("6.0.0", "6.0.4")) {
            val project = fixture("upstream-$artifactory", artifactory)
            val cold = runner(project, version, isolated = false).build()
            assertThat(cold.output).contains("Configuration cache entry stored")
            assertThat(runner(project, version, isolated = false).build().output).contains("Reusing configuration cache")
            val isolated = runner(project, version, isolated = true).buildAndFail()
            assertThat(isolated.output).contains("Configuration cache problems found", "Plugin 'com.jfrog.artifactory'",
                "cannot access 'Project.configurations'", "subprojects")
            assertThat(isolated.output).doesNotContain("Experimental isolated release")
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `real Artifactory adoption receives migration guidance in either plugin order`(version: SupportedGradleVersion) {
        for (artifactory in listOf("6.0.0", "6.0.4")) for (first in listOf(false, true)) {
            val project = fixture("guard-$artifactory-$first", artifactory, adopted = true, artifactoryFirst = first)
            for (isolated in listOf(false, true)) {
                val failed = runner(project, version, isolated).buildAndFail()
                assertThat(failed.output).contains("Experimental isolated release", "Artifactory integration is not supported in project :",
                    "Maven/Ivy publish tasks with explicit release dependencies or finalizers",
                    "legacy release path with Gradle Isolated Projects disabled")
                assertThat(failed.task(":release")).isNull()
            }
            Git.open(project).use { assertThat(it.tagList().call().map { tag -> tag.name }).containsExactly("refs/tags/v1.2.3") }
        }
    }

    private fun fixture(name: String, artifactory: String, adopted: Boolean = false, artifactoryFirst: Boolean = false): File {
        val project = directory.resolve(name).apply { mkdirs() }
        project.resolve("settings.gradle").writeText(
            (if (adopted) "plugins { id 'com.netflix.nebula.release-settings' }\n" else "") +
                "rootProject.name = 'artifactory-boundary'\ninclude 'alpha'\n")
        project.resolve("gradle.properties").writeText("nebula.release.isolatedProjectsSupport=$adopted\n")
        project.resolve(".gitignore").writeText(".gradle/\nbuild/\n")
        val plugins = if (!adopted) "apply plugin: 'com.jfrog.artifactory'" else """
            plugins { id 'com.netflix.nebula.release' apply false }
            ${if (artifactoryFirst) "apply plugin: 'com.jfrog.artifactory'" else ""}
            apply plugin: 'com.netflix.nebula.release'
            ${if (!artifactoryFirst) "apply plugin: 'com.jfrog.artifactory'" else ""}
        """.trimIndent()
        project.resolve("build.gradle").writeText("""
            buildscript {
                repositories { mavenCentral() }
                dependencies { classpath 'org.jfrog.buildinfo:build-info-extractor-gradle:$artifactory' }
            }
            $plugins
        """.trimIndent())
        project.resolve("alpha/build.gradle").apply {
            parentFile.mkdirs()
            writeText("plugins { id 'java'; id 'maven-publish' }\n")
        }
        if (adopted) Git.init().setInitialBranch("main").setDirectory(project).call().use { git ->
            git.repository.config.apply {
                setString("user", null, "name", "Release test")
                setString("user", null, "email", "release@example.test")
                save()
            }
            git.add().addFilepattern(".").call()
            git.commit().setMessage("Fixture").setSign(false).call()
            git.tag().setName("v1.2.3").setAnnotated(false).call()
        }
        return project
    }

    private fun runner(project: File, version: SupportedGradleVersion, isolated: Boolean) =
        GradleRunner.create().withProjectDir(project).withPluginClasspath().apply { withGradle(version.version) }
            .withArguments("help", "-Dorg.gradle.unsafe.isolated-projects=$isolated",
                "--configuration-cache", "--configuration-cache-problems=fail", "--stacktrace")
}
