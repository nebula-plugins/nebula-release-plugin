package nebula.plugin.release.git.command

import groovy.transform.CompileDynamic
import org.gradle.api.GradleException
import org.gradle.process.ExecOperations
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.nio.charset.Charset

/**
 * These git commands involve creating or pushing tags
 * Because these happen at task execution level, we are fine with not using ValueSource
 */
class GitWriteCommandsUtil implements Serializable {
    private static final Logger logger = LoggerFactory.getLogger(GitWriteCommandsUtil)
    private final ExecOperations execOperations
    private File rootDir

    GitWriteCommandsUtil(ExecOperations execOperations) {
        this.execOperations = execOperations
    }

    void configure(File gitRoot) {
        this.rootDir = gitRoot
    }


    /**
     * Pushes a Tag to a remote repository
     * @param remote
     * @param tag
     * @return
     *
     * ex. git push origin v1.0.0
     */
    void pushTag(String remote, String tag) {
        try {
            executeGitCommand("push", remote, tag)
        } catch (Exception e) {
            logger.error("Failed to push tag ${tag} to remote ${remote}", e)
        }
    }

    /**
     * Creates a tag with a message
     * @param name
     * @param message
     *
     * ex. git tag -a v1.0.0 -m "Release 1.0.0"
     */
    void createTag(String name, String message) {
        try {
            executeGitCommand( "tag", "-a", name, "-m", message)
        } catch (Exception e) {
            throw new RuntimeException("Failed to create tag ${name} with message ${message}", e)
        }
    }


    /**
     * Fetches the remote repository
     * @param remote
     *
     * ex: git fetch origin
     */
    void fetch(String remote) {
        executeGitCommand( "fetch", remote)
    }


    @CompileDynamic
    String executeGitCommand(Object... args) {
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
        if(errorMsg) {
            throw new GradleException(errorMsg)
        }
        return new String(output.toByteArray(), Charset.defaultCharset())
    }
}
