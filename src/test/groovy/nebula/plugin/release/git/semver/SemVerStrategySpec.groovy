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
package nebula.plugin.release.git.semver

import com.github.zafarkhaja.semver.Version
import nebula.plugin.release.git.GitOps
import nebula.plugin.release.git.base.ReleaseVersion
import nebula.plugin.release.git.model.Branch
import org.gradle.api.GradleException
import org.gradle.api.Project
import spock.lang.Specification

class SemVerStrategySpec extends Specification {
    Project project = GroovyMock()
    GitOps gitOps = GroovyMock()

    def 'selector returns false if stage is not set to valid value'() {
        given:
        def strategy = new SemVerStrategy(stages: ['one', 'two'] as SortedSet)
        mockStage(stageProp)
        expect:
        !strategy.selector(project, gitOps)
        where:
        stageProp << [null, 'test']
    }


    def 'selector returns false if repo is dirty and not allowed to be'() {
        given:
        def strategy = new SemVerStrategy(stages: ['one'] as SortedSet, allowDirtyRepo: false)
        mockStage('one')
        mockRepoClean(false)
        expect:
        !strategy.selector(project, gitOps)
    }

    def 'selector returns true if repo is dirty and allowed and other criteria met'() {
        given:
        def strategy = new SemVerStrategy(stages: ['one'] as SortedSet, allowDirtyRepo: true)
        mockStage('one')
        mockRepoClean(false)
        mockCurrentBranch()
        expect:
        strategy.selector(project, gitOps)
    }

    def 'selector returns true if all criteria met'() {
        given:
        def strategy = new SemVerStrategy(stages: ['one', 'and'] as SortedSet, allowDirtyRepo: false)
        mockStage('one')
        mockRepoClean(true)
        mockCurrentBranch()
        expect:
        strategy.selector(project, gitOps)
    }

    def 'default selector returns false if stage is defined but not set to valid value'() {
        given:
        def strategy = new SemVerStrategy(stages: ['one', 'two'] as SortedSet)
        mockStage('test')
        expect:
        !strategy.defaultSelector(project, gitOps)
    }

    def 'default selector returns true if stage is not defined'() {
        given:
        def strategy = new SemVerStrategy(stages: ['one', 'two'] as SortedSet)
        mockStage(null)
        mockRepoClean(true)
        expect:
        strategy.defaultSelector(project, gitOps)
    }

    def 'default selector returns false if repo is dirty and not allowed to be'() {
        given:
        def strategy = new SemVerStrategy(stages: ['one'] as SortedSet, allowDirtyRepo: false)
        mockStage(stageProp)
        mockRepoClean(false)
        expect:
        !strategy.defaultSelector(project, gitOps)
        where:
        stageProp << [null, 'one']
    }

    def 'default  selector returns true if repo is dirty and allowed and other criteria met'() {
        given:
        def strategy = new SemVerStrategy(stages: ['one'] as SortedSet, allowDirtyRepo: true)
        mockStage('one')
        mockRepoClean(false)
        mockCurrentBranch()
        expect:
        strategy.defaultSelector(project, gitOps)
    }

    def 'default selector returns true if all criteria met'() {
        given:
        def strategy = new SemVerStrategy(stages: ['one', 'and'] as SortedSet, allowDirtyRepo: false)
        mockStage('one')
        mockRepoClean(true)
        mockCurrentBranch()
        expect:
        strategy.defaultSelector(project, gitOps)
    }

    def 'infer returns correct version'() {
        given:
        mockScope(scope)
        mockStage(stage)
        mockRepoClean(false)
        mockCurrentBranch()
        def nearest = new NearestVersion(
            normal: Version.valueOf('1.2.2'),
            any: Version.valueOf(nearestAny))
        def locator = mockLocator(nearest)
        def strategy = mockStrategy(scope, stage, nearest, createTag, enforcePrecedence)
        expect:
        strategy.doInfer(project, gitOps, locator) == new ReleaseVersion('1.2.3-beta.1+abc123', '1.2.2', createTag)
        where:
        scope   | stage | nearestAny | createTag | enforcePrecedence
        'patch' | 'one' | '1.2.3'    | true      | false
        'minor' | 'one' | '1.2.2'    | true      | true
        'major' | 'one' | '1.2.2'    | false     | true
        'patch' | null  | '1.2.2'    | false     | true
    }

    def 'infer fails if stage is not listed in stages property'() {
        given:
        mockStage('other')
        def strategy = new SemVerStrategy(stages: ['one'] as SortedSet)
        when:
        strategy.doInfer(project, gitOps, null)
        then:
        thrown(GradleException)
    }

    def 'infer fails if precedence enforced and violated'() {
        given:
        mockRepoClean(false)
        mockCurrentBranch()
        def nearest = new NearestVersion(any: Version.valueOf('1.2.3'))
        def locator = mockLocator(nearest)
        def strategy = mockStrategy(null, 'and', nearest, false, true)
        when:
        strategy.doInfer(project, gitOps, locator)
        then:
        thrown(GradleException)
    }

    private def mockScope(String scopeProp) {
        (0..1) * project.hasProperty('release.scope') >> (scopeProp as boolean)
        (0..1) * project.property('release.scope') >> scopeProp
    }

    private def mockStage(String stageProp) {
        (0..1) * project.hasProperty('release.stage') >> (stageProp as boolean)
        (0..1) * project.property('release.stage') >> stageProp
    }

    private def mockRepoClean(boolean isClean) {
        (0..2) * gitOps.isCleanStatus() >> isClean
    }

    private def mockCurrentBranch() {
        (0..1) * gitOps.currentBranch() >> 'refs/heads/master'
        (0..1) * gitOps.head() >> 'refs/heads/master'
    }

    private def mockLocator(NearestVersion nearest) {
        NearestVersionLocator locator = Mock()
        locator.locate() >> nearest
        return locator
    }

    private def mockStrategy(String scope, String stage, NearestVersion nearest, boolean createTag, boolean enforcePrecedence) {
        PartialSemVerStrategy normal = Mock()
        PartialSemVerStrategy preRelease = Mock()
        PartialSemVerStrategy buildMetadata = Mock()
        SemVerStrategyState initial = new SemVerStrategyState([
            scopeFromProp: scope?.toUpperCase(),
            stageFromProp: stage ?: 'and',
            currentHead: null,
            currentBranch: new Branch(fullName: 'refs/heads/master'),
            repoDirty: true,
            nearestVersion: nearest])
        SemVerStrategyState afterNormal = initial.copyWith(inferredNormal: '1.2.3')
        SemVerStrategyState afterPreRelease = afterNormal.copyWith(inferredPreRelease: 'beta.1')
        SemVerStrategyState afterBuildMetadata = afterPreRelease.copyWith(inferredBuildMetadata: 'abc123')

        1 * normal.infer(_) >> afterNormal
        1 * preRelease.infer(_) >> afterPreRelease
        1 * buildMetadata.infer(_) >> afterBuildMetadata


        return new SemVerStrategy(
            stages: ['one', 'and'] as SortedSet,
            normalStrategy: normal,
            preReleaseStrategy: preRelease,
            buildMetadataStrategy: buildMetadata,
            createTag: createTag,
            enforcePrecedence: enforcePrecedence
        )
    }
}
