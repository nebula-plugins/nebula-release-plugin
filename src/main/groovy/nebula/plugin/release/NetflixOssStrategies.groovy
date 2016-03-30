/*
 * Copyright 2014-2015 Netflix, Inc.
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

import static nebula.plugin.release.NetflixOssStrategies.BuildMetadata.DEVELOPMENT_METADATA_STRATEGY
import static nebula.plugin.release.NetflixOssStrategies.PreRelease.BRANCH_NAME_STRATEGY
import static nebula.plugin.release.NetflixOssStrategies.PreRelease.STAGE_APPENDED_STRATEGY

class NetflixOssStrategies {
    private static final scopes = StrategyUtil.one(Strategies.Normal.USE_SCOPE_PROP,
            Strategies.Normal.ENFORCE_GITFLOW_BRANCH_MAJOR_X, Strategies.Normal.ENFORCE_BRANCH_MAJOR_X,
            Strategies.Normal.ENFORCE_GITFLOW_BRANCH_MAJOR_MINOR_X, Strategies.Normal.ENFORCE_BRANCH_MAJOR_MINOR_X,
            Strategies.Normal.USE_NEAREST_ANY, Strategies.Normal.useScope(ChangeScope.MINOR))

    static final SemVerStrategy SNAPSHOT = Strategies.SNAPSHOT.copyWith(normalStrategy: scopes,
        preReleaseStrategy: StrategyUtil.all(BRANCH_NAME_STRATEGY, STAGE_APPENDED_STRATEGY))
    static final SemVerStrategy DEVELOPMENT = Strategies.DEVELOPMENT.copyWith(
            normalStrategy: scopes, buildMetadataStrategy: DEVELOPMENT_METADATA_STRATEGY)
    static final SemVerStrategy PRE_RELEASE = Strategies.PRE_RELEASE.copyWith(normalStrategy: scopes)
    static final SemVerStrategy FINAL = Strategies.FINAL.copyWith(normalStrategy: scopes)

    static final class PreRelease {
        static ReleaseExtension nebulaReleaseExtension

        /**
         * Uses the branch name for the pre-release component of the version (if enabled via the extension).
         */
        static final PartialSemVerStrategy BRANCH_NAME_STRATEGY = StrategyUtil.closure { state ->
            boolean needsBranch = false
            if (nebulaReleaseExtension.branchNameInSnapshot.enabled){
                nebulaReleaseExtension.branchNameInSnapshot.includeBranchPatterns.each {
                    if (state.currentBranch.name =~ it) {
                        needsBranch = true
                    }
                }
                nebulaReleaseExtension.branchNameInSnapshot.excludeBranchPatterns.each {
                    if (state.currentBranch.name =~ it) {
                        needsBranch = false
                    }
                }
            }

            def shortenedBranch = (state.currentBranch.name =~ nebulaReleaseExtension.shortenedBranchPattern)[0][1]
            shortenedBranch = shortenedBranch.replaceAll('_', '.')
            state.copyWith(inferredPreRelease: needsBranch ? shortenedBranch : state.inferredPreRelease)
        }

        /**
         * Like {@link org.ajoberstar.gradle.git.release.opinion.Strategies.PreRelease#STAGE_FIXED} but appends the stage
         * instead of using a fixed value for the pre-release component of the version.
         */
        static final PartialSemVerStrategy STAGE_APPENDED_STRATEGY = StrategyUtil.closure { state ->
            state.copyWith(inferredPreRelease: state.inferredPreRelease ? "${state.inferredPreRelease}-${state.stageFromProp}" : state.stageFromProp)
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
            def shortenedBranch = (state.currentBranch.name =~ nebulaReleaseExtension.shortenedBranchPattern)[0][1]
            shortenedBranch = shortenedBranch.replaceAll('_', '.')
            def metadata = needsBranchMetadata ? "${shortenedBranch}.${state.currentHead.abbreviatedId}" : state.currentHead.abbreviatedId
            state.copyWith(inferredBuildMetadata: metadata)
        }
    }

}
