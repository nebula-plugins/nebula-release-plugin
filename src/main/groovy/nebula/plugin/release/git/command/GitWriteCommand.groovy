package nebula.plugin.release.git.command

import groovy.transform.CompileDynamic
import org.gradle.api.GradleException
import org.gradle.api.provider.ValueSource
import org.gradle.process.ExecOperations

import javax.inject.Inject
import java.nio.charset.Charset

abstract class GitWriteCommand implements ValueSource<String, GitWriteCommandParameters> {
    @Inject
    abstract ExecOperations getExecOperations()

    @CompileDynamic
    String executeGitCommand(Object... args) {
        File rootDir = parameters.rootDir.get()
        ByteArrayOutputStream output = new ByteArrayOutputStream()
        ByteArrayOutputStream error = new ByteArrayOutputStream()
        List<String> commandLineArgs = ["git", "--git-dir=${rootDir.absolutePath}/.git".toString(), "--work-tree=${rootDir.absolutePath}".toString()]
        commandLineArgs.addAll(args)
        execOperations.exec {
            it.ignoreExitValue = true
            it.setCommandLine(commandLineArgs)
            it.standardOutput = output
            it.errorOutput = error
        }
        def errorMsg = new String(error.toByteArray(), Charset.defaultCharset())
        if (errorMsg) {
            throw new GradleException(errorMsg)
        }
        return new String(output.toByteArray(), Charset.defaultCharset())
    }
}

/**
 * Pushes a tag to a remote
 */
abstract class PushTag extends GitWriteCommand {
    @Override
    String obtain() {
        try {
            return executeGitCommand("push", parameters.remote.get(), parameters.tag.get())
        } catch (Exception e) {
            throw e
        }
    }
}

/**
 * Creates a tag with a given message
 */
abstract class CreateTag extends GitWriteCommand {
    @Override
    String obtain() {
        try {
            return executeGitCommand( "tag", "-a", parameters.tag.get(), "-m", parameters.tagMessage.get())
        } catch (Exception e) {
            throw e
        }
    }
}


/**
 * Creates a tag with a given message
 */
abstract class FetchChanges extends GitWriteCommand {
    @Override
    String obtain() {
        try {
            return executeGitCommand( "fetch", parameters.remote.get())
        } catch (Exception e) {
            return null
        }
    }
}
