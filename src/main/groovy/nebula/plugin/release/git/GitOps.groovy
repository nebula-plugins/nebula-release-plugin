package nebula.plugin.release.git

import groovy.transform.CompileDynamic
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

    /*
     * Adds a tag to the current commit
     */
    void createTag(String name, String message) {
        try {
            executeGitCommand( "tag", "-a", name, "-m", message)
        } catch (Exception e) {
            throw new RuntimeException("Failed to create tag ${name} with message ${message}", e)
        }
    }

    /*
     * Returns the current branch name
     */
    String currentBranch() {
        try {
            return executeGitCommand( "rev-parse", "--abbrev-ref", "HEAD")
                    .replaceAll("\n", "").trim()
        } catch (Exception e) {
            return null
        }
    }

    /**
     * Fetches the remote repository
     * @param remote
     */
    void fetch(String remote) {
        executeGitCommand( "fetch", remote)
    }

    /**
     * Checks if the repo is a git repository
     * @return
     */
    boolean isGitRepo() {
        try {
            return executeGitCommand( "rev-parse", "--is-inside-work-tree").contains("true")
        } catch (Exception e) {
            return false
        }
    }

    /**
     * Checks if the repo has a commit
     * @return
     */
    boolean hasCommit() {
        try {
            String describe = executeGitCommand( "describe", "--tags", "--always")
            return describe != null && !describe.contains("fatal:")
        } catch (Exception e) {
            return false
        }
    }

    /*
     * Returns the current HEAD commit
     */
    String head() {
        return executeGitCommand( "rev-parse", "HEAD")
    }

    /**
     * Returns the tags that point to the current HEAD
     */
    List<Tag> headTags() {
        return executeGitCommand( "tag", "--points-at", "HEAD")
                .split("\n")
                .findAll { String tag -> !tag?.replaceAll("\n", "")?.isEmpty() }
                .collect { new Tag(fullName: it) }
    }

    /**
     * Checks if the repo has changes
     * @return
     */
    boolean isCleanStatus() {
        return executeGitCommand( "status", "--porcelain").replaceAll("\n", "").trim().empty
    }

    /**
     * Checks if the current branch is behind of the remote branch
     * @param remote
     * @return
     */
    boolean isCurrentBranchBehindRemote(String remote) {
        if (!isTrackingRemoteBranch(remote)) {
            return true
        }
        try {
            return executeGitCommand( "rev-list", "--count", "--left-only", "@{u}...HEAD").replaceAll("\n", "").trim() != "0"
        } catch (Exception e) {
            return false
        }
    }

    /**
     * Checks if the current branch is tracking a remote branch
     * @param remote
     * @return
     */
    boolean isTrackingRemoteBranch(String remote) {
        try {
            return executeGitCommand( "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}").contains("${remote}/")
        } catch (Exception e) {
            return false
        }
    }

    /**
     * Pushes a Tag to a remote repository
     * @param remote
     * @param tag
     * @return
     */
    void pushTag(String remote, String tag) {
        try {
            executeGitCommand("push", remote, tag)
        } catch (Exception e) {
            throw new RuntimeException("Failed to push tag ${tag} to remote ${remote}", e)
        }
    }

    /**
     * Checks if the repository is dirty
     */
    String status() {
        return executeGitCommand("status", "--porcelain")
    }

    /**
     * Checks if the tag exists in the local repository
     * @param tag
     * @return
     */
    boolean tagExists(String tag) {
        try {
            return executeGitCommand("tag", "-l", "\"${tag}\"")
                    .replaceAll("\n", "").trim().contains(tag)
        } catch (Exception e) {
            return false
        }
    }


    @CompileDynamic
    String executeGitCommand(Object... args) {
        ByteArrayOutputStream output = new ByteArrayOutputStream()
        List<String> commandLineArgs = ["git", "--git-dir=${rootDir.absolutePath}/.git".toString(), "--work-tree=${rootDir.absolutePath}".toString()]
        commandLineArgs.addAll(args)
        execOperations.exec {
            it.setCommandLine(commandLineArgs)
            it.standardOutput = output
        }
        return new String(output.toByteArray(), Charset.defaultCharset())
    }
}
