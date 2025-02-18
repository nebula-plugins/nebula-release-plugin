package nebula.plugin.release.git

import nebula.plugin.release.git.command.AnyCommit
import nebula.plugin.release.git.command.CommitFromTag
import nebula.plugin.release.git.command.CurrentBranch
import nebula.plugin.release.git.command.DescribeHeadWithTag
import nebula.plugin.release.git.command.DescribeHeadWithTagWithExclude
import nebula.plugin.release.git.command.EmailFromLog
import nebula.plugin.release.git.command.GetGitConfigValue
import nebula.plugin.release.git.command.HeadTags
import nebula.plugin.release.git.command.IsCurrentBranchBehindRemote
import nebula.plugin.release.git.command.IsGitRepo
import nebula.plugin.release.git.command.IsTrackingRemoteBranch
import nebula.plugin.release.git.command.RevListCountHead
import nebula.plugin.release.git.command.RevParseHead
import nebula.plugin.release.git.command.StatusPorcelain
import nebula.plugin.release.git.command.TagsPointingAt
import nebula.plugin.release.git.command.UsernameFromLog
import nebula.plugin.release.git.model.TagRef
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.inject.Inject

abstract class GitBuildService implements BuildService<GitBuildService.Params> {
    interface Params extends BuildServiceParameters {
        DirectoryProperty getGitRootDir()
    }

    private final boolean isGitRepo
    private final boolean hasCommit
    private final String currentBranch
    private final String head
    private final String status
    private final boolean isCleanStatus
    private final File gitRootDir
    private final ProviderFactory providerFactory
    private final List<TagRef> headTags
    private final Integer commitCountForHead
    private static final Logger LOGGER = LoggerFactory.getLogger(GitBuildService.class)

    @Inject
    GitBuildService(ProviderFactory providers) {
        this.gitRootDir = getParameters().gitRootDir.get().asFile
        this.providerFactory = providers
        this.isGitRepo = detectIsGitRepo()
        this.currentBranch = detectCurrentBranch()
        this.status = determineStatus()
        this.hasCommit = determineHasCommit()
        this.isCleanStatus = determineIsCleanStatus()
        this.head = determineHead()
        this.headTags = determineHeadTags()
        this.commitCountForHead = determineCommitCountForHead()
    }

    /**
     * Verifies that git is configured with proper user name and email
     */
    void verifyUserGitConfig() {
        String username = getGitConfig('user.name')
        String email = getGitConfig('user.email')
        if(username && email) {
            return
        }
        String globalUsername = getGitConfig('--global', 'user.name')
        String globalEmail = getGitConfig('--global', 'user.email')
        if(globalUsername && globalEmail) {
            return
        }
        String systemUsername = getGitConfig('--system', 'user.name')
        String systemEmail = getGitConfig('--system', 'user.email')
        if(systemUsername && systemEmail) {
            return
        }
        String localUsername = getGitConfig('--local', 'user.name')
        String localEmail = getGitConfig('--local', 'user.email')
        if(localUsername && localEmail) {
            return
        }

        Provider usernameFromLogProvider = providerFactory.of(UsernameFromLog.class) {
            it.parameters.rootDir.set(gitRootDir)
        }
        String usernameFromLog = usernameFromLogProvider.isPresent() ? usernameFromLogProvider.get() : null
        if(!username && !globalUsername && !localUsername && !systemUsername && usernameFromLog) {
            throw new GradleException("Git user.name is not set. Please configure git user.name globally, locally or system wide. You can learn more in https://git-scm.com/book/en/v2/Getting-Started-First-Time-Git-Setup")
        }
        Provider emailFromLogProvider = providerFactory.of(EmailFromLog.class) {
            it.parameters.rootDir.set(gitRootDir)
        }
        String emailFromLog =  emailFromLogProvider.isPresent() ? emailFromLogProvider.get() : null
        if(!email && !globalEmail && !localEmail && !systemEmail && emailFromLog) {
            throw new GradleException("Git user.email is not set. Please configure git user.email globally, locally or system wide. You can learn more in https://git-scm.com/book/en/v2/Getting-Started-First-Time-Git-Setup")
        }
    }

    /**
     * Used to check if the current directory is a git repo
     * ex. git rev-parse --is-inside-work-tree -> true OR
     *    git rev-parse --is-inside-work-tree -> fatal: not a git repository (or any of the parent directories): .git when there isn't a repo
     */
    boolean isGitRepo() {
        return this.isGitRepo
    }

    /**
     * Returns current branch name
     * ex.  git rev-parse --abbrev-ref HEAD  -> configuration-cache-support
     */
    String getCurrentBranch() {
        return this.currentBranch
    }

    /**
     * Uses to determine if a given repo has any commit
     */
    boolean hasCommit() {
        return this.hasCommit
    }

    /**
     * Returns the current HEAD commit
     * ex. git rev-parse HEAD -> 8e6c4c925a54dbe827f043d21cd7a2a01b97fbac
     */
    String getHead() {
        return this.head
    }

    String getStatus() {
        return this.status
    }

    boolean isCleanStatus() {
        return this.isCleanStatus
    }

    List<TagRef> headTags() {
        return this.headTags
    }

    Integer getCommitCountForHead() {
        return this.commitCountForHead
    }

    String describeHeadWithTags(boolean excludePreReleases) {
        try {
            def describeTagInHeadProvider = excludePreReleases ? providerFactory.of(DescribeHeadWithTagWithExclude.class) {
                it.parameters.rootDir.set(gitRootDir)
            } : providerFactory.of(DescribeHeadWithTag.class) {
                it.parameters.rootDir.set(gitRootDir)
            }
            return describeTagInHeadProvider.get().toString()
                    .split("\n").toList()
                    .first()?.replaceAll("\n", "")?.toString()
        } catch(Exception e) {
            return null
        }
    }

    String findCommitForTag(String tag) {
        try {
            def commitForTag = providerFactory.of(CommitFromTag.class) {
                it.parameters.rootDir.set(gitRootDir)
                it.parameters.tag.set(tag)
            }
            return commitForTag.get().toString()
                    .split("\n").toList()
                    .first()?.replaceAll("\n", "")?.toString()
        } catch(Exception e) {
            return null
        }
    }


    List<String> getTagsPointingAt(String commit) {
        try {
            def tagsPointingAtProvider = providerFactory.of(TagsPointingAt.class) {
                it.parameters.rootDir.set(gitRootDir)
                it.parameters.commit.set(commit)
            }
            return tagsPointingAtProvider.get().toString()
                    .split("\n").toList()
                    .findAll { String tag -> !tag?.replaceAll("\n", "")?.isEmpty() }
                    .collect()
        } catch(Exception e) {
            return null
        }
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
            def isCurrentBranchBehindRemoteProvider = providerFactory.of(IsCurrentBranchBehindRemote.class) {
                it.parameters.rootDir.set(gitRootDir)
            }
            return isCurrentBranchBehindRemoteProvider.get() != "0"
        } catch (Exception e) {
            return false
        }
    }

    /**
     * Checks if the current branch is tracking a remote branch
     * @param remote
     * @return
     */
    private boolean isTrackingRemoteBranch(String remote) {
        try {
            return providerFactory.of(IsTrackingRemoteBranch.class) {
                it.parameters.rootDir.set(gitRootDir)
            }.get().toString().contains("${remote}/")
        } catch (Exception e) {
            return false
        }
    }

    private Integer determineCommitCountForHead() {
        try {
            def refListCountHeadProvider = providerFactory.of(RevListCountHead.class) {
                it.parameters.rootDir.set(gitRootDir)
            }
            return refListCountHeadProvider.get().toString()
                    .split("\n").toList()
                    .first()?.replaceAll("\n", "")?.trim()?.toInteger()
        } catch(Exception e) {
            return 0
        }
    }

    private List<TagRef> determineHeadTags() {
       try {
           def headTagsProvider = providerFactory.of(HeadTags.class) {
               it.parameters.rootDir.set(gitRootDir)
           }
           return headTagsProvider.get().toString()
                   .split("\n").toList()
                   .findAll { String tag -> !tag?.replaceAll("\n", "")?.isEmpty() }
                   .collect { new TagRef(it) }
       } catch (Exception e) {
           return Collections.emptyList()
       }
    }

    private String determineStatus() {
        try {
            def statusPorcelainProvider = providerFactory.of(StatusPorcelain.class) {
                it.parameters.rootDir.set(gitRootDir)
            }
            return statusPorcelainProvider.get().toString()
        } catch (Exception e) {
            return null
        }
    }

    private boolean determineIsCleanStatus() {
       try {
           def statusPorcelainProvider = providerFactory.of(StatusPorcelain.class) {
               it.parameters.rootDir.set(gitRootDir)
           }
           return statusPorcelainProvider.get().toString().replaceAll("\n", "").trim().empty
       } catch (Exception e) {
           return false
       }
    }

    private String determineHead() {
        try {
            def revParseHeadProvider = providerFactory.of(RevParseHead.class) {
                it.parameters.rootDir.set(gitRootDir)
            }
            return revParseHeadProvider.get().toString()
        } catch (Exception e) {
            return null
        }
    }

    private Boolean determineHasCommit() {
        try {
            def anyCommitProvider = providerFactory.of(AnyCommit.class) {
                it.parameters.rootDir.set(gitRootDir)
            }
            String describe = anyCommitProvider.get().toString()
            return describe != null && !describe.empty && !describe.contains("fatal:")
        } catch (Exception e) {
            return false
        }
    }

    private Boolean detectIsGitRepo() {
        try {
            Provider isGitRepoProvider = providerFactory.of(IsGitRepo.class) {
                it.parameters.rootDir.set(gitRootDir)
            }
            return Boolean.valueOf(isGitRepoProvider.get().toString())
        } catch (Exception e) {
            return false
        }
    }

    private String detectCurrentBranch() {
        try {
            Provider currentBranchProvider = providerFactory.of(CurrentBranch.class) {
                it.parameters.rootDir.set(gitRootDir)
            }
            return currentBranchProvider.get().toString().replaceAll("\n", "").trim()
        } catch (Exception e) {
            return null
        }

    }

    /**
     * Returns a git config value for a given scope
     * @param scope
     * @param configKey
     * @return
     */
    private String getGitConfig(String scope, String configKey) {
        try {
            def getConfigValueProvider = providerFactory.of(GetGitConfigValue.class) {
                it.parameters.rootDir.set(gitRootDir)
                it.parameters.gitConfigScope.set(scope)
                it.parameters.gitConfigKey.set(configKey)
            }
            return getConfigValueProvider.get().toString()?.
                    replaceAll("\n", "")?.toString()
        } catch(Exception e) {
            LOGGER.debug("Could not get git config {} {}", scope, configKey)
            return null
        }
    }

    /**
     * Returns a git config value for a given scope
     * @param configKey
     * @return
     */
    private String getGitConfig(String configKey) {
        try {
            def getConfigValueProvider = providerFactory.of(GetGitConfigValue.class) {
                it.parameters.rootDir.set(gitRootDir)
                it.parameters.gitConfigKey.set(configKey)
            }
            return getConfigValueProvider.get().toString()?.
                    replaceAll("\n", "")?.toString()
        } catch(Exception e) {
            LOGGER.debug("Could not get git config {}", configKey)
            return null
        }
    }
}
