package nebula.plugin.release.git

import org.gradle.process.ExecOperations

import java.nio.charset.Charset

class GitOps implements Serializable {

    private final ExecOperations execOperations

    GitOps(ExecOperations execOperations) {
        this.execOperations = execOperations
    }

    String status() {
        return executeGitCommand( "git", "status", "--porcelain")
    }

    String currentBranch() {
       try {
           return executeGitCommand( "git", "rev-parse", "--abbrev-ref", "HEAD")
                   .replaceAll("\n", "").trim()
       } catch (Exception e) {
           return null
       }
    }

    boolean tagExists(String tag) {
        try {
            return executeGitCommand( "git", "tag", "-l", "\"${tag}\"")
                    .replaceAll("\n", "").trim().contains(tag)
        } catch (Exception e) {
            return false
        }
    }

    String executeGitCommand(Object... args) {
        ByteArrayOutputStream output = new ByteArrayOutputStream()
        execOperations.exec {
            it.commandLine args
            it.standardOutput = output
        }
        return new String(output.toByteArray(), Charset.defaultCharset())
    }
}
