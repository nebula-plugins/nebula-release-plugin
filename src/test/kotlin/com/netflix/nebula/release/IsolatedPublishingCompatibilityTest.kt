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
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.URIish
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File
import java.util.jar.JarFile

internal class IsolatedPublishingCompatibilityTest {
    @TempDir
    lateinit var directory: File

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `native publishing dependencies and finalizers preserve participation and warm cache`(version: SupportedGradleVersion) {
        for (mode in listOf("legacy", "cache", "isolated")) for (base in listOf(false, true)) {
            val project = fixture("publish-$mode-$base", mode, base)
            val tasks = listOf(if (base) "release" else "snapshot")
            val options = listOf("-Prelease.stage=SNAPSHOT")
            for (attempt in 1..2) {
                val result = runner(project, version, mode, tasks, options).build()
                assertThat(result.output).contains(if (attempt == 1) "Configuration cache entry stored" else "Reusing configuration cache")
                verifyPublished(result, project, "1.3.0-SNAPSHOT")
                val paths = result.tasks.map { it.path }
                assertThat(paths.indexOf(":alpha:publish")).isLessThan(paths.indexOf(":release"))
                assertThat(paths.indexOf(":parent:leaf:publish")).isGreaterThan(paths.indexOf(":release"))
                assertThat(result.task(":release")!!.outcome).isEqualTo(if (base) TaskOutcome.SUCCESS else TaskOutcome.SKIPPED)
            }
            verifyTags(project, null)
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `publishing hooks preserve real releases and last-tag reuse`(version: SupportedGradleVersion) {
        for (mode in listOf("legacy", "isolated")) for (base in listOf(false, true)) for (stage in listOf("rc", "final")) {
            val project = fixture("tagged-$mode-$base-$stage", mode, base)
            val tasks = listOf(if (base) "release" else if (stage == "rc") "candidate" else "final")
            val options = listOf("-Prelease.stage=$stage")
            val expected = (if (base) "1.2.4" else "1.3.0") + if (stage == "rc") "-rc.1" else ""
            val released = runner(project, version, mode, tasks, options).build()
            verifyPublished(released, project, expected)
            assertThat(released.task(":release")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
            verifyTags(project, expected)
            for (attempt in 1..2) {
                val reused = runner(project, version, mode, tasks, options + "-Prelease.useLastTag=true").build()
                if (attempt == 2) assertThat(reused.output).contains("Reusing configuration cache")
                verifyPublished(reused, project, expected)
                assertThat(reused.task(":release")!!.outcome).isEqualTo(TaskOutcome.SKIPPED)
                verifyTags(project, expected)
            }
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `consumer release failure still runs publishing finalizers without creating a tag`(version: SupportedGradleVersion) {
        for (mode in listOf("legacy", "cache", "isolated")) {
            val project = fixture("failed-$mode", mode, base = false, failRelease = true)
            for (attempt in 1..2) {
                val result = runner(project, version, mode, listOf("final")).buildAndFail()
                assertThat(result.output).contains("release blocked by consumer")
                if (attempt == 2) assertThat(result.output).contains("Reusing configuration cache")
                assertThat(result.task(":release")!!.outcome).isEqualTo(TaskOutcome.FAILED)
                verifyPublished(result, project, "1.3.0")
                verifyTags(project, null)
            }
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `descriptor commands do not run consumer publishing hooks`(version: SupportedGradleVersion) {
        for (mode in listOf("legacy", "cache", "isolated")) {
            val project = fixture("descriptors-$mode", mode, base = false)
            val tasks = listOf(":alpha:generatePomFileForMavenPublication", ":parent:leaf:generateDescriptorFileForIvyPublication")
            for (attempt in 1..2) {
                val result = runner(project, version, mode, tasks).build()
                if (attempt == 2) assertThat(result.output).contains("Reusing configuration cache")
                assertThat(result.task(":release")).isNull()
                assertThat(result.task(":alpha:publish")).isNull()
                assertThat(result.task(":parent:leaf:publish")).isNull()
                assertThat(project.resolve("build/repository")).doesNotExist()
                assertThat(project.resolve("alpha/build/publications/maven/pom-default.xml")).exists()
                assertThat(project.resolve("parent/leaf/build/publications/ivy/ivy.xml")).exists()
                verifyTags(project, null)
            }
        }
    }

    private fun fixture(name: String, mode: String, base: Boolean, failRelease: Boolean = false): File {
        val project = directory.resolve(name).apply { mkdirs() }
        project.resolve("settings.gradle").writeText(
            (if (mode != "legacy") """
                plugins { id 'com.netflix.nebula.release-settings' }
                isolatedRelease { baseRelease = $base; ${if (base) "defaultStage = 'SNAPSHOT'" else ""} }
            """.trimIndent() + "\n" else "") +
                "rootProject.name = 'sample'\ninclude 'alpha', 'parent:leaf', 'versionOnly'\n")
        project.resolve("gradle.properties").writeText("nebula.release.isolatedProjectsSupport=${mode != "legacy"}\n")
        project.resolve(".gitignore").writeText(".gradle/\nbuild/\n")
        for (path in listOf("", "alpha", "parent", "parent/leaf", "versionOnly")) {
            val target = project.resolve(path).apply { mkdirs() }
            val plugin = if (path == "" && base) "com.netflix.nebula.release-base"
                else if (!base && path in listOf("", "alpha", "parent/leaf")) "com.netflix.nebula.release" else null
            val publishing = when (path) { "alpha", "versionOnly" -> "maven-publish"; "parent/leaf" -> "ivy-publish"; else -> null }
            target.resolve("build.gradle").writeText("plugins { id 'java'" +
                (plugin?.let { "; id '$it'" } ?: "") + (publishing?.let { "; id '$it'" } ?: "") + " }\ngroup = 'sample'\n" +
                (publishing?.let {
                    val maven = it == "maven-publish"
                    """
                        publishing {
                            publications { ${if (maven) "maven(MavenPublication)" else "ivy(IvyPublication)"} { from components.java } }
                            repositories { ${if (maven) "maven" else "ivy"} { url = uri(providers.gradleProperty('testRepository').get()) } }
                        }
                    """.trimIndent() + "\n"
                } ?: ""))
            target.resolve("src/main/java/Sample.java").apply {
                parentFile.mkdirs()
                writeText("public class Sample {}\n")
            }
        }
        if (base && mode == "legacy") project.resolve("build.gradle").appendText("""

            release {
                versionStrategy new nebula.plugin.release.OverrideStrategies.ReleaseLastTagStrategy(project)
                versionStrategy new nebula.plugin.release.OverrideStrategies.GradlePropertyStrategy(project)
                ['SNAPSHOT', 'IMMUTABLE_SNAPSHOT', 'DEVELOPMENT', 'PRE_RELEASE', 'FINAL'].each {
                    versionStrategy nebula.plugin.release.git.opinion.Strategies[it]
                }
                defaultVersionStrategy = nebula.plugin.release.git.opinion.Strategies.SNAPSHOT
            }
        """.trimIndent())
        project.resolve("build.gradle").appendText("""

            tasks.named('release') {
                dependsOn ':alpha:publish'
                finalizedBy ':parent:leaf:publish'
                ${if (failRelease) "doFirst { throw new GradleException('release blocked by consumer') }" else ""}
            }
        """.trimIndent())
        Git.init().setInitialBranch("main").setDirectory(project).call().use { git ->
            git.repository.config.apply {
                setString("user", null, "name", "Release test")
                setString("user", null, "email", "release@example.test")
                save()
            }
            git.add().addFilepattern(".").call()
            git.commit().setMessage("Fixture").setSign(false).call()
            git.tag().setName("v1.2.3").setAnnotated(false).call()
            Git.init().setBare(true).setInitialBranch("main").setDirectory(remote(project)).call().close()
            git.remoteAdd().setName("origin").setUri(URIish(remote(project).toURI().toString())).call()
            git.push().setRemote("origin").add("main").call()
            git.repository.config.apply {
                setString("branch", "main", "remote", "origin")
                setString("branch", "main", "merge", "refs/heads/main")
                save()
            }
        }
        return project
    }

    private fun verifyPublished(result: BuildResult, project: File, version: String) {
        assertThat(result.task(":alpha:publish")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.task(":parent:leaf:publish")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        val repository = project.resolve("build/repository")
        val maven = repository.resolve("sample/alpha/$version")
        val ivy = repository.resolve("sample/leaf/$version")
        val poms = maven.listFiles { file -> file.extension == "pom" }.orEmpty()
        assertThat(poms).isNotEmpty()
        poms.forEach { assertThat(it.readText()).contains("<version>$version</version>") }
        assertThat(ivy.resolve("ivy-$version.xml").readText()).contains("revision=\"$version\"")
        for (module in listOf(maven, ivy)) {
            val jars = module.listFiles { file -> file.extension == "jar" }.orEmpty()
            assertThat(jars).isNotEmpty()
            jars.forEach { jar -> JarFile(jar).use { assertThat(it.getEntry("Sample.class")).isNotNull() } }
        }
        assertThat(result.task(":versionOnly:publish")).isNull()
        assertThat(repository.resolve("sample/versionOnly")).doesNotExist()
        for (path in listOf("", "parent", "versionOnly")) {
            assertThat(project.resolve(path).resolve("build/libs")).doesNotExist()
        }
    }

    private fun verifyTags(project: File, expected: String?) {
        Git.open(project).use { git ->
            assertThat(git.tagList().call().map { it.name }).containsExactlyInAnyOrderElementsOf(
                listOfNotNull("refs/tags/v1.2.3", expected?.let { "refs/tags/v$it" }))
            if (expected != null) RevWalk(git.repository).use { walk ->
                val tag = walk.parseTag(git.repository.resolve("refs/tags/v$expected"))
                assertThat(tag.`object`.id.name).isEqualTo(git.repository.resolve("HEAD").name)
                assertThat(tag.fullMessage).isEqualTo("Release of $expected\n")
            }
        }
        Git.open(remote(project)).use { git ->
            assertThat(git.tagList().call().map { it.name }).containsExactlyElementsOf(
                expected?.let { listOf("refs/tags/v$it") } ?: emptyList())
        }
    }

    private fun remote(project: File) = directory.resolve("${project.name}-remote.git")

    private fun runner(project: File, version: SupportedGradleVersion, mode: String,
                       tasks: List<String>, extra: List<String> = emptyList()) =
        GradleRunner.create().withProjectDir(project).withPluginClasspath().apply { withGradle(version.version) }
            .withArguments(tasks + extra + listOf("-PtestRepository=${project.resolve("build/repository").toURI().toString().trimEnd('/')}/",
                "-Dorg.gradle.unsafe.isolated-projects=${mode == "isolated"}",
                "--configuration-cache", "--configuration-cache-problems=fail", "--stacktrace"))
}
