package nebula.plugin.release.git

import org.gradle.process.ExecOperations

import java.nio.charset.Charset

class GitOps implements Serializable {

    private final ExecOperations execOperations
    private File rootDir

    GitOps(ExecOperations execOperations) {
        this.execOperations = execOperations
    }

    void setRootDir(File rootDir) {
        this.rootDir = rootDir
    }

    String isCleanStatus() {
        return executeGitCommand( "git", "status", "--porcelain").replaceAll("\n", "").trim().empty
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
            it.workingDir = rootDir
            it.standardOutput = output
        }
        return new String(output.toByteArray(), Charset.defaultCharset())
    }
}
