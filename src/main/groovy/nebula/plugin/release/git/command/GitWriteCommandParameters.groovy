package nebula.plugin.release.git.command

import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSourceParameters

interface GitWriteCommandParameters extends ValueSourceParameters {
    Property<File> getRootDir()
    Property<String> getRemote()
    Property<String> getTag()
    Property<String> getTagMessage()
}
