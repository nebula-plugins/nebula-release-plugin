package nebula.plugin.release.git.base

import nebula.plugin.release.git.GitBuildService
import nebula.plugin.release.git.command.GitWriteCommandsUtil
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Publishing related tasks should not be cacheable")
abstract class PrepareTask extends DefaultTask {

    @Internal
    abstract Property<GitWriteCommandsUtil> getGitWriteCommandsUtil()
    @Input
    abstract Property<String> getRemote()
    @Input
    @Optional
    abstract Property<ReleaseVersion> getProjectVersion()

    @ServiceReference("gitBuildService")
    abstract Property<GitBuildService> getGitService()

    @TaskAction
    void prepare() {
        if(!projectVersion.isPresent()) {
            throw new GradleException("version should not be set in build file when using nebula-release plugin. Instead use `-Prelease.version` parameter")
        }
        logger.info('Fetching changes from remote: {}', remote.get())
        GitBuildService gitBuildService = gitService.get()
        GitWriteCommandsUtil gitWriteCommandUtil = gitWriteCommandsUtil.get()
        gitWriteCommandUtil.fetch(remote.get())

        boolean currentBranchIsBehindRemote = gitBuildService.isCurrentBranchBehindRemote(remote.get())
        // if branch is tracking another, make sure it's not behind
        if (currentBranchIsBehindRemote) {
            throw new GradleException('Current branch is behind the tracked branch. Cannot release.')
        }
    }
}
