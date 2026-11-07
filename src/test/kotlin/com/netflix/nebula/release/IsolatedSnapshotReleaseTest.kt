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

internal class IsolatedSnapshotReleaseTest {
    @TempDir
    lateinit var directory: File

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `snapshot versions artifacts descriptors and participation match legacy`(version: SupportedGradleVersion) {
        val legacy = fixture("legacy", adopted = false)
        verifySnapshot(run(legacy, version, isolated = false, extra = listOf("--no-configuration-cache")),
            legacy, "1.3.0-SNAPSHOT")
        verifySnapshot(run(legacy, version, isolated = false), legacy, "1.3.0-SNAPSHOT")
        assertThat(run(legacy, version, isolated = false).output).contains("Reusing configuration cache")

        val adopted = fixture("adopted", adopted = true)
        for (isolated in listOf(false, true)) {
            val cold = run(adopted, version, isolated)
            assertThat(cold.output).contains("Configuration cache entry stored")
            verifySnapshot(cold, adopted, "1.3.0-SNAPSHOT")
            assertThat(cold.task(":prepare")!!.outcome).isEqualTo(TaskOutcome.SKIPPED)
            assertThat(cold.task(":release")!!.outcome).isEqualTo(TaskOutcome.SKIPPED)
            assertThat(cold.task(":releaseCheck")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

            val warm = run(adopted, version, isolated)
            assertThat(warm.output).contains("Reusing configuration cache")
            verifySnapshot(warm, adopted, "1.3.0-SNAPSHOT")
        }

        // A shared property change must update every project's cached version.
        val changed = run(adopted, version, isolated = true, extra = listOf("-Prelease.scope=patch"))
        verifySnapshot(changed, adopted, "1.2.4-SNAPSHOT")
        assertThat(run(adopted, version, true, listOf("-Prelease.scope=patch")).output)
            .contains("Reusing configuration cache")
        Git.open(adopted).use { git ->
            assertThat(git.tagList().call().map { it.name }).containsExactly("refs/tags/v1.2.3")
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `changed local configuration and Git inputs keep versions consistent`(version: SupportedGradleVersion) {
        val project = fixture("changed", adopted = true)
        verifySnapshot(run(project, version, true), project, "1.3.0-SNAPSHOT")
        project.resolve("alpha/build.gradle").appendText("\n// changed only this project's script\n")
        val changed = run(project, version, true)
        verifySnapshot(changed, project, "1.3.0-SNAPSHOT")
        assertThat(run(project, version, true).output).contains("Reusing configuration cache")

        Git.open(project).use { git ->
            git.add().addFilepattern(".").call()
            git.commit().setMessage("Change local configuration").setSign(false).call()
            git.tag().setName("v2.0.0").setAnnotated(false).call()
        }
        verifySnapshot(run(project, version, true), project, "2.1.0-SNAPSHOT")
        assertThat(run(project, version, true).output).contains("Reusing configuration cache")
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `settings inputs and local version only projects work without enabling participation`(version: SupportedGradleVersion) {
        val project = fixture("settings", adopted = true, pluginId = "nebula.release")
        project.resolve("settings.gradle").appendText("\nisolatedRelease { scope = 'major' }\n")
        verifySnapshot(run(project, version, true), project, "2.0.0-SNAPSHOT")
        project.resolve("settings.gradle").appendText("\nisolatedRelease { scope = 'patch' }\n")
        verifySnapshot(run(project, version, true), project, "1.2.4-SNAPSHOT")
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `unsupported adoption and workflows fail before legacy project access`(version: SupportedGradleVersion) {
        val project = fixture("diagnostics", adopted = true)
        val unsupported = runner(project, version, true, tasks = listOf("help"),
            extra = listOf("-Prelease.stage=custom")).buildAndFail()
        assertThat(unsupported.output).contains("Experimental isolated release")
            .contains("release.stage must be")
            .doesNotContain("cannot access 'Project")
        val custom = runner(project, version, true,
            extra = listOf("-Prelease.defaultVersioningStrategy=example.CustomStrategy")).buildAndFail()
        assertThat(custom.output).contains("custom strategy overrides are not implemented")

        val disabled = runner(project, version, false,
            extra = listOf("-Pnebula.release.isolatedProjectsSupport=false")).buildAndFail()
        assertThat(disabled.output).contains("com.netflix.nebula.release-settings requires")

        val rootBuild = project.resolve("build.gradle").readText()
        project.resolve("build.gradle").writeText(rootBuild.replace("com.netflix.nebula.release'", "com.netflix.nebula.release-base'"))
        assertThat(runner(project, version, true).buildAndFail().output)
            .contains("release-base requires isolatedRelease.baseRelease = true")
        project.resolve("build.gradle").writeText(rootBuild)

        project.resolve("settings.gradle").writeText("rootProject.name = 'sample'\n")
        val missing = runner(project, version, true).buildAndFail()
        assertThat(missing.output).contains("apply com.netflix.nebula.release-settings in settings.gradle")
            .doesNotContain("cannot dynamically look up a property")
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `explicit version is shared and direct assignment is diagnosed`(version: SupportedGradleVersion) {
        val project = fixture("override", adopted = true)
        verifySnapshot(run(project, version, true, listOf("-Prelease.version=4.5.6")), project, "4.5.6")
        project.resolve("alpha/build.gradle").appendText("\nversion = '9.9.9'\n")
        val failed = runner(project, version, true).buildAndFail()
        assertThat(failed.output).contains("project :alpha assigns project.version directly")
            .contains("use -Prelease.version instead")
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `legacy DSL and local extra properties cannot silently replace common settings`(version: SupportedGradleVersion) {
        val project = fixture("legacy-dsl", adopted = true)
        val original = project.resolve("build.gradle").readText()
        project.resolve("build.gradle").appendText("\nrelease { tagStrategy { prefixNameWithV = false } }\n")
        assertThat(runner(project, version, true).buildAndFail().output)
            .contains("legacy release.tagStrategy customization is not implemented")
        project.resolve("build.gradle").writeText(original + "\next['release.scope'] = 'major'\n")
        assertThat(runner(project, version, true).buildAndFail().output)
            .contains("sets extra property 'release.scope'")
        project.resolve("build.gradle").writeText(original + "\nnebulaRelease { excludeBranchPatterns = ['main'] }\n")
        assertThat(runner(project, version, true).buildAndFail().output)
            .contains("legacy nebulaRelease.excludeBranchPatterns customization is not implemented")
        for ((property, value) in mapOf("nebula.release.features.immutableSnapshot.timestampPrecision" to "SECONDS",
            "release.disableGitChecks" to "true", "release.travisci" to "true")) {
            project.resolve("build.gradle").writeText(original + "\next['$property'] = '$value'\n")
            assertThat(runner(project, version, true).buildAndFail().output)
                .contains("sets extra property '$property'")
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `single project root is a participating leaf`(version: SupportedGradleVersion) {
        val project = fixture("single", adopted = true)
        project.resolve("settings.gradle").writeText("""
            plugins { id 'com.netflix.nebula.release-settings' }
            rootProject.name = 'sample'
        """.trimIndent())
        val result = run(project, version, true)
        assertThat(result.task(":jar")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(project.resolve("build/libs/sample-1.3.0-SNAPSHOT.jar")).exists()
        assertThat(run(project, version, true).output).contains("Reusing configuration cache")
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `flag changes do not leak implementation state in the same daemon`(version: SupportedGradleVersion) {
        val project = fixture("toggle", adopted = true)
        project.resolve("settings.gradle").writeText("""
            plugins { id 'com.netflix.nebula.release-settings' apply false }
            if (providers.gradleProperty('nebula.release.isolatedProjectsSupport').map(Boolean.&parseBoolean).getOrElse(false)) {
                apply plugin: 'com.netflix.nebula.release-settings'
            }
            rootProject.name = 'sample'
            include 'alpha', 'parent:leaf', 'versionOnly'
        """.trimIndent())
        verifySnapshot(run(project, version, false), project, "1.3.0-SNAPSHOT")
        val legacy = run(project, version, false, listOf("-Pnebula.release.isolatedProjectsSupport=false"))
        verifySnapshot(legacy, project, "1.3.0-SNAPSHOT")
        assertThat(legacy.task(":nebulaReleaseBuild")).isNull()
        val adopted = run(project, version, false)
        verifySnapshot(adopted, project, "1.3.0-SNAPSHOT")
        assertThat(adopted.task(":nebulaReleaseBuild")).isNotNull()
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `tag reading and branch scopes use common inputs`(version: SupportedGradleVersion) {
        val project = fixture("tag-inputs", adopted = true)
        project.resolve("settings.gradle").appendText("\nisolatedRelease { prefixTagsWithV = false }\n")
        Git.open(project).use { git ->
            git.tag().setName("4.0.0").setAnnotated(false).call()
        }
        verifySnapshot(run(project, version, true), project, "4.1.0-SNAPSHOT")
        Git.open(project).use { git ->
            git.checkout().setCreateBranch(true).setName("release/5.x").call()
        }
        verifySnapshot(run(project, version, true), project, "5.0.0-SNAPSHOT")
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `snapshot setup validates without building or generating descriptors`(version: SupportedGradleVersion) {
        val project = fixture("setup-only", adopted = true)
        val result = runner(project, version, true, tasks = listOf(":snapshotSetup", "recordVersion")).build()
        assertThat(result.task(":releaseCheck")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.task(":alpha:jar")).isNull()
        assertThat(result.task(":release")).isNull()
        assertThat(project.resolve("alpha/build/publications/maven/pom-default.xml")).doesNotExist()
        assertThat(project.resolve("alpha/build/release-version.properties").readText()).contains("1.3.0-dev.")
        assertThat(runner(project, version, true, tasks = listOf(":snapshotSetup", "recordVersion")).build().output)
            .contains("Reusing configuration cache")
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `default git root follows the root project directory rather than settings directory`(version: SupportedGradleVersion) {
        val project = fixture("launcher/worktree", adopted = true)
        val launcher = project.parentFile
        check(project.resolve("settings.gradle").delete())
        launcher.resolve("gradle.properties").writeText("nebula.release.isolatedProjectsSupport=true\n")
        launcher.resolve("settings.gradle").writeText("""
            plugins { id 'com.netflix.nebula.release-settings' }
            rootProject.name = 'sample'
            rootProject.projectDir = file('worktree')
        """.trimIndent())
        val result = run(project, version, true)
        assertThat(result.task(":jar")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(project.resolve("build/libs/sample-1.3.0-SNAPSHOT.jar")).exists()
        assertThat(run(project, version, true).output).contains("Reusing configuration cache")
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `help and build retain default development versions without requesting a release stage`(version: SupportedGradleVersion) {
        val project = fixture("ordinary", adopted = true)
        project.resolve("settings.gradle").writeText("""
            plugins { id 'com.netflix.nebula.release-settings' apply false }
            if (providers.gradleProperty('nebula.release.isolatedProjectsSupport').map(Boolean.&parseBoolean).getOrElse(false)) {
                apply plugin: 'com.netflix.nebula.release-settings'
            }
            rootProject.name = 'sample'
            include 'alpha', 'parent:leaf', 'versionOnly'
        """.trimIndent())
        for (task in listOf("help", "build")) {
            val legacy = runner(project, version, false,
                extra = listOf("-Pnebula.release.isolatedProjectsSupport=false"),
                tasks = listOf(task, "recordVersion")).build()
            val expected = project.resolve("build/release-version.properties").readText()
            assertThat(expected).contains("version=1.3.0-dev.")
            assertThat(legacy.task(":$task")).isNotNull()
            for (isolated in listOf(false, true)) {
                val adopted = runner(project, version, isolated, tasks = listOf(task, "recordVersion")).build()
                assertThat(adopted.task(":$task")).isNotNull()
                for (path in listOf("", "alpha", "parent", "parent/leaf", "versionOnly")) {
                    assertThat(project.resolve(path).resolve("build/release-version.properties").readText())
                        .isEqualTo(expected)
                }
                assertThat(runner(project, version, isolated, tasks = listOf(task, "recordVersion")).build().output)
                    .contains("Reusing configuration cache")
            }
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `immutable snapshots share a timestamp across delayed configuration and cache reuse`(version: SupportedGradleVersion) {
        val project = fixture("immutable", adopted = true)
        project.resolve("alpha/build.gradle").appendText("\nThread.sleep(1100)\n")
        val options = listOf("-Pnebula.release.features.immutableSnapshot.timestampPrecision=MILLISECONDS")
        val tasks = listOf(":immutableSnapshot", "recordVersion")
        for (isolated in listOf(false, true)) {
            val cold = runner(project, version, isolated, options, tasks).build()
            val expected = recordedVersion(project)
            assertThat(expected).matches("1\\.3\\.0-snapshot\\.[0-9]{17}\\.uncommitted\\+[a-f0-9]{7}")
            verifySnapshot(cold, project, expected)
            assertThat(cold.task(":release")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
            val warm = runner(project, version, isolated, options, tasks).build()
            assertThat(warm.output).contains("Reusing configuration cache")
            verifySnapshot(warm, project, expected)
            project.resolve("parent/leaf/build.gradle").appendText("\n// invalidate one project\n")
            val changed = runner(project, version, isolated, options, tasks).build()
            val next = recordedVersion(project)
            assertThat(next).isNotEqualTo(expected)
            verifySnapshot(changed, project, next)
        }
        Git.open(project).use { git ->
            assertThat(git.tagList().call().map { it.name }).containsExactly("refs/tags/v1.2.3")
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `development workflow matches legacy and never tags`(version: SupportedGradleVersion) {
        val project = fixture("development", adopted = true)
        conditionalSettings(project)
        Git.open(project).use { git ->
            git.checkout().setCreateBranch(true).setName("feature/snapshots").call()
        }
        val tasks = listOf("devSnapshot", "recordVersion")
        val legacy = runner(project, version, false,
            listOf("-Pnebula.release.isolatedProjectsSupport=false"), tasks).build()
        val expected = recordedVersion(project)
        assertThat(expected).contains("1.3.0-dev.", "+snapshots.")
        verifySnapshot(legacy, project, expected)
        for (isolated in listOf(false, true)) {
            val result = runner(project, version, isolated, tasks = tasks).build()
            verifySnapshot(result, project, expected)
            assertThat(result.task(":release")!!.outcome).isEqualTo(TaskOutcome.SKIPPED)
            assertThat(runner(project, version, isolated, tasks = tasks).build().output)
                .contains("Reusing configuration cache")
        }
        Git.open(project).use { git ->
            assertThat(git.tagList().call().map { it.name }).containsExactly("refs/tags/v1.2.3")
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `replacement flag applies to default and explicit development with precision and branch metadata`(version: SupportedGradleVersion) {
        val project = fixture("replacement", adopted = true)
        Git.open(project).use { git ->
            git.checkout().setCreateBranch(true).setName("feature/snapshots").call()
        }
        val options = listOf("-Pnebula.release.features.replaceDevWithImmutableSnapshot=true",
            "-Pnebula.release.features.immutableSnapshot.timestampPrecision=DAYS")
        for (task in listOf("help", "devSnapshot")) {
            val tasks = listOf(task, "recordVersion")
            val result = runner(project, version, true, options, tasks).build()
            val expected = recordedVersion(project)
            assertThat(expected).matches("1\\.3\\.0-snapshot\\.[0-9]{8}\\+snapshots\\.[a-f0-9]{7}")
            for (path in listOf("", "alpha", "parent", "parent/leaf", "versionOnly")) {
                assertThat(recordedVersion(project.resolve(path))).isEqualTo(expected)
            }
            if (task == "devSnapshot") verifySnapshot(result, project, expected)
            assertThat(runner(project, version, true, options, tasks).build().output)
                .contains("Reusing configuration cache")
        }
        project.resolve("settings.gradle").appendText("\nisolatedRelease { timestampPrecision = 'SECONDS' }\n")
        runner(project, version, true, options, listOf("help", "recordVersion")).build()
        assertThat(recordedVersion(project)).matches("1\\.3\\.0-snapshot\\.[0-9]{14}\\.uncommitted\\+snapshots\\.[a-f0-9]{7}")
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `snapshot stages cannot be combined and setup tasks retain default selection`(version: SupportedGradleVersion) {
        val project = fixture("snapshot-stages", adopted = true)
        assertThat(runner(project, version, true, tasks = listOf("snapshot", "devSnapshot")).buildAndFail().output)
            .contains("Only one of snapshot, immutableSnapshot, devSnapshot, candidate, or final can be specified")
        for (setup in listOf("devSnapshotSetup", "immutableSnapshotSetup")) {
            val result = runner(project, version, true, tasks = listOf(setup, "recordVersion")).build()
            assertThat(recordedVersion(project)).startsWith("1.3.0-dev.")
            assertThat(result.task(":release")).isNull()
            assertThat(result.task(":alpha:jar")).isNull()
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `explicit snapshot versions preserve legacy tag decisions and root owned pushes`(version: SupportedGradleVersion) {
        for (mode in listOf("legacy", "cache", "isolated")) {
            for (workflow in listOf("snapshot", "devSnapshot", "immutableSnapshot")) {
                val project = fixture("override-$mode-$workflow", adopted = true)
                conditionalSettings(project)
                val tagName = if (mode == "isolated") "4.5.6" else "v4.5.6"
                if (mode == "isolated") {
                    project.resolve("settings.gradle").appendText("\nisolatedRelease { prefixTagsWithV = false }\n")
                }
                val remote = directory.resolve("remote-$mode-$workflow.git")
                Git.init().setBare(true).setDirectory(remote).call().close()
                val head = Git.open(project).use { git ->
                    git.remoteAdd().setName("origin").setUri(URIish(remote.toURI().toString())).call()
                    git.repository.resolve("HEAD")
                }
                val options = listOf("-Prelease.version=4.5.6") +
                    if (mode == "legacy") listOf("-Pnebula.release.isolatedProjectsSupport=false") else emptyList()
                val result = runner(project, version, mode == "isolated", options,
                    listOf(workflow, "recordVersion")).build()
                verifySnapshot(result, project, "4.5.6")
                val tagging = workflow == "immutableSnapshot"
                assertThat(result.task(":release")!!.outcome)
                    .isEqualTo(if (tagging) TaskOutcome.SUCCESS else TaskOutcome.SKIPPED)
                Git.open(project).use { git ->
                    assertThat(git.tagList().call().map { it.name }).containsExactlyInAnyOrderElementsOf(
                        listOf("refs/tags/v1.2.3") + if (tagging) listOf("refs/tags/$tagName") else emptyList())
                }
                Git.open(remote).use { git ->
                    val tags = git.tagList().call()
                    assertThat(tags.map { it.name }).containsExactlyElementsOf(
                        if (tagging) listOf("refs/tags/$tagName") else emptyList())
                    if (tagging) RevWalk(git.repository).use { walk ->
                        val tag = walk.parseTag(tags.single().objectId)
                        assertThat(tag.`object`.id).isEqualTo(head)
                        assertThat(tag.fullMessage).isEqualTo("Release of 4.5.6\n")
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `immutable snapshots honor remote check settings and snapshot skip controls`(version: SupportedGradleVersion) {
        val project = fixture("remote-check", adopted = true)
        project.resolve("settings.gradle").appendText("\nisolatedRelease { checkRemoteBranchOnRelease = true }\n")
        // No origin exists: a requested immutable check must fail rather than be ignored.
        val failed = runner(project, version, true, tasks = listOf("immutableSnapshot")).buildAndFail()
        assertThat(failed.task(":prepare")!!.outcome).isEqualTo(TaskOutcome.FAILED)
        for (workflow in listOf("snapshot", "devSnapshot")) {
            val result = runner(project, version, true, tasks = listOf(workflow)).build()
            assertThat(result.task(":prepare")!!.outcome).isEqualTo(TaskOutcome.SKIPPED)
        }
        for (skip in listOf("release.disableGitChecks", "release.travisci")) {
            val result = runner(project, version, true, listOf("-P$skip=true", "-Prelease.version=4.5.6"), listOf("immutableSnapshot")).build()
            assertThat(result.task(":prepare")!!.outcome).isEqualTo(TaskOutcome.SKIPPED)
            assertThat(result.task(":release")!!.outcome).isEqualTo(TaskOutcome.SKIPPED)
        }
        Git.open(project).use { git ->
            assertThat(git.tagList().call().map { it.name }).containsExactly("refs/tags/v1.2.3")
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `no commit default replacement version precedes a supplied version`(version: SupportedGradleVersion) {
        val project = fixture("no-commits", adopted = true, committed = false)
        val options = listOf("-Pnebula.release.features.replaceDevWithImmutableSnapshot=true", "-Prelease.version=9.9.9")
        val tasks = listOf("help", "recordVersion")
        val result = runner(project, version, true, options, tasks).build()
        val expected = recordedVersion(project)
        assertThat(expected).matches("0\\.1\\.0-snapshot\\.[0-9]{12}\\.uncommitted")
        for (path in listOf("", "alpha", "parent", "parent/leaf", "versionOnly")) {
            assertThat(recordedVersion(project.resolve(path))).isEqualTo(expected)
        }
        assertThat(result.task(":release")).isNull()
        assertThat(runner(project, version, true, options, tasks).build().output).contains("Reusing configuration cache")
        Git.open(project).use { git -> assertThat(git.tagList().call()).isEmpty() }
        for (workflow in listOf("immutableSnapshot", ":immutableSnapshotSetup")) {
            assertThat(runner(project, version, true, options, listOf(workflow)).buildAndFail().output)
                .contains("release orchestration requires an initial Git commit")
        }
        Git.open(project).use { git ->
            git.add().addFilepattern(".").call()
            git.commit().setMessage("Initial commit").setSign(false).call()
        }
        runner(project, version, true, options, tasks).build()
        for (path in listOf("", "alpha", "parent", "parent/leaf", "versionOnly")) {
            assertThat(recordedVersion(project.resolve(path))).isEqualTo("9.9.9")
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `abbreviated workflows cannot silently use a different stage or tag an explicit version`(version: SupportedGradleVersion) {
        val project = fixture("selectors", adopted = true)
        for (selector in listOf("snap", ":dS", "iS", "pR", ":SNAPSHOT")) {
            val result = runner(project, version, true, listOf("-Prelease.version=4.5.6"), listOf(selector)).buildAndFail()
            assertThat(result.output).contains("abbreviated workflow selector '$selector' is not supported")
            assertThat(result.task(":release")).isNull()
        }
        Git.open(project).use { git ->
            assertThat(git.tagList().call().map { it.name }).containsExactly("refs/tags/v1.2.3")
        }
        project.resolve("build.gradle").appendText("\ntasks.register('iS')\n")
        assertThat(runner(project, version, true, tasks = listOf("iS")).build().task(":iS")).isNotNull()
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `candidate and final versions status artifacts and pushed tags match legacy`(version: SupportedGradleVersion) {
        for (mode in listOf("legacy", "cache", "isolated")) {
            for ((workflow, expected, status) in listOf(
                Triple("candidate", "1.3.0-rc.1", "candidate"), Triple("final", "1.3.0", "release"))) {
                val project = fixture("tagged-$mode-$workflow", adopted = mode != "legacy")
                project.resolve("build.gradle").appendText("""

                    apply plugin: 'ivy-publish'
                    publishing { publications { ivy(IvyPublication) { from components.java } } }
                """.trimIndent())
                commit(project)
                val remote = remote(project)
                val result = runner(project, version, mode == "isolated",
                    extra = if (mode == "legacy") listOf("--no-configuration-cache") else emptyList(),
                    tasks = listOf(":$workflow", "recordVersion")).build()
                verifySnapshot(result, project, expected)
                verifyStatus(project, status)
                assertThat(project.resolve("build/publications/ivy/ivy.xml").readText()).contains("status=\"$status\"")
                assertThat(project.resolve("parent/leaf/build/publications/ivy/ivy.xml").readText()).contains("status=\"integration\"")
                assertThat(result.task(":prepare")!!.outcome).isEqualTo(TaskOutcome.SKIPPED)
                assertThat(result.task(":release")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
                verifyTag(project, remote, "v$expected", expected)
                Git.open(remote).use { git ->
                    assertThat(git.tagList().call().map { it.name }).containsExactly("refs/tags/v$expected")
                }
                if (mode == "isolated" && workflow == "candidate") {
                    // Creating the first tag must invalidate inference, even with an unchanged HEAD.
                    verifySnapshot(runner(project, version, true,
                        tasks = listOf(workflow, "recordVersion")).build(), project, "1.3.0-rc.2")
                    verifyTag(project, remote, "v1.3.0-rc.2", "1.3.0-rc.2")
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `release setup tasks set local status without choosing a stage and reuse cache`(version: SupportedGradleVersion) {
        for (adopted in listOf(false, true)) {
            for ((setup, status) in listOf("candidateSetup" to "candidate", "finalSetup" to "release")) {
                val project = fixture("setup-$adopted-$setup", adopted)
                val tasks = listOf(setup, "recordVersion", ":parent:leaf:generateDescriptorFileForIvyPublication")
                val cold = runner(project, version, adopted, tasks = tasks).build()
                assertThat(cold.task(":release")).isNull()
                assertThat(recordedVersion(project)).startsWith("1.3.0-dev.")
                verifyStatus(project, status)
                assertThat(project.resolve("parent/leaf/build/publications/ivy/ivy.xml").readText())
                    .contains("status=\"integration\"")
                assertThat(runner(project, version, adopted, tasks = tasks).build().output)
                    .contains("Reusing configuration cache")
            }
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `last tag reuse preserves validation precedence status and warm cache`(version: SupportedGradleVersion) {
        for (mode in listOf("legacy", "cache", "isolated")) {
            for ((workflow, tag) in listOf("candidate" to "1.3.0-rc.7", "final" to "1.3.0")) {
                val project = fixture("reuse-$mode-$workflow", mode != "legacy")
                Git.open(project).use { it.tag().setName("v$tag").setAnnotated(false).call() }
                val remote = remote(project)
                // Reuse wins over an explicit version.
                val options = listOf("-Prelease.useLastTag=true", "-Prelease.version=9.9.9")
                val tasks = listOf(workflow, "recordVersion")
                val result = runner(project, version, mode == "isolated", options, tasks).build()
                verifySnapshot(result, project, tag)
                assertThat(result.task(":release")!!.outcome).isEqualTo(TaskOutcome.SKIPPED)
                val warm = runner(project, version, mode == "isolated", options, tasks).build()
                assertThat(warm.output).contains("Reusing configuration cache")
                Git.open(project).use { git ->
                    assertThat(git.tagList().call().map { it.name }).containsExactlyInAnyOrder("refs/tags/v1.2.3", "refs/tags/v$tag")
                }
                Git.open(remote).use { git -> assertThat(git.tagList().call()).isEmpty() }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `last tag rejects mismatched stages snapshots and untagged commits`(version: SupportedGradleVersion) {
        for (adopted in listOf(false, true)) {
            val project = fixture("reuse-validation-$adopted", adopted)
            val options = listOf("-Prelease.useLastTag=true")
            assertThat(runner(project, version, adopted, options, listOf("candidate")).buildAndFail().output)
                .contains("does not appear to be a pre-release version")
            Git.open(project).use { it.tag().setName("v1.3.0-rc.1").setAnnotated(false).call() }
            assertThat(runner(project, version, adopted, options, listOf("final")).buildAndFail().output)
                .contains("does not appear to be a final version")
            for (workflow in listOf("snapshot", "immutableSnapshot", "devSnapshot")) {
                assertThat(runner(project, version, adopted, options, listOf(workflow)).buildAndFail().output)
                    .contains("Cannot use useLastTag with snapshot, immutableSnapshot and devSnapshot tasks")
            }
            Git.open(project).use { git ->
                git.commit().setAllowEmpty(true).setMessage("Untagged").setSign(false).call()
            }
            assertThat(runner(project, version, adopted, options, listOf("final")).buildAndFail().output)
                .contains("Current commit does not have a tag")
            Git.open(project).use { it.tag().setName("not-a-version").setAnnotated(false).call() }
            assertThat(runner(project, version, adopted, options, listOf("help")).buildAndFail().output)
                .contains("not recognized as valid versions")
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `release validation rejects dirty invalid and excluded branches even with Git skip flags`(version: SupportedGradleVersion) {
        for (adopted in listOf(false, true)) {
            val project = fixture("validation-$adopted", adopted)
            project.resolve("alpha/src/main/java/Sample.java").appendText("// dirty\n")
            for (skip in listOf("release.disableGitChecks", "release.travisci")) {
                assertThat(runner(project, version, adopted, listOf("-P$skip=true"), listOf("final")).buildAndFail().output)
                    .contains("Sample.java")
            }
            commit(project)
            Git.open(project).use { it.checkout().setCreateBranch(true).setName("feature/invalid").call() }
            val failed = runner(project, version, adopted, listOf("-Prelease.disableGitChecks=true"), listOf("candidate")).buildAndFail()
            assertThat(failed.output).contains("does not match one of the included patterns")
            assertThat(failed.task(":release")?.outcome).isIn(null, TaskOutcome.SKIPPED)
            project.resolve(if (adopted) "settings.gradle" else "build.gradle").appendText(
                if (adopted) "\nisolatedRelease { excludeBranchPatterns = ['feature/.*'] }\n"
                else "\nnebulaRelease { excludeBranchPatterns = ['feature/.*'] }\n")
            commit(project)
            assertThat(runner(project, version, adopted, tasks = listOf("snapshotSetup")).buildAndFail().output)
                .contains("matched an excluded pattern")
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `release scope explicit stages and settings control tagging`(version: SupportedGradleVersion) {
        val project = fixture("tag-controls", true)
        project.resolve("settings.gradle").appendText("\nisolatedRelease { prefixTagsWithV = false; scope = 'patch' }\n")
        commit(project)
        Git.open(project).use { it.tag().setName("2.0.0").setAnnotated(false).call() }
        val remote = remote(project)
        verifySnapshot(runner(project, version, true, tasks = listOf("candidate", "recordVersion")).build(), project, "2.0.1-rc.1")
        verifyTag(project, remote, "2.0.1-rc.1", "2.0.1-rc.1")

        val lower = runner(project, version, true, listOf("-Prelease.stage=milestone"), listOf("help")).buildAndFail()
        assertThat(lower.output).contains("nearest version is 2.0.1-rc.1").contains("attempting to release 2.0.1-milestone.1")
        for (stage in listOf("rc", "final")) {
            val options = listOf("-Prelease.stage=$stage", "-Prelease.disableGitChecks=true")
            val result = runner(project, version, true, options, listOf("release", "recordVersion")).build()
            assertThat(result.task(":release")!!.outcome).isEqualTo(TaskOutcome.SKIPPED)
            assertThat(recordedVersion(project)).isEqualTo(
                if (stage == "final") "2.0.1" else "2.0.1-$stage.${if (stage == "rc") 2 else 1}")
            assertThat(runner(project, version, true, options, listOf("release", "recordVersion")).build().output)
                .contains("Reusing configuration cache")
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `optional preparation catches a branch behind its remote before tagging`(version: SupportedGradleVersion) {
        val project = fixture("behind", true)
        project.resolve("settings.gradle").appendText("\nisolatedRelease { checkRemoteBranchOnRelease = true }\n")
        commit(project)
        val remote = remote(project)
        Git.open(project).use { git ->
            git.push().setRemote("origin").add("main").call()
            git.repository.config.apply {
                setString("branch", "main", "remote", "origin")
                setString("branch", "main", "merge", "refs/heads/main")
                save()
            }
        }
        Git.cloneRepository().setURI(remote.toURI().toString()).setDirectory(directory.resolve("ahead")).call().use { git ->
            git.commit().setAllowEmpty(true).setMessage("Ahead").setAuthor("Test", "test@example.test")
                .setCommitter("Test", "test@example.test").setSign(false).call()
            git.push().setRemote("origin").add("main").call()
        }
        val result = runner(project, version, true, tasks = listOf("final")).buildAndFail()
        assertThat(result.output).contains("Current branch is behind the tracked branch. Cannot release.")
        assertThat(result.task(":prepare")!!.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.task(":release")).isNull()
        Git.open(remote).use { assertThat(it.tagList().call()).isEmpty() }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `explicit release versions and Git skip controls preserve legacy outcomes`(version: SupportedGradleVersion) {
        for (adopted in listOf(false, true)) {
            for ((workflow, skip) in listOf("candidate" to "release.disableGitChecks", "final" to "release.travisci")) {
                val project = fixture("explicit-$adopted-$workflow", adopted)
                val remote = remote(project)
                val tasks = listOf(workflow, "recordVersion")
                val options = listOf("-Prelease.version=4.5.6-rc.8", "-P$skip=true")
                val result = runner(project, version, adopted, options, tasks).build()
                verifySnapshot(result, project, "4.5.6-rc.8")
                assertThat(result.task(":release")!!.outcome).isEqualTo(TaskOutcome.SKIPPED)
                assertThat(result.task(":prepare")!!.outcome).isEqualTo(TaskOutcome.SKIPPED)
                assertThat(runner(project, version, adopted, options, tasks).build().output).contains("Reusing configuration cache")
                Git.open(remote).use { assertThat(it.tagList().call()).isEmpty() }
                val tagged = runner(project, version, adopted, listOf("-Prelease.version=4.5.6-rc.8"), tasks).build()
                assertThat(tagged.task(":release")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
                verifyTag(project, remote, "v4.5.6-rc.8", "4.5.6-rc.8")
            }
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `release Git identity checks are skipped only when reusing a tag`(version: SupportedGradleVersion) {
        for (adopted in listOf(false, true)) {
            val project = fixture("identity-$adopted", adopted)
            Git.open(project).use { git ->
                git.repository.config.apply {
                    unset("user", null, "name")
                    unset("user", null, "email")
                    save()
                }
            }
            val environment = System.getenv() + mapOf("GIT_CONFIG_GLOBAL" to "/dev/null", "GIT_CONFIG_NOSYSTEM" to "1")
            assertThat(runner(project, version, adopted, tasks = listOf("final"))
                .withEnvironment(environment).buildAndFail().output).contains("Git user.name is not set")
            val reused = runner(project, version, adopted, listOf("-Prelease.useLastTag=true"),
                listOf("final", "recordVersion")).withEnvironment(environment).build()
            verifySnapshot(reused, project, "1.2.3")
            assertThat(reused.task(":release")!!.outcome).isEqualTo(TaskOutcome.SKIPPED)
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `isolated release validates branches before tagging and diagnoses conflicting status`(version: SupportedGradleVersion) {
        val project = fixture("branch-order", true)
        val remote = remote(project)
        Git.open(project).use { it.checkout().setCreateBranch(true).setName("feature/blocked").call() }
        val invalid = runner(project, version, true, listOf("--parallel"), listOf("final")).buildAndFail()
        assertThat(invalid.output).contains("does not match one of the included patterns")
        assertThat(invalid.task(":release")).isNull()
        Git.open(remote).use { assertThat(it.tagList().call()).isEmpty() }

        val conflict = runner(project, version, true, tasks = listOf("candidateSetup", "finalSetup")).buildAndFail()
        assertThat(conflict.output).contains("candidate and final setup/status cannot be combined")
        val stages = runner(project, version, true, tasks = listOf("candidate", "final")).buildAndFail()
        assertThat(stages.output).contains("Only one of snapshot, immutableSnapshot, devSnapshot, candidate, or final")
        for (selector in listOf("cand", ":cS", "fi", ":fS")) {
            assertThat(runner(project, version, true, tasks = listOf(selector)).buildAndFail().output)
                .contains("abbreviated workflow selector '$selector'")
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `direct orchestration retains default selection participation and cache reuse`(version: SupportedGradleVersion) {
        for (mode in listOf("legacy", "cache", "isolated")) {
            for (workflow in listOf("prepare", "release", "postRelease")) {
                val project = fixture("direct-$mode-$workflow", mode != "legacy")
                val remote = remote(project)
                val tasks = listOf(workflow, "recordVersion")
                val cold = runner(project, version, mode == "isolated", tasks = tasks).build()
                assertThat(recordedVersion(project)).startsWith("1.3.0-dev.")
                verifyStatus(project, "integration")
                assertThat(cold.task(":prepare")!!.outcome).isEqualTo(TaskOutcome.SKIPPED)
                assertThat(cold.task(":alpha:jar") != null).isEqualTo(workflow != "prepare")
                assertThat(cold.task(":parent:leaf:generateDescriptorFileForIvyPublication") != null)
                    .isEqualTo(workflow == "postRelease")
                if (workflow != "prepare") assertThat(cold.task(":release")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
                assertThat(cold.task(":versionOnly:jar")).isNull()
                assertThat(runner(project, version, mode == "isolated", tasks = tasks).build().output)
                    .contains("Reusing configuration cache")
                Git.open(remote).use { assertThat(it.tagList().call()).isEmpty() }
            }
        }
    }

    private fun commit(project: File) = Git.open(project).use { git ->
        git.add().addFilepattern(".").call()
        git.commit().setMessage("Configure fixture").setSign(false).call()
    }

    private fun remote(project: File): File {
        val remote = directory.resolve("${project.name}-remote.git")
        Git.init().setBare(true).setInitialBranch("main").setDirectory(remote).call().close()
        Git.open(project).use { it.remoteAdd().setName("origin").setUri(URIish(remote.toURI().toString())).call() }
        return remote
    }

    private fun verifyTag(project: File, remote: File, tag: String, version: String) {
        val head = Git.open(project).use { it.repository.resolve("HEAD") }
        for (repository in listOf(project, remote)) {
            Git.open(repository).use { git ->
                RevWalk(git.repository).use { walk ->
                    val annotation = walk.parseTag(git.repository.resolve("refs/tags/$tag"))
                    assertThat(annotation.fullMessage).isEqualTo("Release of $version\n")
                    assertThat(annotation.`object`.id).isEqualTo(head)
                }
            }
        }
    }

    private fun verifyStatus(project: File, status: String) {
        for (path in listOf("", "alpha", "parent", "parent/leaf", "versionOnly")) {
            assertThat(project.resolve(path).resolve("build/release-version.properties").readText()).contains("status=$status")
        }
    }

    private fun recordedVersion(project: File): String = java.util.Properties().apply {
        project.resolve("build/release-version.properties").inputStream().use { load(it) }
    }.getProperty("version")

    private fun conditionalSettings(project: File) {
        project.resolve("settings.gradle").writeText("""
            plugins { id 'com.netflix.nebula.release-settings' apply false }
            if (providers.gradleProperty('nebula.release.isolatedProjectsSupport').map(Boolean.&parseBoolean).getOrElse(false)) {
                apply plugin: 'com.netflix.nebula.release-settings'
            }
            rootProject.name = 'sample'
            include 'alpha', 'parent:leaf', 'versionOnly'
        """.trimIndent())
    }

    private fun fixture(name: String, adopted: Boolean, pluginId: String = "com.netflix.nebula.release",
                        committed: Boolean = true): File {
        val project = directory.resolve(name).apply { mkdirs() }
        project.resolve("settings.gradle").writeText(
            (if (adopted) "plugins { id 'com.netflix.nebula.release-settings' }\n" else "") +
                "rootProject.name = 'sample'\ninclude 'alpha', 'parent:leaf', 'versionOnly'\n"
        )
        project.resolve("gradle.properties").writeText(if (adopted) "nebula.release.isolatedProjectsSupport=true\n" else "")
        project.resolve(".gitignore").writeText(".gradle/\nbuild/\n")
        val recordVersion = """
            def inferredVersion = project.version.toString()
            tasks.register('recordVersion', WriteProperties) {
                destinationFile = layout.buildDirectory.file('release-version.properties')
                property 'version', inferredVersion
                property 'status', providers.provider { project.status.toString() }
            }
        """.trimIndent()
        for (path in listOf("", "alpha", "parent", "parent/leaf", "versionOnly")) {
            val target = project.resolve(path).apply { mkdirs() }
            target.resolve("build.gradle").writeText(
                "plugins { id 'java'" +
                    (if (path == "versionOnly") "" else "; id '$pluginId'") + " }\n" +
                    "group = 'sample'\n" + recordVersion + "\n"
            )
            // A real class gives the produced JAR an observable payload.
            target.resolve("src/main/java/Sample.java").apply {
                parentFile.mkdirs()
                writeText("public class Sample {}\n")
            }
        }
        project.resolve("alpha/build.gradle").appendText("""

            apply plugin: 'maven-publish'
            publishing { publications { maven(MavenPublication) { from components.java } } }
        """.trimIndent())
        project.resolve("parent/leaf/build.gradle").appendText("""

            apply plugin: 'ivy-publish'
            publishing { publications { ivy(IvyPublication) { from components.java } } }
        """.trimIndent())
        Git.init().setDirectory(project).setInitialBranch("main").call().use { git ->
            git.repository.config.apply {
                setString("user", null, "name", "Release test")
                setString("user", null, "email", "release@example.test")
                save()
            }
            if (committed) {
                git.add().addFilepattern(".").call()
                git.commit().setMessage("Fixture").setSign(false).call()
                git.tag().setName("v1.2.3").setAnnotated(false).call()
            }
        }
        return project
    }

    private fun verifySnapshot(result: BuildResult, project: File, expected: String) {
        for (path in listOf("", "alpha", "parent", "parent/leaf", "versionOnly")) {
            assertThat(project.resolve(path).resolve("build/release-version.properties").readText())
                .contains("version=$expected")
        }
        assertThat(result.task(":alpha:jar")!!.outcome).isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE)
        assertThat(result.task(":parent:leaf:jar")!!.outcome).isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE)
        assertThat(result.task(":jar")).isNull()
        assertThat(result.task(":parent:jar")).isNull()
        assertThat(result.task(":versionOnly:jar")).isNull()
        for ((path, name) in listOf("alpha" to "alpha", "parent/leaf" to "leaf")) {
            val jar = project.resolve("$path/build/libs/$name-$expected.jar")
            assertThat(jar).exists()
            java.util.jar.JarFile(jar).use { assertThat(it.getEntry("Sample.class")).isNotNull() }
        }
        assertThat(project.resolve("alpha/build/publications/maven/pom-default.xml").readText())
            .contains("<version>$expected</version>")
        assertThat(project.resolve("parent/leaf/build/publications/ivy/ivy.xml").readText())
            .contains("revision=\"$expected\"")
    }

    private fun run(project: File, version: SupportedGradleVersion, isolated: Boolean,
                    extra: List<String> = emptyList()) = runner(project, version, isolated, extra).build()

    private fun runner(project: File, version: SupportedGradleVersion, isolated: Boolean,
                       extra: List<String> = emptyList(), tasks: List<String> = listOf("snapshot", "recordVersion")) =
        GradleRunner.create().withProjectDir(project).withPluginClasspath()
            .apply { withGradle(version.version) }
            .withArguments(tasks + extra + listOf(
                "-Dorg.gradle.unsafe.isolated-projects=$isolated", "--configuration-cache",
                "--configuration-cache-problems=fail", "--stacktrace"
            ))
}
