package nebula.plugin.release.git

import nebula.plugin.release.git.command.CurrentBranch
import nebula.plugin.release.git.command.IsGitRepo
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

import javax.inject.Inject

// TODO: migrate GitReadOnlyCommandUtil funcionality to build service when possible
abstract class GitBuildService implements BuildService<GitBuildService.Params> {
    interface Params extends BuildServiceParameters {
        DirectoryProperty getGitRootDir()
    }

    private final boolean isGitRepo
    private final String currentBranch
    private final File gitRootDir
    private final ProviderFactory providerFactory

    @Inject
    GitBuildService(ProviderFactory providers) {
        this.gitRootDir = getParameters().gitRootDir.get().asFile
        this.providerFactory = providers
        this.isGitRepo = detectIsGitRepo()
        this.currentBranch = detectCurrentBranch()
    }

    boolean isGitRepo() {
        return isGitRepo
    }

    String getCurrentBranch() {
        return currentBranch
    }

    private Boolean detectIsGitRepo() {
        try {
            Provider isGitRepoProvider = providerFactory.of(IsGitRepo.class) {
                it.parameters.rootDir.set(gitRootDir)
            }
            return Boolean.valueOf(isGitRepoProvider.get().toString())
        } catch (Exception e) {
            return false
        }
    }

    private String detectCurrentBranch() {
        try {
            Provider currentBranchProvider = providerFactory.of(CurrentBranch.class) {
                it.parameters.rootDir.set(gitRootDir)
            }
            return currentBranchProvider.get().toString().replaceAll("\n", "").trim()
        } catch (Exception e) {
            return null
        }

    }
}
