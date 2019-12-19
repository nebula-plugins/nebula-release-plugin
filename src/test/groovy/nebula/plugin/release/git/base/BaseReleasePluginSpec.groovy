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

import org.ajoberstar.grgit.Branch
import org.ajoberstar.grgit.BranchStatus
import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.service.BranchService
import org.ajoberstar.grgit.service.TagService
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Ignore
import spock.lang.Specification

class BaseReleasePluginSpec extends Specification {
    Project project = ProjectBuilder.builder().build()

    def setup() {
        project.plugins.apply(BaseReleasePlugin)
    }

    def 'prepare task succeeds if branch is up to date'() {
        given:
        Grgit repo = GroovyMock()
        BranchService branch = GroovyMock()
        branch.current() >> new Branch(fullName: 'refs/heads/master')
        branch.status([branch: 'refs/heads/master']) >> new BranchStatus(behindCount: 0)
        ReleasePluginExtension releaseExtension = new ReleasePluginExtension(project)
        releaseExtension.grgit = repo

        when:
        BaseReleasePlugin.prepare(releaseExtension)

        then:
        notThrown(GradleException)
        1 * repo.branch >> branch
        1 * repo.fetch([remote: 'origin'])

    }

    def 'prepare task fails if branch is behind'() {
        given:
        Grgit repo = GroovyMock()
        BranchService branch = GroovyMock()
        ReleasePluginExtension releaseExtension = new ReleasePluginExtension(project)
        releaseExtension.grgit = repo

        when:
        BaseReleasePlugin.prepare(releaseExtension)

        then:
        thrown(GradleException)
        1 * repo.fetch([remote: 'origin'])
        _ * repo.branch >> branch
        1 * branch.status([name: 'refs/heads/master']) >> new BranchStatus(behindCount: 2)
        1 * branch.current() >> new Branch(fullName: 'refs/heads/master', trackingBranch: new Branch(fullName: 'refs/remotes/origin/master'))

    }

    def 'release task pushes branch and tag if created'() {
        given:
        VersionStrategy strategy = [
                getName: { 'a' },
                selector: {proj, repo2 -> true },
                infer: {proj, repo2 -> new ReleaseVersion('1.2.3', null, true)}] as VersionStrategy
        Grgit repo = GroovyMock()
        BranchService branch = GroovyMock()
        repo.branch >> branch
        TagService tag = GroovyMock()

        ReleasePluginExtension releaseExtension = new ReleasePluginExtension(project)
        releaseExtension.grgit = repo
        releaseExtension.versionStrategy(strategy)

        when:
        BaseReleasePlugin.release(project, project.ext, releaseExtension)

        then:
        1 * repo.push([remote: 'origin', refsOrSpecs: ['v1.2.3']])
        _ * repo.branch >> branch
        _ * branch.current >> new Branch(fullName: 'refs/heads/master', trackingBranch: new Branch(fullName: 'refs/remotes/origin/master'))
        1 * repo.tag >> tag
    }
}
