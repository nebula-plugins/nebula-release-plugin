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
            return executeGitCommand(execOperations, "git", "status", "--porcelain")
        }
    }

    static abstract class CurrentBranchNameProvider implements ValueSource<String, ValueSourceParameters.None> {
        @Inject
        abstract ExecOperations getExecOperations()

        String obtain() {
           try {
               return executeGitCommand(execOperations, "git", "rev-parse", "--abbrev-ref", "HEAD")
                       .replaceAll("\n", "").trim()
           } catch (Exception e) {
               return null
           }
        }
    }

    static String executeGitCommand(ExecOperations execOperations, Object... args) {
        ByteArrayOutputStream output = new ByteArrayOutputStream()
        execOperations.exec {
            it.commandLine args
            it.standardOutput = output
        }
        return new String(output.toByteArray(), Charset.defaultCharset())
    }
}
