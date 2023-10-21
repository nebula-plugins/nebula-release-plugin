/*
 * Copyright 2012-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nebula.plugin.release.git.base

import nebula.plugin.release.git.command.GitReadOnlyCommandUtil
import nebula.plugin.release.git.command.GitWriteCommandsUtil

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

import spock.lang.Specification

class ReleasePluginExtensionSpec extends Specification {
    def 'infers default version if selector returns false for all but default'() {
        given:
        Project project = ProjectBuilder.builder().build()
        ReleasePluginExtension extension = new ReleasePluginExtension(project)
        extension.gitReadCommands = GroovyMock(GitReadOnlyCommandUtil)
        extension.gitWriteCommands = GroovyMock(GitWriteCommandsUtil)
        extension.versionStrategy([
            getName: { 'b' },
            selector: { proj, git -> false },
            infer: { proj, git -> new ReleaseVersion('1.0.0', null, true) }] as VersionStrategy)
        extension.defaultVersionStrategy = [
            getName: { 'a' },
            selector: { proj, git -> true },
            infer: { proj, git -> new ReleaseVersion('1.2.3', null, true) }] as VersionStrategy
        expect:
        project.version.toString() == '1.2.3'
    }

    def 'infers using first strategy selector returns true for'() {
        Project project = ProjectBuilder.builder().build()
        ReleasePluginExtension extension = new ReleasePluginExtension(project)
        extension.gitReadCommands = GroovyMock(GitReadOnlyCommandUtil)
        extension.gitWriteCommands = GroovyMock(GitWriteCommandsUtil)
        extension.versionStrategy([
            getName: { 'b' },
            selector: { proj, gitOps -> false },
            infer: { proj, git -> new ReleaseVersion('1.0.0', null, true) }] as VersionStrategy)
        extension.versionStrategy([
            getName: { 'a' },
            selector: { proj, gitOps -> true },
            infer: { proj, git -> new ReleaseVersion('1.2.3', null, true) }] as VersionStrategy)
        expect:
        project.version.toString() == '1.2.3'
    }

    def 'infers using first strategy selector returns true for in order'() {
        Project project = ProjectBuilder.builder().build()
        ReleasePluginExtension extension = new ReleasePluginExtension(project)
        extension.gitReadCommands = GroovyMock(GitReadOnlyCommandUtil)
        extension.gitWriteCommands = GroovyMock(GitWriteCommandsUtil)
        extension.versionStrategy([
            getName: { 'b' },
            selector: { proj, gitOps -> true },
            infer: { proj, git -> new ReleaseVersion('1.0.0', null, true) }] as VersionStrategy)
        extension.versionStrategy([
            getName: { 'a' },
            selector: { proj, gitOps -> true },
            infer: { proj, git -> new ReleaseVersion('1.2.3', null, true) }] as VersionStrategy)
        expect:
        project.version.toString() == '1.0.0'
    }

    def 'infer uses default if it has default selector that passes when selector doesnt'() {
        given:
        Project project = ProjectBuilder.builder().build()
        ReleasePluginExtension extension = new ReleasePluginExtension(project)
        extension.gitReadCommands = GroovyMock(GitReadOnlyCommandUtil)
        extension.gitWriteCommands = GroovyMock(GitWriteCommandsUtil)
        extension.versionStrategy([
            getName: { 'b' },
            selector: { proj, gitOps -> false },
            infer: { proj, git -> new ReleaseVersion('1.0.0', null, true) }] as VersionStrategy)
        extension.defaultVersionStrategy = [
            getName: { 'a' },
            selector: { proj, gitOps -> false },
            defaultSelector: { proj, gitOps -> true },
            infer: { proj, git -> new ReleaseVersion('1.2.3', null, true) }] as DefaultVersionStrategy
        expect:
        project.version.toString() == '1.2.3'
    }

    def 'infer fails if no strategy selected including the default strategy'() {
        given:
        Project project = ProjectBuilder.builder().build()
        ReleasePluginExtension extension = new ReleasePluginExtension(project)
        extension.gitReadCommands = GroovyMock(GitReadOnlyCommandUtil)
        extension.gitWriteCommands = GroovyMock(GitWriteCommandsUtil)
        extension.versionStrategy([
            getName: { 'b' },
            selector: { proj, gitOps -> false },
            infer: { proj, git -> new ReleaseVersion('1.0.0', null, true) }] as VersionStrategy)
        extension.defaultVersionStrategy = [
            getName: { 'a' },
            selector: { proj, gitOps -> false },
            infer: { proj, git -> new ReleaseVersion('1.2.3', null, true) }] as VersionStrategy
        when:
        project.version.toString()
        then:
        thrown(GradleException)
    }

    def 'infer fails if no strategy selected and no default set'() {
        Project project = ProjectBuilder.builder().build()
        ReleasePluginExtension extension = new ReleasePluginExtension(project)
        extension.gitReadCommands = GroovyMock(GitReadOnlyCommandUtil)
        extension.gitWriteCommands = GroovyMock(GitWriteCommandsUtil)
        extension.versionStrategy([
            getName: { 'b' },
            selector: { proj, gitOps -> false },
            infer: { proj, git -> new ReleaseVersion('1.0.0', null, true) }] as VersionStrategy)
        extension.versionStrategy([
            getName: { 'a' },
            selector: { proj, gitOps -> false },
            infer: { proj, git -> new ReleaseVersion('1.2.3', null, true) }] as VersionStrategy)
        when:
        project.version.toString()
        then:
        thrown(GradleException)
    }
}
