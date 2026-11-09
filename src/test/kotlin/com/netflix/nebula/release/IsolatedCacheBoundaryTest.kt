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

import nebula.test.dsl.Gradle
import nebula.test.dsl.withGradle
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jgit.api.Git
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Properties
import java.util.jar.JarFile

internal class IsolatedCacheBoundaryTest {
    @TempDir
    lateinit var directory: File

    @ParameterizedTest
    @ValueSource(strings = ["9.0.0", "9.5.0", "9.9.0"])
    fun `ignored child input invalidates the whole graph and refreshes one common timestamp`(gradleVersion: String) {
        val project = fixture()
        verifyCleanGit(project)

        val cold = runner(project, gradleVersion).build()
        assertConfigured(cold, paths)
        assertThat(cold.output).contains("Configuration cache entry stored", "NEBULA_CACHE_SETTINGS")
        val first = recorded(project)
        verifyVersionsAndArtifacts(project, first)
        assertThat(recorded(project, "alpha", "marker")).isEqualTo("before")
        // Alpha's real configuration delay crosses a precision boundary after settings captured the timestamp.
        assertThat(Instant.now().epochSecond).isGreaterThan(timestamp(first).epochSecond)

        val warm = runner(project, gradleVersion).build()
        assertReused(warm)
        verifyVersionsAndArtifacts(project, first)
        verifyCleanGit(project)

        // This applied script is tracked by Gradle and ignored by Git. Editing it leaves all shared Git facts unchanged.
        project.resolve("alpha/local.gradle").writeText("description = 'after'\n")
        verifyCleanGit(project)
        val changed = runner(project, gradleVersion).build()
        // These pinned versions provide whole-graph caching, not partial project configuration reuse.
        assertConfigured(changed, paths)
        assertThat(changed.output).contains("Configuration cache entry stored", "NEBULA_CACHE_SETTINGS")
            .doesNotContain("Reusing configuration cache")
        val next = recorded(project)
        assertThat(timestamp(next)).isAfter(timestamp(first))
        verifyVersionsAndArtifacts(project, next)
        assertThat(recorded(project, "alpha", "marker")).isEqualTo("after")
        verifyCleanGit(project)

        assertReused(runner(project, gradleVersion).build())
        verifyVersionsAndArtifacts(project, next)
        Git.open(project).use { assertThat(it.tagList().call().map { tag -> tag.name }).containsExactly("refs/tags/v1.2.3") }
    }

    private fun fixture(): File {
        val project = directory.resolve("cache-boundary").apply { mkdirs() }
        project.resolve("settings.gradle").writeText("""
            plugins { id 'com.netflix.nebula.release-settings' }
            println 'NEBULA_CACHE_SETTINGS'
            isolatedRelease { defaultStage = 'snapshot'; timestampPrecision = 'SECONDS' }
            rootProject.name = 'sample'
            include 'alpha', 'parent:leaf', 'versionOnly'
        """.trimIndent())
        project.resolve("gradle.properties").writeText("nebula.release.isolatedProjectsSupport=true\n")
        project.resolve(".gitignore").writeText(".gradle/\nbuild/\n**/local.gradle\n")
        for (path in paths) {
            val relative = if (path == ":") "" else path.substring(1).replace(':', '/')
            val target = project.resolve(relative).apply { mkdirs() }
            val participant = path in listOf(":", ":alpha", ":parent:leaf")
            val java = path in listOf(":alpha", ":parent:leaf")
            target.resolve("build.gradle").writeText("""
                plugins {
                    ${if (participant) "id 'com.netflix.nebula.release'" else ""}
                    ${if (java) "id 'java'" else ""}
                    ${if (path == ":parent:leaf") "id 'maven-publish'" else ""}
                }
                println 'NEBULA_CACHE_PROJECT=$path'
                group = 'sample'
                ${if (path == ":alpha") "apply from: 'local.gradle'\nThread.sleep(1100)" else ""}
                def observed = version.toString()
                def marker = description ?: ''
                tasks.register('recordVersion', WriteProperties) {
                    destinationFile = layout.buildDirectory.file('release-version.properties')
                    property 'version', observed
                    property 'marker', marker
                }
                ${if (path == ":parent:leaf") "publishing { publications { maven(MavenPublication) { from components.java } } }" else ""}
            """.trimIndent())
            if (java) target.resolve("src/main/java/Sample.java").apply {
                parentFile.mkdirs()
                writeText("public class Sample {}\n")
            }
        }
        project.resolve("alpha/local.gradle").writeText("description = 'before'\n")
        Git.init().setInitialBranch("main").setDirectory(project).call().use { git ->
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

    private fun assertConfigured(result: BuildResult, expected: List<String>) {
        assertThat(result.output.lineSequence().filter { it.startsWith("NEBULA_CACHE_PROJECT=") }
            .map { it.substringAfter('=') }.toList()).containsExactlyInAnyOrderElementsOf(expected)
    }

    private fun assertReused(result: BuildResult) {
        assertThat(result.output).contains("Reusing configuration cache").doesNotContain("NEBULA_CACHE_SETTINGS")
        assertConfigured(result, emptyList())
    }

    private fun verifyVersionsAndArtifacts(project: File, expected: String) {
        assertThat(expected).matches("1\\.3\\.0-snapshot\\.[0-9]{14}\\+[a-f0-9]{7}")
        paths.forEach { path ->
            val relative = if (path == ":") "" else path.substring(1).replace(':', '/')
            assertThat(recorded(project, relative)).isEqualTo(expected)
        }
        JarFile(project.resolve("alpha/build/libs/alpha-$expected.jar")).use {
            assertThat(it.getEntry("Sample.class")).isNotNull()
        }
        assertThat(project.resolve("parent/leaf/build/publications/maven/pom-default.xml").readText())
            .contains("<version>$expected</version>")
    }

    private fun verifyCleanGit(project: File) = Git.open(project).use { assertThat(it.status().call().isClean).isTrue() }

    private fun recorded(project: File, relative: String = "", key: String = "version") = Properties().apply {
        project.resolve(relative).resolve("build/release-version.properties").inputStream().use { load(it) }
    }.getProperty(key)

    private fun timestamp(version: String): Instant = LocalDateTime.parse(
        version.substringAfter("-snapshot.").substringBefore('+'), DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    ).toInstant(ZoneOffset.UTC)

    private fun runner(project: File, gradleVersion: String) =
        GradleRunner.create().withProjectDir(project).withPluginClasspath().apply { withGradle(Gradle.ofVersion(gradleVersion)) }
            .withArguments(listOf("recordVersion", ":alpha:jar", ":parent:leaf:generatePomFileForMavenPublication") +
                (if (gradleVersion == "9.9.0") listOf("--isolated-projects") else listOf("-Dorg.gradle.unsafe.isolated-projects=true")) +
                listOf("--no-watch-fs", "--configuration-cache", "--configuration-cache-problems=fail", "--stacktrace"))

    private val paths = listOf(":", ":alpha", ":parent", ":parent:leaf", ":versionOnly")
}
