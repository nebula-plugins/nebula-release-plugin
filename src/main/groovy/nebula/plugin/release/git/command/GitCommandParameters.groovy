package nebula.plugin.release.git.command

import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSourceParameters

interface GitCommandParameters extends ValueSourceParameters {
    Property<File> getRootDir()
    Property<String> getGitConfigScope()
    Property<String> getGitConfigKey()
    Property<String> getGitConfigValue()
    Property<String> getCommit()
    Property<String> getTag()
}
