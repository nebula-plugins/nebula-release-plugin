package nebula.plugin.release.git

import groovy.transform.CompileDynamic
import nebula.plugin.release.git.model.TagRef
import org.gradle.api.GradleException
import org.gradle.process.ExecOperations
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.nio.charset.Charset

/**
 * Responsible for executing native Git commands via Gradle's ExecOperations
 * This replaced grgit/jgit need and it is useful to be configuration cache compliant
 */
class GitOps implements Serializable {
    private static final Logger logger = LoggerFactory.getLogger(GitOps)

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
        return executeGitCommand( "rev-parse", "HEAD")?.replaceAll("\n", "")
    }

    /**
     * Returns the tags that point to the current HEAD
     */
    List<TagRef> headTags() {
        return executeGitCommand( "tag", "--points-at", "HEAD")
                .split("\n")
                .findAll { String tag -> !tag?.replaceAll("\n", "")?.isEmpty() }
                .collect { new TagRef(it) }
    }

    /**
     * Returns the tags that point to the current HEAD
     */
    List<String> refTags() {
        try {
            return executeGitCommand("show-ref", "--tags")
                    .split("\n")
                    .findAll { String tag -> !tag?.replaceAll("\n", "")?.isEmpty() }
                    .toList()
        } catch (Exception e) {
            return Collections.emptyList()
        }
    }


    String describeTagForHead(String tagName) {
        try {
            return executeGitCommand( "describe", "HEAD", "--tags", "--match", tagName)
                    .split("\n")
                    .first()?.replaceAll("\n", "")?.toString()
        } catch(Exception e) {
            return null
        }
    }

    Integer getCommitCountForHead() {
        try {
            return executeGitCommand( "rev-list", "--count", "HEAD")
                    .split("\n")
                    .first()?.replaceAll("\n", "")?.trim()?.toInteger()
        } catch(Exception e) {
            return 0
        }
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
            logger.error("Failed to push tag ${tag} to remote ${remote}", e)
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
        ByteArrayOutputStream error = new ByteArrayOutputStream()
        List<String> commandLineArgs = ["git", "--git-dir=${rootDir.absolutePath}/.git".toString(), "--work-tree=${rootDir.absolutePath}".toString()]
        commandLineArgs.addAll(args)
        execOperations.exec {
            ignoreExitValue = true
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
