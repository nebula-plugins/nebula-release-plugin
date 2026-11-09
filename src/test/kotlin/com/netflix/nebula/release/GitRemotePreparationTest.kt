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
import org.eclipse.jgit.transport.URIish
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File

internal class GitRemotePreparationTest {
    @TempDir
    lateinit var directory: File

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `successful fetch with progress on stderr permits release`(version: SupportedGradleVersion) {
        for (isolated in listOf(false, true)) {
            val (project, remote) = fixture("success-$isolated", isolated)
            advanceRemote(remote, "other-$isolated")
            val result = runner(project, version, isolated).build()
            assertThat(result.task(":prepare")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(result.task(":release")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
            Git.open(project).use { git ->
                assertThat(git.repository.resolve("refs/remotes/origin/other-$isolated")).isNotNull()
            }
            Git.open(remote).use { git -> assertThat(git.repository.resolve("refs/tags/v1.3.0")).isNotNull() }
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `fetching new upstream commits rejects a behind branch before tagging`(version: SupportedGradleVersion) {
        for (isolated in listOf(false, true)) {
            val (project, remote) = fixture("behind-$isolated", isolated)
            advanceRemote(remote, "main")
            val result = runner(project, version, isolated).buildAndFail()
            assertThat(result.output).contains("Current branch is behind the tracked branch. Cannot release.")
            assertThat(result.task(":prepare")!!.outcome).isEqualTo(TaskOutcome.FAILED)
            assertThat(result.task(":release")).isNull()
            Git.open(remote).use { git -> assertThat(git.tagList().call()).isEmpty() }
        }
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `missing remote still fails preparation`(version: SupportedGradleVersion) {
        val (project, _) = fixture("missing", false)
        Git.open(project).use { git ->
            git.remoteSetUrl().setRemoteName("origin").setRemoteUri(URIish(directory.resolve("absent.git").toURI().toString())).call()
        }
        val result = runner(project, version, false).buildAndFail()
        assertThat(result.output).contains("Could not fetch remote changes")
        assertThat(result.task(":prepare")!!.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.task(":release")).isNull()
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `nonzero exit without stderr still fails a Git write command`(version: SupportedGradleVersion) {
        val (project, _) = fixture("silent", false)
        project.resolve("build.gradle").appendText("""

            abstract class MissingConfig extends nebula.plugin.release.git.command.GitWriteCommand {
                String obtain() { executeGitCommand('config', '--get', 'nebula.absent') }
            }
            providers.of(MissingConfig) { parameters.rootDir.set(layout.projectDirectory.asFile) }.get()
        """.trimIndent())
        val result = runner(project, version, false, "help").buildAndFail()
        assertThat(result.output).contains("Git command failed with exit code 1")
    }

    private fun fixture(name: String, isolated: Boolean): Pair<File, File> {
        val project = directory.resolve(name).apply { mkdirs() }
        project.resolve("settings.gradle").writeText(
            (if (isolated) "plugins { id 'com.netflix.nebula.release-settings' }\nisolatedRelease { checkRemoteBranchOnRelease = true }\n" else "") +
                "rootProject.name = 'preparation'\n")
        project.resolve("gradle.properties").writeText("nebula.release.isolatedProjectsSupport=$isolated\n")
        project.resolve("build.gradle").writeText("""
            plugins { id 'com.netflix.nebula.release' }
            ${if (isolated) "" else "nebulaRelease { checkRemoteBranchOnRelease = true }"}
        """.trimIndent())
        project.resolve(".gitignore").writeText(".gradle/\nbuild/\n")
        val remote = directory.resolve("$name-remote.git")
        Git.init().setBare(true).setInitialBranch("main").setDirectory(remote).call().close()
        Git.init().setInitialBranch("main").setDirectory(project).call().use { git ->
            git.repository.config.apply {
                setString("user", null, "name", "Release test")
                setString("user", null, "email", "release@example.test")
                setString("branch", "main", "remote", "origin")
                setString("branch", "main", "merge", "refs/heads/main")
                save()
            }
            git.add().addFilepattern(".").call()
            git.commit().setMessage("Fixture").setSign(false).call()
            git.tag().setName("v1.2.3").setAnnotated(false).call()
            git.remoteAdd().setName("origin").setUri(URIish(remote.toURI().toString())).call()
            git.push().setRemote("origin").add("main").call()
        }
        return project to remote
    }

    private fun advanceRemote(remote: File, branch: String) {
        val clone = directory.resolve("${remote.name}-clone")
        Git.cloneRepository().setURI(remote.toURI().toString()).setDirectory(clone).call().use { git ->
            if (branch != "main") git.checkout().setCreateBranch(true).setName(branch).call()
            git.commit().setAllowEmpty(true).setMessage("Ahead").setAuthor("Test", "test@example.test")
                .setCommitter("Test", "test@example.test").setSign(false).call()
            git.push().setRemote("origin").add(branch).call()
        }
    }

    private fun runner(project: File, version: SupportedGradleVersion, isolated: Boolean, task: String = "immutableSnapshot") =
        GradleRunner.create().withProjectDir(project).withPluginClasspath()
            .apply { withGradle(version.version) }
            .withArguments(task, "-Prelease.version=1.3.0", "-Dorg.gradle.unsafe.isolated-projects=$isolated",
                "--configuration-cache", "--configuration-cache-problems=fail", "--stacktrace")
}
