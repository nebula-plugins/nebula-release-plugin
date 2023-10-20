package nebula.plugin.release.git.base

import nebula.plugin.release.git.command.GitWriteCommandsUtil
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Publishing related tasks should not be cacheable")
abstract class ReleaseTask extends DefaultTask {

    @Internal
    abstract Property<GitWriteCommandsUtil> getGitWriteCommandsUtil()
    @Internal
    abstract Property<TagStrategy> getTagStrategy()
    @Input
    abstract Property<ReleaseVersion> getProjectVersion()
    @Input
    abstract Property<String> getRemote()

    @TaskAction
    void release() {
        GitWriteCommandsUtil gitCommands = gitWriteCommandsUtil.get()
        String tagName = tagStrategy.get().maybeCreateTag(gitCommands, projectVersion.get())
        if (tagName) {
            logger.warn('Pushing changes in {} to {}', tagName, remote.get())
            gitCommands.pushTag(remote.get(), tagName)
        } else {
            logger.warn('Nothing to push.')
        }
    }
}
