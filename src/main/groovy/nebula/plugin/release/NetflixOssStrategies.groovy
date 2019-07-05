/*
 * Copyright 2014-2019 Netflix, Inc.
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
package nebula.plugin.release

import nebula.plugin.release.ReleaseExtension
import nebula.plugin.release.git.opinion.Strategies
import nebula.plugin.release.git.semver.ChangeScope
import nebula.plugin.release.git.semver.PartialSemVerStrategy
import nebula.plugin.release.git.semver.SemVerStrategy
import org.gradle.api.GradleException
import org.gradle.api.Project

import java.util.regex.Pattern

import static nebula.plugin.release.git.semver.StrategyUtil.*

class NetflixOssStrategies {

    private static final String TRAVIS_BRANCH_PROP = 'release.travisBranch'

    static SemVerStrategy SNAPSHOT(Project project) {
        Strategies.SNAPSHOT.copyWith(normalStrategy: getScopes(project))
    }

    static SemVerStrategy DEVELOPMENT(Project project) {
        Strategies.DEVELOPMENT.copyWith(
                normalStrategy: getScopes(project),
                buildMetadataStrategy: BuildMetadata.DEVELOPMENT_METADATA_STRATEGY)
    }

    static SemVerStrategy IMMUTABLE_SNAPSHOT(Project project) {
        Strategies.IMMUTABLE_SNAPSHOT.copyWith(
                normalStrategy: getScopes(project),
                buildMetadataStrategy: BuildMetadata.DEVELOPMENT_METADATA_STRATEGY)
    }

    static SemVerStrategy PRE_RELEASE(Project project) {
        Strategies.PRE_RELEASE.copyWith(normalStrategy: getScopes(project))
    }

    static SemVerStrategy FINAL(Project project) {
        Strategies.FINAL.copyWith(normalStrategy: getScopes(project))
    }

    private static getScopes(Project project) {
        Object travisReleaseBranch = (project.hasProperty(TRAVIS_BRANCH_PROP)) ? project.property(TRAVIS_BRANCH_PROP) : null
        final PartialSemVerStrategy TRAVIS_BRANCH_MAJOR_X = fromTravisPropertyPattern(travisReleaseBranch, ~/^(\d+)\.x$/)
        final PartialSemVerStrategy TRAVIS_BRANCH_MAJOR_MINOR_X = fromTravisPropertyPattern(travisReleaseBranch, ~/^(\d+)\.(\d+)\.x$/)
        final PartialSemVerStrategy NEAREST_HIGHER_ANY = nearestHigherAny()
        one(Strategies.Normal.USE_SCOPE_PROP,
                TRAVIS_BRANCH_MAJOR_X, TRAVIS_BRANCH_MAJOR_MINOR_X,
                Strategies.Normal.ENFORCE_GITFLOW_BRANCH_MAJOR_X, Strategies.Normal.ENFORCE_BRANCH_MAJOR_X,
                Strategies.Normal.ENFORCE_GITFLOW_BRANCH_MAJOR_MINOR_X, Strategies.Normal.ENFORCE_BRANCH_MAJOR_MINOR_X,
                NEAREST_HIGHER_ANY, Strategies.Normal.useScope(ChangeScope.MINOR))
    }

    private static PartialSemVerStrategy fromTravisPropertyPattern(Object travisReleaseBranch, Pattern pattern) {
        return closure { state ->
            if (travisReleaseBranch) {
                def branch = travisReleaseBranch
                def m = branch =~ pattern
                if (m) {
                    def major = m.groupCount() >= 1 ? parseIntOrZero(m[0][1]) : -1
                    def minor = m.groupCount() >= 2 ? parseIntOrZero(m[0][2]) : -1

                    def normal = state.nearestVersion.normal
                    def majorDiff = major - normal.majorVersion
                    def minorDiff = minor - normal.minorVersion

                    if (majorDiff == 1 && minor <= 0) {
                        // major is off by one and minor is either 0 or not in the branch name
                        return incrementNormalFromScope(state, ChangeScope.MAJOR)
                    } else if (minorDiff == 1 && minor > 0) {
                        // minor is off by one and specified in the branch name
                        return incrementNormalFromScope(state, ChangeScope.MINOR)
                    } else if (majorDiff == 0 && minorDiff == 0 && minor >= 0) {
                        // major and minor match, both are specified in branch name
                        return incrementNormalFromScope(state, ChangeScope.PATCH)
                    } else if (majorDiff == 0 && minor < 0) {
                        // only major specified in branch name and already matches
                        return state
                    } else {
                        throw new GradleException("Invalid branch (${state.currentBranch.name}) for nearest normal (${normal}).")
                    }
                }
            }

            return state
        }
    }

    /**
     * If the nearest any is higher from the nearest normal, sets the
     * normal component to the nearest any's normal component. Otherwise
     * do nothing.
     *
     * <p>
     * For example, if the nearest any is {@code 1.2.3-alpha.1} and the
     * nearest normal is {@code 1.2.2}, this will infer the normal
     * component as {@code 1.2.3}.
     * </p>
     */
    static private PartialSemVerStrategy nearestHigherAny() {
        return closure { state ->
            def nearest = state.nearestVersion
            if (nearest.any.lessThanOrEqualTo(nearest.normal)) {
                return state
            } else {
                return state.copyWith(inferredNormal: nearest.any.normalVersion)
            }
        }
    }



    static final class BuildMetadata {
        static ReleaseExtension nebulaReleaseExtension

        static final PartialSemVerStrategy DEVELOPMENT_METADATA_STRATEGY = { state ->
            boolean needsBranchMetadata = true
            nebulaReleaseExtension.releaseBranchPatterns.each {
                if (state.currentBranch.name =~ it) {
                    needsBranchMetadata = false
                }
            }
            String shortenedBranch = (state.currentBranch.name =~ nebulaReleaseExtension.shortenedBranchPattern)[0][1]
            shortenedBranch = shortenedBranch.replaceAll(/[_\/-]/, '.')
            def metadata = needsBranchMetadata ? "${shortenedBranch}.${state.currentHead.abbreviatedId}" : state.currentHead.abbreviatedId
            state.copyWith(inferredBuildMetadata: metadata)
        }
    }

}
