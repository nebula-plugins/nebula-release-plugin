package nebula.plugin.release.git.command

import groovy.transform.CompileDynamic
import org.gradle.api.GradleException
import org.gradle.api.provider.ValueSource
import org.gradle.process.ExecOperations
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.inject.Inject
import java.nio.charset.Charset

/**
 * These read only git commands use ValueSource approach for configuration cache
 * @see {@link https://docs.gradle.org/8.4/userguide/configuration_cache.html#config_cache:requirements:external_processes}
 */
abstract class GitReadCommand implements ValueSource<String, GitCommandParameters> {
    @Inject
    abstract ExecOperations getExecOperations()

    @CompileDynamic
    String executeGitCommand(Object ... args) {
        File rootDir = parameters.rootDir.get()
        ByteArrayOutputStream output = new ByteArrayOutputStream()
        ByteArrayOutputStream error = new ByteArrayOutputStream()
        List<String> commandLineArgs = ["git", "--git-dir=${rootDir.absolutePath}/.git".toString(), "--work-tree=${rootDir.absolutePath}".toString()]
        commandLineArgs.addAll(args)
        execOperations.exec {
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

/**
 * Returns current branch name
 * ex.  git rev-parse --abbrev-ref HEAD  -> configuration-cache-support
 */
abstract class CurrentBranch extends GitReadCommand {
    @Override
    String obtain() {
        try {
            return executeGitCommand(  "rev-parse", "--abbrev-ref", "HEAD")
                    .replaceAll("\n", "").trim()
        } catch (Exception e) {
            return null
        }
    }
}

/**
 * Uses git describe to find a given tag in the history of the current branch
 * ex. git describe HEAD --tags --match v10.0.0 -> v10.0.0-220-ga00baaa
 */
abstract class DescribeTagForHead extends GitReadCommand {
    @Override
    String obtain() {
        try {
            return executeGitCommand( "describe", "HEAD", "--tags", "--match", parameters.getTagForSearch().get())
        } catch (Exception e) {
            return null
        }
    }
}

/**
 * Uses to determine if a given repo has any commit
 */
abstract class DescribeTags extends GitReadCommand {
    @Override
    String obtain() {
        try {
            return executeGitCommand(   "describe", "--tags", "--always")
                    .replaceAll("\n", "").trim()
        } catch (Exception e) {
            return null
        }
    }
}


/**
 * Used to determine if a tag is pointing to current head
 * ex. git tag --points-at HEAD -> v10.0.0
 */
abstract class HeadTags extends GitReadCommand {

    @Override
    String obtain() {
        try {
            return executeGitCommand(  "tag", "--points-at", "HEAD")
        } catch (Exception e) {
            return null
        }
    }
}

/**
 * Used to check if the current branch is behind the remote one
 * ex. git rev-list --count --left-only @{u}...HEAD -> 1 = behind, 0 = up-to-date
 */
abstract class IsCurrentBranchBehindRemote extends GitReadCommand {

    @Override
    String obtain() {
        try {
            return executeGitCommand( "rev-list", "--count", "--left-only", "@{u}...HEAD").replaceAll("\n", "").trim()
        } catch (Exception e) {
            return null
        }
    }
}

/**
 * Used to check if the current directory is a git repo
 * ex. git rev-parse --is-inside-work-tree -> true OR
 *    git rev-parse --is-inside-work-tree -> fatal: not a git repository (or any of the parent directories): .git when there isn't a repo
 */
abstract class IsGitRepo extends GitReadCommand  {

    @Override
    String obtain() {
        try {
            return executeGitCommand( "rev-parse", "--is-inside-work-tree").contains("true")
        } catch (Exception e) {
            return false
        }
    }
}

/**
 * Used to verify if the current branch is tracking a remote branch
 * ex. git rev-parse --abbrev-ref --symbolic-full-name @{u} -> origin/main
 * ex. git rev-parse --abbrev-ref --symbolic-full-name @{u} -> fatal: no upstream configured for branch 'configuration-cache-support' when there isn't a remote branch
 */
abstract class IsTrackingRemoteBranch  extends GitReadCommand {
    @Override
    String obtain() {
        try {
            return executeGitCommand( "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}")
        } catch (Exception e) {
            return null
        }
    }
}

/**
 * Returns all the tag refs for current branch
 * ex. git show-ref --tags can result in:
 * 8e6c4c925a54dbe827f043d21cd7a2a01b97fbac refs/tags/v15.3.0
 * b95875abf10cd3fdf5253c6be20658c2682b82e1 refs/tags/v15.3.1
 * dd097a0f29af8b54091a0f72521d052bc0d739dd refs/tags/v16.0.0
 */
abstract class RefTags extends GitReadCommand {

    @Override
    String obtain() {
        try {
            return executeGitCommand(  "show-ref", "--tags")
        } catch (Exception e) {
            return null
        }
    }
}

/**
 * This returns the number of commits in HEAD without a tag.
 * Mostly used when we can't find tags and we want to return some information on how many commits we are behind
 * ex. git rev-list --count HEAD -> 578
 */
abstract class RevListCountHead  extends GitReadCommand {

    @Override
    String obtain() {
        try {
            return executeGitCommand(  "rev-list", "--count", "HEAD")
        } catch (Exception e) {
            return null
        }
    }
}

/**
 * Returns the current HEAD commit
 * ex. git rev-parse HEAD -> 8e6c4c925a54dbe827f043d21cd7a2a01b97fbac
 */
abstract class RevParseHead extends GitReadCommand {

    @Override
    String obtain() {
        try {
            return executeGitCommand(  "rev-parse", "HEAD")
                    .replaceAll("\n", "").trim()
        } catch (Exception e) {
            return null
        }
    }
}

/**
 * Returns the status of the current repo in a more machine readable format
 * ex. git status --porcelain ->  M buildSrc/src/main/groovy/nebula/plugin/release/OverrideStrategies.groovy
 */
abstract class StatusPorcelain extends GitReadCommand {

    @Override
    String obtain() {
        try {
            return executeGitCommand( "status", "--porcelain")
        } catch (Exception e) {
            return null
        }
    }
}

/**
 * Retrieves a given Git config key with its value for a given scope
 */
abstract class GetGitConfigValue extends GitReadCommand {
    private static final Logger logger = LoggerFactory.getLogger(GetGitConfigValue)
    @Override
    String obtain() {
        try {
            return executeGitCommand( "config", parameters.getGitConfigScope().get(), parameters.getGitConfigKey().get())
        } catch (Exception e) {
            logger.debug("Could not get git config {} {} {}", parameters.getGitConfigScope().get(), parameters.getGitConfigKey().get())
            return null
        }
    }
}


/**
 * Set a given Git config key with its value for a given scope
 */
abstract class SetGitConfigValue extends GitReadCommand {
    private static final Logger logger = LoggerFactory.getLogger(SetGitConfigValue)
    @Override
    String obtain() {
        try {
            return executeGitCommand( "config", parameters.getGitConfigKey().get(), parameters.getGitConfigValue().get())
        } catch (Exception e) {
            logger.debug("Could not set git config {} {} {}", parameters.getGitConfigKey().get(), parameters.getGitConfigValue().get())
            return null
        }
    }
}

