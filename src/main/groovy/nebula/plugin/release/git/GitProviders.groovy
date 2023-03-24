package nebula.plugin.release.git

import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations

import javax.inject.Inject
import java.nio.charset.Charset

class GitProviders {
    static abstract class GitStatusProvider implements ValueSource<String, ValueSourceParameters.None> {
        @Inject
        abstract ExecOperations getExecOperations()

        String obtain() {
            ByteArrayOutputStream output = new ByteArrayOutputStream()
            execOperations.exec {
                it.commandLine "git", "status", "--porcelain"
                it.standardOutput = output
            }
            return new String(output.toByteArray(), Charset.defaultCharset())
        }
    }
}
