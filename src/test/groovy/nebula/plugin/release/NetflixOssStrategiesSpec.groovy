package nebula.plugin.release

import org.ajoberstar.gradle.git.release.opinion.Strategies
import org.ajoberstar.gradle.git.release.semver.SemVerStrategyState
import org.ajoberstar.grgit.Branch
import org.ajoberstar.grgit.Commit
import spock.lang.Specification

import static org.ajoberstar.gradle.git.release.semver.StrategyUtil.all

class NetflixOssStrategiesSpec extends Specification {
    def setup() {
        NetflixOssStrategies.BuildMetadata.nebulaReleaseExtension = new ReleaseExtension()
    }

    def 'BRANCH_METADATA_STRATEGY adds info extracted by shortenedBranchPattern'() {
        given:
        def initialState = new SemVerStrategyState(
                currentBranch: new Branch(fullName: 'refs/heads/feature/some-cool-feat')
        )

        expect:
        NetflixOssStrategies.BuildMetadata.BRANCH_METADATA_STRATEGY.infer(initialState) ==
                initialState.copyWith(inferredBuildMetadata: 'some.cool.feat')
    }

    def 'BRANCH_METADATA_STRATEGY composes with COMMIT_ABBREVIATED_ID'() {
        given:
        def initialState = new SemVerStrategyState(
                currentBranch: new Branch(fullName: 'refs/heads/feature/some-cool-feat'),
                currentHead: new Commit(id: 'ac6a4b15e1a9b02970937f327cc6890226d80210')
        )
        def strategy = all(
                Strategies.BuildMetadata.COMMIT_ABBREVIATED_ID,
                NetflixOssStrategies.BuildMetadata.BRANCH_METADATA_STRATEGY
        )

        expect:
        strategy.infer(initialState) == initialState.copyWith(
                inferredBuildMetadata: 'some.cool.feat.ac6a4b1'
        )
    }

    def 'BRANCH_METADATA_STRATEGY does nothing if branch excluded by releaseBranchPatterns'() {
        given:
        def initialState = stateWithBranch('master')

        expect:
        NetflixOssStrategies.BuildMetadata.BRANCH_METADATA_STRATEGY.
                infer(initialState) == initialState
    }

    def 'BRANCH_METADATA_STRATEGY does nothing if branch does not match shortenedBranchPattern'() {
        given:
        NetflixOssStrategies.BuildMetadata.nebulaReleaseExtension.
                shortenedBranchPattern = /(?:(?:bugfix|feature|hotfix|release)(?:-|\/))?(\d+)/
        def initialState = stateWithBranch('feature/some-cool-feat')

        expect:
        NetflixOssStrategies.BuildMetadata.BRANCH_METADATA_STRATEGY.
                infer(initialState) == initialState
    }

    private static SemVerStrategyState stateWithBranch(String name) {
        new SemVerStrategyState(
                currentBranch: new Branch(fullName: "refs/heads/${name}")
        )
    }
}
