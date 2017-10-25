/*
 * Copyright 2014-2017 Netflix, Inc.
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

import org.ajoberstar.gradle.git.release.opinion.Strategies
import org.ajoberstar.gradle.git.release.semver.ChangeScope
import org.ajoberstar.gradle.git.release.semver.PartialSemVerStrategy
import org.ajoberstar.gradle.git.release.semver.SemVerStrategy
import org.ajoberstar.gradle.git.release.semver.StrategyUtil
import org.gradle.api.GradleException
import org.gradle.api.Project

import java.util.regex.Pattern

import static org.ajoberstar.gradle.git.release.semver.StrategyUtil.incrementNormalFromScope
import static org.ajoberstar.gradle.git.release.semver.StrategyUtil.parseIntOrZero

class NetflixOssStrategies {
    static final PartialSemVerStrategy TRAVIS_BRANCH_MAJOR_X = fromTravisPropertyPattern(~/^(\d+)\.x$/)
    static final PartialSemVerStrategy TRAVIS_BRANCH_MAJOR_MINOR_X = fromTravisPropertyPattern(~/^(\d+)\.(\d+)\.x$/)
    private static final scopes = StrategyUtil.one(Strategies.Normal.USE_SCOPE_PROP,
            TRAVIS_BRANCH_MAJOR_X, TRAVIS_BRANCH_MAJOR_MINOR_X,
            Strategies.Normal.ENFORCE_GITFLOW_BRANCH_MAJOR_X, Strategies.Normal.ENFORCE_BRANCH_MAJOR_X,
            Strategies.Normal.ENFORCE_GITFLOW_BRANCH_MAJOR_MINOR_X, Strategies.Normal.ENFORCE_BRANCH_MAJOR_MINOR_X,
            Strategies.Normal.USE_NEAREST_ANY, Strategies.Normal.useScope(ChangeScope.MINOR))

    static final SemVerStrategy SNAPSHOT = Strategies.SNAPSHOT.copyWith(normalStrategy: scopes)
    static final SemVerStrategy DEVELOPMENT = Strategies.DEVELOPMENT.copyWith(
            normalStrategy: scopes,
            buildMetadataStrategy: NetflixOssStrategies.BuildMetadata.DEVELOPMENT_METADATA_STRATEGY)
    static final SemVerStrategy PRE_RELEASE = Strategies.PRE_RELEASE.copyWith(normalStrategy: scopes)
    static final SemVerStrategy FINAL = Strategies.FINAL.copyWith(normalStrategy: scopes)

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

    static Project project

    static final String TRAVIS_BRANCH_PROP = 'release.travisBranch'

    static PartialSemVerStrategy fromTravisPropertyPattern(Pattern pattern) {
        return StrategyUtil.closure { state ->
            println state
            if (project.hasProperty(TRAVIS_BRANCH_PROP)) {
                def branch = project.property(TRAVIS_BRANCH_PROP)
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
}
