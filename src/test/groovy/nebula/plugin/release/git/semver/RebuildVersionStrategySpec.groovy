/*
 * Copyright 2012-2023 the original author or authors.
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
package nebula.plugin.release.git.semver


import nebula.plugin.release.git.base.BaseReleasePlugin
import nebula.plugin.release.git.base.ReleaseVersion
import nebula.plugin.release.git.command.GitReadOnlyCommandUtil
import nebula.plugin.release.git.model.TagRef
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class RebuildVersionStrategySpec extends Specification {
    RebuildVersionStrategy strategy = new RebuildVersionStrategy()
    GitReadOnlyCommandUtil gitReadOnlyCommandUtil = GroovyMock()

    def getProject(Map properties) {
        Project p = ProjectBuilder.builder().withName("testproject").build()
        p.apply plugin: BaseReleasePlugin
        properties.each { k, v ->
            p.ext[k] = v
        }
        p
    }

    def 'selector returns false if repo is dirty'() {
        given:
        mockClean(false)
        Project project = getProject([:])
        mockTagsAtHead('v1.0.0')
        expect:
        !strategy.selector(project, gitReadOnlyCommandUtil)
    }

    def 'selector returns false if any release properties are set'() {
        given:
        mockClean(true)
        Project project = getProject('release.scope': 'value')
        mockTagsAtHead('v1.0.0')
        expect:
        !strategy.selector(project, gitReadOnlyCommandUtil)
    }

    def 'selector returns false if no version tag at HEAD'() {
        given:
        mockClean(true)
        Project project = getProject([:])
        mockTagsAtHead('non-version-tag')
        expect:
        !strategy.selector(project, gitReadOnlyCommandUtil)
    }

    def 'selector returns true if rebuild is attempted'() {
        given:
        mockClean(true)
        Project project = getProject([:])
        mockTagsAtHead('v0.1.1', 'v1.0.0', '0.19.1')
        expect:
        strategy.selector(project, gitReadOnlyCommandUtil)
    }

    def 'infer returns HEAD version is inferred and previous with create tag false'() {
        given:
        mockClean(true)
        Project project = getProject([:])
        mockTagsAtHead('v0.1.1', 'v1.0.0', '0.19.1')
        expect:
        strategy.infer(project, gitReadOnlyCommandUtil) == new ReleaseVersion('1.0.0', '1.0.0', false)
    }

    private void mockTagsAtHead(String... tagNames) {
        gitReadOnlyCommandUtil.headTags() >>  tagNames.collect { new TagRef("refs/tags/${it}") }
    }


    private void mockClean(boolean clean) {
        gitReadOnlyCommandUtil.isCleanStatus() >> clean
    }
}
