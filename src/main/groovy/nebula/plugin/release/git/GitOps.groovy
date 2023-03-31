package nebula.plugin.release.git

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

    File getRootDir() {
        return this.rootDir
    }

    void createTag(String name, String message) {
        try {
            executeGitCommand("git", "tag", "-a", name, "-m", message)
        } catch (Exception e) {
            throw new RuntimeException("Failed to create tag ${name} with message ${message}", e)
        }
    }

    String currentBranch() {
        try {
            return executeGitCommand("git", "rev-parse", "--abbrev-ref", "HEAD")
                    .replaceAll("\n", "").trim()
        } catch (Exception e) {
            return null
        }
    }

    void fetch(String remote) {
        executeGitCommand("git", "fetch", remote)
    }

    boolean hasCommit() {
        try {
            String describe = executeGitCommand("git", "describe", "--tags", "--always")
            return describe != null && !describe.contains("fatal:")
        } catch (Exception e) {
            return false
        }
    }

    String head() {
        return executeGitCommand("git", "rev-parse", "HEAD")
    }

    List<Tag> headTags() {
        return executeGitCommand("git", "tag", "--points-at", "HEAD")
                .split("\n")
                .collect { new Tag(fullName: it) }
    }

    boolean isCleanStatus() {
        return executeGitCommand("git", "status", "--porcelain").replaceAll("\n", "").trim().empty
    }

    boolean isCurrentBranchBehindRemote(String remote) {
        if (!isTrackingRemoteBranch(remote)) {
            return true
        }
        try {
            return executeGitCommand("git", "rev-list", "--count", "--left-only", "@{u}...HEAD").replaceAll("\n", "").trim() != "0"
        } catch (Exception e) {
            return false
        }
    }

    boolean isTrackingRemoteBranch(String remote) {
        try {
            return executeGitCommand("git", "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}").contains("${remote}/")
        } catch (Exception e) {
            return false
        }
    }

    void pushTag(String remote, String tag) {
        try {
            executeGitCommand("git", "push", remote, tag)
        } catch (Exception e) {
            throw new RuntimeException("Failed to push tag ${tag} to remote ${remote}", e)
        }
    }

    String status() {
        return executeGitCommand("git", "status", "--porcelain")
    }

    boolean tagExists(String tag) {
        try {
            return executeGitCommand("git", "tag", "-l", "\"${tag}\"")
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
