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

import groovy.transform.CompileDynamic
import groovy.transform.Immutable
import groovy.transform.PackageScope

import com.github.zafarkhaja.semver.Version
import nebula.plugin.release.VersionSanitizerUtil
import nebula.plugin.release.git.GitOps
import nebula.plugin.release.git.base.DefaultVersionStrategy
import nebula.plugin.release.git.base.ReleasePluginExtension
import nebula.plugin.release.git.base.ReleaseVersion
import nebula.plugin.release.git.model.Branch
import nebula.plugin.release.git.model.Commit
import org.gradle.api.GradleException
import org.gradle.api.Project

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Strategy to infer versions that comply with Semantic Versioning.
 * @see PartialSemVerStrategy
 * @see SemVerStrategyState
 * @see <a href="https://github.com/ajoberstar/gradle-git/wiki/SemVer%20Support">Wiki Doc</a>
 */
@CompileDynamic
@Immutable(copyWith=true, knownImmutableClasses=[PartialSemVerStrategy])
final class SemVerStrategy implements DefaultVersionStrategy {
    private static final Logger logger = LoggerFactory.getLogger(SemVerStrategy)
    static final String SCOPE_PROP = 'release.scope'
    static final String STAGE_PROP = 'release.stage'

    /**
     * The name of the strategy.
     */
    String name

    /**
     * The stages supported by this strategy.
     */
    SortedSet<String> stages

    /**
     * Whether or not this strategy can be used if the repo has uncommited changes.
     */
    boolean allowDirtyRepo

    /**
     * The strategy used to infer the normal component of the version. There is no enforcement that
     * this strategy only modify that part of the state.
     */
    PartialSemVerStrategy normalStrategy

    /**
     * The strategy used to infer the pre-release component of the version. There is no enforcement that
     * this strategy only modify that part of the state.
     */
    PartialSemVerStrategy preReleaseStrategy

    /**
     * The strategy used to infer the build metadata component of the version. There is no enforcement that
     * this strategy only modify that part of the state.
     */
    PartialSemVerStrategy buildMetadataStrategy

    /**
     * Whether or not to create tags for versions inferred by this strategy.
     */
    boolean createTag

    /**
     * Whether or not to enforce that versions inferred by this strategy are of higher precedence
     * than the nearest any.
     */
    boolean enforcePrecedence

    @Override
    boolean defaultSelector(Project project, GitOps gitOps) {
        String stage = getPropertyOrNull(project, STAGE_PROP)
        if (stage != null && !stages.contains(stage)) {
            logger.info('Skipping {} default strategy because stage ({}) is not one of: {}', name, stage, stages)
            return false
        } else if (!allowDirtyRepo && !gitOps.isCleanStatus()) {
            logger.info('Skipping {} default strategy because repo is dirty.', name)
            return false
        } else {
            String status = gitOps.isCleanStatus() ? 'clean' : 'dirty'
            logger.info('Using {} default strategy because repo is {} and no stage defined', name, status)
            return true
        }
    }


    /**
     * Determines whether this strategy should be used to infer the version.
     * <ul>
     * <li>Return {@code false}, if the {@code release.stage} is not one listed in the {@code stages} property.</li>
     * <li>Return {@code false}, if the repository has uncommitted changes and {@code allowDirtyRepo} is {@code false}.</li>
     * <li>Return {@code true}, otherwise.</li>
     * </ul>
     */
    @Override
    boolean selector(Project project, GitOps gitOps) {
        String stage = getPropertyOrNull(project, STAGE_PROP)
        if (stage == null || !stages.contains(stage)) {
            logger.info('Skipping {} strategy because stage ({}) is not one of: {}', name, stage, stages)
            return false
        } else if (!allowDirtyRepo && !gitOps.isCleanStatus()) {
            logger.info('Skipping {} strategy because repo is dirty.', name)
            return false
        } else {
            logger.info('Using {} strategy because repo is not dirty (or allowed to be dirty) and stage ({}) is one of: {}', name, stage, stages)
            return true
        }
    }

    /**
     * Infers the version to use for this build. Uses the normal, pre-release, and build metadata
     * strategies in order to infer the version. If the {@code release.stage} is not set, uses the
     * first value in the {@code stages} set (i.e. the one with the lowest precedence). After inferring
     * the version precedence will be enforced, if required by this strategy.
     */
    @CompileDynamic
    @Override
    ReleaseVersion infer(Project project, GitOps gitOps) {
        def tagStrategy = project.extensions.getByType(ReleasePluginExtension).tagStrategy
        return doInfer(project, gitOps, new NearestVersionLocator(gitOps, tagStrategy))
    }

    @PackageScope
    ReleaseVersion doInfer(Project project, GitOps gitOps, NearestVersionLocator locator) {
        ChangeScope scope = getPropertyOrNull(project, SCOPE_PROP).with { scope ->
            scope == null ? null : ChangeScope.valueOf(scope.toUpperCase())
        }
        String stage = getPropertyOrNull(project, STAGE_PROP) ?: stages.first()
        if (!stages.contains(stage)) {
            throw new GradleException("Stage ${stage} is not one of ${stages} allowed for strategy ${name}.")
        }
        logger.info('Beginning version inference using {} strategy and input scope ({}) and stage ({})', name, scope, stage)

        NearestVersion nearestVersion = locator.locate()
        logger.debug('Located nearest version: {}', nearestVersion)

        String currentHead = gitOps.head()
        SemVerStrategyState state = new SemVerStrategyState(
                scopeFromProp: scope,
                stageFromProp: stage,
                currentHead: new Commit(id: currentHead, abbreviatedId: currentHead.take(7)),
                currentBranch: new Branch(fullName: gitOps.currentBranch()),
                repoDirty: !gitOps.cleanStatus,
                nearestVersion: nearestVersion
        )

        Version version = StrategyUtil.all(
                normalStrategy, preReleaseStrategy, buildMetadataStrategy).infer(state).toVersion()


        String versionAsString = version.toString()
        if(VersionSanitizerUtil.hasSanitizeFlag(project)) {
            versionAsString = VersionSanitizerUtil.sanitize(version.toString())
        }

        logger.warn('Inferred project: {}, version: {}', project.name, versionAsString)

        if (enforcePrecedence && version < nearestVersion.any) {
            throw new GradleException("Based on previous tags in this branch the nearest version is ${nearestVersion.any} You're attempting to release ${version} based on the tag recently pushed. Please look at https://github.com/nebula-plugins/nebula-release-plugin/wiki/Error-Messages-Explained#orggradleapigradleexception-inferred-version-cannot-be-lower-than-nearest-required-by-selected-strategy")
        }


        return new ReleaseVersion(versionAsString, nearestVersion.normal.toString(), createTag)
    }

    private static String getPropertyOrNull(Project project, String name) {
        return project.hasProperty(name) ? project.property(name) : null
    }
}
