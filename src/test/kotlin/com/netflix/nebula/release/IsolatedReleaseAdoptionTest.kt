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
import java.nio.file.Files
import java.util.Properties

internal class IsolatedReleaseAdoptionTest {
    @TempDir
    lateinit var directory: File

    private val paths = listOf("", "alpha", "parent", "parent/leaf", "versionOnly")

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `base built-in defaults preserve versions metadata preparation and tags`(version: SupportedGradleVersion) {
        for (mode in listOf("legacy", "cache", "isolated")) {
            for ((index, stage) in listOf("dev", "SNAPSHOT", "snapshot", "rc", "final").withIndex()) {
                val project = fixture("defaults-$mode-$index", mode != "legacy", defaultStage = stage)
                if (stage == "dev") Git.open(project).use { git ->
                    git.checkout().setCreateBranch(true).setName("feature/metadata").call()
                    git.repository.config.apply {
                        setString("branch", "feature/metadata", "remote", "origin")
                        setString("branch", "feature/metadata", "merge", "refs/heads/main")
                        save()
                    }
                }
                val options = listOf("-Pnebula.release.features.immutableSnapshot.timestampPrecision=DAYS")
                val cold = runner(project, version, mode == "isolated", options).build()
                assertThat(cold.output).contains("Configuration cache entry stored")
                val expected = when (stage) {
                    "dev" -> "1.2.4-dev.0+${head(project).take(7)}"
                    "SNAPSHOT" -> "1.3.0-SNAPSHOT"
                    "rc" -> "1.2.4-rc.1"
                    "final" -> "1.2.4"
                    else -> recorded(project)
                }
                if (stage == "snapshot") assertThat(expected)
                    .startsWith("1.2.4-snapshot.").endsWith("+${head(project).take(7)}")
                verifyVersions(project, expected)
                assertThat(recorded(project, key = "releaseTasks")).isEqualTo("prepare,release")
                assertThat(runner(project, version, mode == "isolated", options).build().output)
                    .contains("Reusing configuration cache")
                val release = runner(project, version, mode == "isolated", options, listOf("release", "recordVersion")).build()
                assertThat(release.task(":prepare")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
                assertThat(release.task(":release")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
                verifyNoImplicitWork(release, project)
                if (stage in listOf("rc", "final")) verifyTag(project, remote(project), "v$expected", expected)
                else Git.open(remote(project)).use { assertThat(it.tagList().call()).isEmpty() }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `base stage scope tag prefix and skip flag semantics match legacy`(version: SupportedGradleVersion) {
        for (mode in listOf("legacy", "cache", "isolated")) {
            val project = fixture("base-options-$mode", mode != "legacy", prefix = false, remoteName = "upstream")
            val result = runner(project, version, mode == "isolated", listOf(
                "-Prelease.stage=rc", "-Prelease.scope=minor", "-Prelease.disableGitChecks=true", "-Prelease.travisci=true"
            ), listOf("release", "recordVersion")).build()
            verifyVersions(project, "1.3.0-rc.1")
            assertThat(result.task(":prepare")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(result.task(":release")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
            verifyTag(project, remote(project), "1.3.0-rc.1", "1.3.0-rc.1")
            verifyNoImplicitWork(result, project)
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `base last tag reuse still prepares and supports warm cache`(version: SupportedGradleVersion) {
        for (mode in listOf("legacy", "cache", "isolated")) {
            val project = fixture("base-reuse-$mode", mode != "legacy", defaultStage = null)
            val options = listOf("-Prelease.useLastTag=true", "-Prelease.version=9.9.9")
            val tasks = listOf("release", "recordVersion")
            for (attempt in 1..2) {
                val result = runner(project, version, mode == "isolated", options, tasks).build()
                if (attempt == 2) assertThat(result.output).contains("Reusing configuration cache")
                verifyVersions(project, "1.2.3")
                assertThat(result.task(":prepare")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
                assertThat(result.task(":release")!!.outcome).isEqualTo(TaskOutcome.SKIPPED)
                verifyNoImplicitWork(result, project)
            }
            Git.open(remote(project)).use { assertThat(it.tagList().call()).isEmpty() }
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `base consumer task wiring stays explicit and task names do not select full workflows`(version: SupportedGradleVersion) {
        for (adopted in listOf(false, true)) {
            val project = fixture("base-hooks-$adopted", adopted, defaultStage = "SNAPSHOT")
            project.resolve("build.gradle").appendText("""

                tasks.named('release') { dependsOn ':alpha:build' }
                tasks.register('candidate') { dependsOn 'release' }
            """.trimIndent())
            commit(project)
            val tasks = listOf("candidate", "recordVersion", ":alpha:generatePomFileForMavenPublication",
                ":parent:leaf:generateDescriptorFileForIvyPublication")
            val result = runner(project, version, adopted, tasks = tasks).build()
            verifyVersions(project, "1.3.0-SNAPSHOT")
            assertThat(result.task(":alpha:jar")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(project.resolve("alpha/build/libs/alpha-1.3.0-SNAPSHOT.jar")).exists()
            assertThat(result.task(":parent:leaf:jar")).isNull()
            assertThat(result.task(":releaseCheck")).isNull()
            assertThat(result.task(":prepare")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(result.task(":release")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(project.resolve("alpha/build/publications/maven/pom-default.xml").readText())
                .contains("<version>1.3.0-SNAPSHOT</version>")
            assertThat(project.resolve("parent/leaf/build/publications/ivy/ivy.xml").readText())
                .contains("revision=\"1.3.0-SNAPSHOT\"")
            assertThat(runner(project, version, adopted, tasks = tasks).build().output)
                .contains("Reusing configuration cache")
            Git.open(remote(project)).use { assertThat(it.tagList().call()).isEmpty() }
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `base supplied versions precede no-commit inference without a fallback`(version: SupportedGradleVersion) {
        for (adopted in listOf(false, true)) {
            for (git in listOf(false, true)) {
                val project = fixture("base-empty-$adopted-$git", adopted, defaultStage = null, git = git, committed = false)
                val options = listOf("-Prelease.version=4.5.6")
                verifyVersionsAfter(runner(project, version, adopted, options).build(), project, "4.5.6")
                assertThat(runner(project, version, adopted, options).build().output).contains("Reusing configuration cache")
                assertThat(recorded(project, key = "releaseTasks")).isEqualTo("prepare,release")
            }
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `no-Git full fallback preserves participants versions descriptors and absent release tasks`(version: SupportedGradleVersion) {
        for (mode in listOf("legacy", "cache", "isolated")) {
            for (children in listOf(false, true)) {
                val project = fixture("no-git-$mode-$children", mode != "legacy", base = false,
                    git = false, childParticipants = children)
                if (children) {
                    val alpha = project.resolve("alpha/build.gradle")
                    alpha.writeText(alpha.readText().replace("def observedVersion", "version = 'custom-no-git'\ndef observedVersion"))
                }
                // The full plugin's early no-Git return ignores release inference policies.
                val options = listOf("-Prelease.version=not-semver", "-Prelease.stage=custom",
                    "-Prelease.useLastTag=true", "-Prelease.defaultVersioningStrategy=example.Unsupported",
                    "-Pnebula.release.features.replaceDevWithImmutableSnapshot=true")
                val tasks = listOf("build", "recordVersion", "generatePomFileForMavenPublication", "generateDescriptorFileForIvyPublication")
                val cold = runner(project, version, mode == "isolated", options, tasks).build()
                assertThat(cold.output).contains("Git repository not found")
                for (path in paths) {
                    val expected = if (children && path == "alpha") "custom-no-git"
                    else if (path == "" || children && path == "parent/leaf")
                        "0.1.0-dev.0.uncommitted" else "unspecified"
                    assertThat(recorded(project, path)).isEqualTo(expected)
                    assertThat(recorded(project, path, "releaseTasks")).isEmpty()
                }
                assertThat(project.resolve("alpha/build/publications/maven/pom-default.xml").readText())
                    .contains("<version>${recorded(project, "alpha")}</version>")
                assertThat(project.resolve("parent/leaf/build/publications/ivy/ivy.xml").readText())
                    .contains("revision=\"${recorded(project, "parent/leaf")}\"")
                assertThat(cold.task(":release")).isNull()
                assertThat(runner(project, version, mode == "isolated", options, tasks).build().output)
                    .contains("Reusing configuration cache")
            }
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `repository discovery invalidates the no-Git cache in both directions`(version: SupportedGradleVersion) {
        val project = fixture("discovery", true, base = false, git = false)
        runner(project, version, true).build()
        assertThat(recorded(project)).isEqualTo("0.1.0-dev.0.uncommitted")
        assertThat(recorded(project, "versionOnly")).isEqualTo("unspecified")
        initializeGit(project, committed = true, prefix = true, remoteName = "origin")
        val active = runner(project, version, true).build()
        assertThat(active.output).doesNotContain("Reusing configuration cache")
        assertThat(recorded(project)).startsWith("1.3.0-dev.")
        verifyVersions(project, recorded(project))
        assertThat(runner(project, version, true).build().output).contains("Reusing configuration cache")
        Files.move(project.resolve(".git").toPath(), directory.resolve("discovery-saved-git").toPath())
        val fallback = runner(project, version, true).build()
        assertThat(fallback.output).doesNotContain("Reusing configuration cache")
        assertThat(recorded(project)).isEqualTo("0.1.0-dev.0.uncommitted")
        assertThat(recorded(project, "versionOnly")).isEqualTo("unspecified")
        assertThat(recorded(project, key = "releaseTasks")).isEmpty()
        assertThat(runner(project, version, true).build().output).contains("Reusing configuration cache")
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `version and remote providers change common versions and actual push targets`(version: SupportedGradleVersion) {
        val project = fixture("providers", true, base = false)
        project.resolve("settings.gradle").appendText("""

            isolatedRelease {
                version = providers.gradleProperty('targetVersion')
                remote = providers.gradleProperty('targetRemote').orElse('origin')
            }
        """.trimIndent())
        val secondRemote = directory.resolve("second-remote.git")
        Git.init().setBare(true).setDirectory(secondRemote).call().close()
        Git.open(project).use { it.remoteAdd().setName("upstream").setUri(URIish(secondRemote.toURI().toString())).call() }
        commit(project)
        for ((target, value) in listOf("origin" to "4.5.6", "upstream" to "5.0.0")) {
            val options = listOf("-PtargetRemote=$target", "-PtargetVersion=$value", "-Prelease.version=ignored-by-settings")
            runner(project, version, true, options, listOf("final", "recordVersion")).build()
            verifyVersions(project, value)
            verifyTag(project, if (target == "origin") remote(project) else secondRemote, "v$value", value)
            runner(project, version, true, options).build()
            assertThat(runner(project, version, true, options).build().output).contains("Reusing configuration cache")
        }
        Git.open(remote(project)).use { assertThat(it.tagList().call().map { ref -> ref.name }).containsExactly("refs/tags/v4.5.6") }
        Git.open(secondRemote).use { assertThat(it.tagList().call().map { ref -> ref.name }).containsExactly("refs/tags/v5.0.0") }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `supplied version verification preserves the flag presence semantics and settings precedence`(version: SupportedGradleVersion) {
        for (base in listOf(false, true)) for (adopted in listOf(false, true)) {
            val project = fixture("verification-$base-$adopted", adopted, base = base)
            val options = listOf("-Prelease.version=custom-version")
            assertThat(runner(project, version, adopted, options).buildAndFail().output).contains("is not valid per semver spec")
            val ignored = options + "-Prelease.ignoreSuppliedVersionVerification=false"
            runner(project, version, adopted, ignored).build()
            verifyVersions(project, "custom-version")
            assertThat(runner(project, version, adopted, ignored).build().output).contains("Reusing configuration cache")
            if (adopted) {
                project.resolve("settings.gradle").appendText("\nisolatedRelease.verifySuppliedVersion = true\n")
                assertThat(runner(project, version, true, ignored).buildAndFail().output).contains("is not valid per semver spec")
            }
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `base mode and strategy adoption errors fail before cross-project access`(version: SupportedGradleVersion) {
        val project = fixture("adoption-errors", true, defaultStage = null)
        assertThat(runner(project, version, true).buildAndFail().output)
            .contains("release-base requires isolatedRelease.defaultStage")
        project.resolve("settings.gradle").appendText("\nisolatedRelease.defaultStage = 'dev'\n")
        project.resolve("alpha/build.gradle").appendText("\napply plugin: 'com.netflix.nebula.release-base'\n")
        assertThat(runner(project, version, true).buildAndFail().output)
            .contains("apply com.netflix.nebula.release-base only at the root")
        project.resolve("alpha/build.gradle").writeText("plugins { id 'java' }\n")
        project.resolve("build.gradle").appendText("\nrelease { versionStrategy(new Object()) }\n")
        assertThat(runner(project, version, true).buildAndFail().output)
            .contains("legacy release.versionStrategy customization is not implemented")
        project.resolve("build.gradle").writeText("plugins { id 'com.netflix.nebula.release' }\n")
        assertThat(runner(project, version, true).buildAndFail().output)
            .contains("baseRelease = true requires the release-base coordinator")
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `full default stage adapter matches legacy and remote freezes before projects`(version: SupportedGradleVersion) {
        for (mode in listOf("legacy", "cache", "isolated")) {
            val project = fixture("full-default-$mode", mode != "legacy", base = false, defaultStage = "SNAPSHOT")
            runner(project, version, mode == "isolated").build()
            verifyVersions(project, "1.3.0-SNAPSHOT")
            assertThat(runner(project, version, mode == "isolated").build().output)
                .contains("Reusing configuration cache")
        }
        val late = fixture("late-remote", true)
        late.resolve("settings.gradle").appendText("""

            def common = isolatedRelease
            gradle.settingsEvaluated {
                common.remote.set('too-late')
            }
        """.trimIndent())
        assertThat(runner(late, version, true).buildAndFail().output)
            .contains("final").contains("cannot be changed")
    }

    private fun fixture(name: String, adopted: Boolean, base: Boolean = true, defaultStage: String? = "dev",
                        git: Boolean = true, committed: Boolean = true, prefix: Boolean = true,
                        remoteName: String = "origin", childParticipants: Boolean = true): File {
        val project = directory.resolve(name).apply { mkdirs() }
        project.resolve("settings.gradle").writeText(
            (if (adopted) """
                plugins { id 'com.netflix.nebula.release-settings' }
                isolatedRelease {
                    baseRelease = $base
                    ${if (defaultStage != null) "defaultStage = '$defaultStage'" else ""}
                    remote = '$remoteName'
                    prefixTagsWithV = $prefix
                }
            """.trimIndent() + "\n" else "") +
                "rootProject.name = 'sample'\ninclude 'alpha', 'parent:leaf', 'versionOnly'\n")
        project.resolve("gradle.properties").writeText("nebula.release.isolatedProjectsSupport=$adopted\n")
        project.resolve(".gitignore").writeText(".gradle/\nbuild/\n")
        val record = """
            def observedVersion = project.version.toString()
            def releaseNames = project.tasks.names.findAll {
                it in ['prepare', 'release', 'postRelease', 'releaseCheck', 'snapshot', 'candidate', 'final'] || it.startsWith('nebulaRelease')
            }.sort().join(',')
            tasks.register('recordVersion', WriteProperties) {
                destinationFile = layout.buildDirectory.file('release-version.properties')
                property 'version', observedVersion
                property 'releaseTasks', releaseNames
            }
        """.trimIndent()
        for (path in paths) {
            val target = project.resolve(path).apply { mkdirs() }
            val participant = path == "" || !base && childParticipants && path in listOf("alpha", "parent/leaf")
            val plugin = if (base) "com.netflix.nebula.release-base" else "com.netflix.nebula.release"
            target.resolve("build.gradle").writeText("plugins { id 'java'${if (participant) "; id '$plugin'" else ""} }\ngroup = 'sample'\n" +
                (if (!adopted && path == "" && git) legacyConfiguration(base, defaultStage, prefix, remoteName) else "") +
                record + "\n")
            target.resolve("src/main/java/Sample.java").apply { parentFile.mkdirs(); writeText("public class Sample {}\n") }
        }
        project.resolve("alpha/build.gradle").appendText("\napply plugin: 'maven-publish'\npublishing { publications { maven(MavenPublication) { from components.java } } }\n")
        project.resolve("parent/leaf/build.gradle").appendText("\napply plugin: 'ivy-publish'\npublishing { publications { ivy(IvyPublication) { from components.java } } }\n")
        // Base's legacy extension is also available without Git.
        if (!adopted && base && !git) {
            val root = project.resolve("build.gradle")
            root.writeText(root.readText().replace("def observedVersion", legacyConfiguration(true, defaultStage, prefix, remoteName) + "def observedVersion"))
        }
        if (git) initializeGit(project, committed, prefix, remoteName)
        return project
    }

    private fun legacyConfiguration(base: Boolean, stage: String?, prefix: Boolean, remoteName: String): String {
        val defaults = mapOf("dev" to "DEVELOPMENT", "SNAPSHOT" to "SNAPSHOT", "snapshot" to "IMMUTABLE_SNAPSHOT",
            "milestone" to "PRE_RELEASE", "rc" to "PRE_RELEASE", "final" to "FINAL")
        return """
            release {
                remote = '$remoteName'
                tagStrategy { prefixNameWithV = $prefix }
                ${if (base) """
                    versionStrategy new nebula.plugin.release.OverrideStrategies.ReleaseLastTagStrategy(project)
                    versionStrategy new nebula.plugin.release.OverrideStrategies.GradlePropertyStrategy(project)
                    ['SNAPSHOT', 'IMMUTABLE_SNAPSHOT', 'DEVELOPMENT', 'PRE_RELEASE', 'FINAL'].each {
                        versionStrategy nebula.plugin.release.git.opinion.Strategies[it]
                    }
                """.trimIndent() else ""}
                ${if (stage != null && (base || stage != "dev")) "defaultVersionStrategy = " +
                    (if (base) "nebula.plugin.release.git.opinion.Strategies.${defaults[stage]}.copyWith(stages: ['$stage'] as SortedSet)"
                     else "nebula.plugin.release.NetflixOssStrategies.${defaults[stage]}(project)") else ""}
            }
        """.trimIndent() + "\n"
    }

    private fun initializeGit(project: File, committed: Boolean, prefix: Boolean, remoteName: String) {
        Git.init().setInitialBranch("main").setDirectory(project).call().use { git ->
            git.repository.config.apply {
                setString("user", null, "name", "Release test")
                setString("user", null, "email", "release@example.test")
                save()
            }
            if (committed) {
                git.add().addFilepattern(".").call()
                git.commit().setMessage("Fixture").setSign(false).call()
                git.tag().setName(if (prefix) "v1.2.3" else "1.2.3").setAnnotated(false).call()
                Git.init().setBare(true).setInitialBranch("main").setDirectory(remote(project)).call().close()
                git.remoteAdd().setName(remoteName).setUri(URIish(remote(project).toURI().toString())).call()
                git.push().setRemote(remoteName).add("main").call()
                git.repository.config.apply {
                    setString("branch", "main", "remote", remoteName)
                    setString("branch", "main", "merge", "refs/heads/main")
                    save()
                }
            }
        }
    }

    private fun commit(project: File) = Git.open(project).use {
        it.add().addFilepattern(".").call()
        it.commit().setMessage("Configure fixture").setSign(false).call()
    }

    private fun head(project: File): String = Git.open(project).use { it.repository.resolve("HEAD").name }
    private fun remote(project: File) = directory.resolve("${project.name}-remote.git")
    private fun recorded(project: File, path: String = "", key: String = "version") = Properties().apply {
        project.resolve(path).resolve("build/release-version.properties").inputStream().use { load(it) }
    }.getProperty(key)

    private fun verifyVersions(project: File, expected: String) {
        paths.forEach { assertThat(recorded(project, it)).isEqualTo(expected) }
    }

    private fun verifyVersionsAfter(result: BuildResult, project: File, expected: String) {
        assertThat(result.task(":recordVersion")).isNotNull()
        verifyVersions(project, expected)
    }

    private fun verifyNoImplicitWork(result: BuildResult, project: File) {
        paths.forEach { path ->
            val taskPrefix = if (path.isEmpty()) "" else ":${path.replace('/', ':')}"
            assertThat(result.task("$taskPrefix:jar")).isNull()
            assertThat(project.resolve(path).resolve("build/libs")).doesNotExist()
        }
        assertThat(result.task(":alpha:generatePomFileForMavenPublication")).isNull()
        assertThat(result.task(":parent:leaf:generateDescriptorFileForIvyPublication")).isNull()
    }

    private fun verifyTag(project: File, remote: File, tag: String, version: String) {
        for (repository in listOf(project, remote)) {
            Git.open(repository).use { git -> RevWalk(git.repository).use { walk ->
                val annotation = walk.parseTag(git.repository.resolve("refs/tags/$tag"))
                assertThat(annotation.fullMessage).isEqualTo("Release of $version\n")
                assertThat(annotation.`object`.id.name).isEqualTo(head(project))
            } }
        }
    }

    private fun runner(project: File, version: SupportedGradleVersion, isolated: Boolean,
                       extra: List<String> = emptyList(), tasks: List<String> = listOf("help", "recordVersion")) =
        GradleRunner.create().withProjectDir(project).withPluginClasspath().apply { withGradle(version.version) }
            .withArguments(tasks + extra + listOf("-Dorg.gradle.unsafe.isolated-projects=$isolated",
                "--configuration-cache", "--configuration-cache-problems=fail", "--stacktrace"))
}
