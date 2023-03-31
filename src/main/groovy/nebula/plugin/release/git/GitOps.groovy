package nebula.plugin.release.git

import org.ajoberstar.grgit.Commit
import org.ajoberstar.grgit.Tag
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

    public File getRootDir() {
        return this.rootDir
    }

    boolean isCleanStatus() {
        return executeGitCommand( "git", "status", "--porcelain").replaceAll("\n", "").trim().empty
    }

    boolean hasCommit() {
       try {
           String describe = executeGitCommand( "git", "describe", "--tags", "--always")
           return describe != null && !describe.contains("fatal:")
       } catch (Exception e) {
           return false
       }
    }

    String head() {
        return executeGitCommand( "git", "rev-parse", "HEAD")
    }

    List<Tag> headTags() {
        return executeGitCommand( "git", "tag", "--points-at", "HEAD")
                .split("\n")
                .collect { new Tag(fullName: it) }
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

    void fetch(String remote) {
        executeGitCommand( "git", "fetch", remote)
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
