package nebula.plugin.release.git

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

    @Inject
    GitBuildService(ProviderFactory providers) {
        File gitRootDir = getParameters().gitRootDir.get().asFile
        Provider isGitRepoProvider = providers.of(IsGitRepo.class) {
            it.parameters.rootDir.set(gitRootDir)
        }
        isGitRepo = Boolean.valueOf(isGitRepoProvider.getOrElse("false"))
    }

    boolean isGitRepo() {
        return isGitRepo
    }
}
