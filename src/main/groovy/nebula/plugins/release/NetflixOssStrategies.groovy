package nebula.plugins.release

import org.ajoberstar.gradle.git.release.opinion.Strategies
import org.ajoberstar.gradle.git.release.semver.ChangeScope
import org.ajoberstar.gradle.git.release.semver.PartialSemVerStrategy
import org.ajoberstar.gradle.git.release.semver.SemVerStrategy
import org.ajoberstar.gradle.git.release.semver.StrategyUtil

class NetflixOssStrategies {
    private static final scopes = StrategyUtil.one(Strategies.Normal.USE_SCOPE_PROP,
            Strategies.Normal.ENFORCE_GITFLOW_BRANCH_MAJOR_X, Strategies.Normal.ENFORCE_BRANCH_MAJOR_X,
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
            def shortenedBranch = (state.currentBranch.name =~ nebulaReleaseExtension.shortenedBranchPattern)[0][1]
            def metadata = needsBranchMetadata ? "${shortenedBranch}.${state.currentHead.abbreviatedId}" : state.currentHead.abbreviatedId
            state.copyWith(inferredBuildMetadata: metadata)
        }
    }
}
