package nebula.plugin.release.git.command

import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSourceParameters

interface GitCommandParameters extends ValueSourceParameters {
    Property<File> getRootDir()
    Property<String> getTagForSearch()
}
