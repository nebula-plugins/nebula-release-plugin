package nebula.plugin.release.git.command

import nebula.plugin.release.git.model.TagRef
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GitReadOnlyCommandUtil implements Serializable {
    private static final Logger logger = LoggerFactory.getLogger(GitReadOnlyCommandUtil)
    private final ProviderFactory providers

    private Provider usernameFromLogProvider
    private Provider emailFromLogProvider
    private Provider currentBranchProvider
    private Provider isGitRepoProvider
    private Provider describeTagsProvider
    private Provider revParseHeadProvider
    private Provider headTagsProvider
    private Provider refTagsProvider
    private Provider refListCountHeadProvider
    private Provider statusPorcelainProvider
    private Provider isTrackingRemoteBranchProvider
    private Provider isCurrentBranchBehindRemote
    private File rootDir

    GitReadOnlyCommandUtil(ProviderFactory providerFactory) {
        this.providers = providerFactory
    }

    /**
     * Register the providers for the read only commands
     * This uses the provider factory to create the providers in order to support ocnfiguration cache
     * @param gitRoot
     */
    void configure(File gitRoot) {
        this.rootDir = gitRoot
        usernameFromLogProvider = providers.of(UsernameFromLog.class) {
            it.parameters.rootDir.set(rootDir)
        }
        emailFromLogProvider = providers.of(EmailFromLog.class) {
            it.parameters.rootDir.set(rootDir)
        }
        configureCommitterIfNecessary()
        currentBranchProvider = providers.of(CurrentBranch.class) {
            it.parameters.rootDir.set(rootDir)
        }
        isGitRepoProvider = providers.of(IsGitRepo.class) {
            it.parameters.rootDir.set(rootDir)
        }
        describeTagsProvider = providers.of(DescribeTags.class) {
            it.parameters.rootDir.set(rootDir)
        }
        revParseHeadProvider = providers.of(RevParseHead.class) {
            it.parameters.rootDir.set(rootDir)
        }
        headTagsProvider = providers.of(HeadTags.class) {
            it.parameters.rootDir.set(rootDir)
        }
        refTagsProvider = providers.of(RefTags.class) {
            it.parameters.rootDir.set(rootDir)
        }
        refListCountHeadProvider = providers.of(RevListCountHead.class) {
            it.parameters.rootDir.set(rootDir)
        }
        statusPorcelainProvider = providers.of(StatusPorcelain.class) {
            it.parameters.rootDir.set(rootDir)
        }
        isTrackingRemoteBranchProvider = providers.of(IsTrackingRemoteBranch.class) {
            it.parameters.rootDir.set(rootDir)
        }
        isCurrentBranchBehindRemote = providers.of(IsCurrentBranchBehindRemote.class) {
            it.parameters.rootDir.set(rootDir)
        }

    }

    private void configureCommitterIfNecessary() {
        String globalUsername = getGitConfig('--global', 'user.name')
        String globalEmail = getGitConfig('--global', 'user.email')
        String localUsername = getGitConfig('--local', 'user.name')
        String localEmail = getGitConfig('--local', 'user.email')
        String usernameFromLog = usernameFromLogProvider.isPresent() ? usernameFromLogProvider.get() : null
        if(!globalUsername && !localUsername && usernameFromLog) {
            setGitConfig("user.name", usernameFromLog)
        }
        String emailFromLog =  emailFromLogProvider.isPresent() ? emailFromLogProvider.get() : null
        if(!globalEmail && !localEmail && emailFromLog) {
            setGitConfig("user.email", emailFromLog)
        }
    }

    Boolean isGitRepo() {
        try {
            return Boolean.valueOf(isGitRepoProvider.get().toString())
        } catch (Exception e) {
            return false
        }
    }

    String getUsernameFromLog() {
        try {
            return currentBranchProvider.get().toString().replaceAll("\n", "").trim()
        } catch (Exception e) {
            return null
        }
    }

    Boolean hasCommit() {
        try {
            String describe = describeTagsProvider.get().toString()
            return describe != null && !describe.contains("fatal:")
        } catch (Exception e) {
            return false
        }
    }

    String currentBranch() {
        try {
            return currentBranchProvider.get().toString().replaceAll("\n", "").trim()
        } catch (Exception e) {
            return null
        }
    }

    String head() {
        try {
            return revParseHeadProvider.get().toString()
        } catch (Exception e) {
            return null
        }
    }

    List<TagRef> headTags() {
        return headTagsProvider.get().toString()
                .split("\n")
                .findAll { String tag -> !tag?.replaceAll("\n", "")?.isEmpty() }
                .collect { new TagRef(it) }
    }


    Integer getCommitCountForHead() {
        try {
            return refListCountHeadProvider.get().toString()
                    .split("\n")
                    .first()?.replaceAll("\n", "")?.trim()?.toInteger()
        } catch(Exception e) {
            return 0
        }
    }



    String describeHeadWithTags(boolean excludePreReleases) {
        try {
            def describeTagInHeadProvider = excludePreReleases ? providers.of(DescribeHeadWithTagWithExclude.class) {
                it.parameters.rootDir.set(rootDir)
            } : providers.of(DescribeHeadWithTag.class) {
                it.parameters.rootDir.set(rootDir)
            }
            return describeTagInHeadProvider.get().toString()
                    .split("\n")
                    .first()?.replaceAll("\n", "")?.toString()
        } catch(Exception e) {
            return null
        }
    }

    String findCommitForTag(String tag) {
        try {
            def commitForTag = providers.of(CommitFromTag.class) {
                it.parameters.rootDir.set(rootDir)
                it.parameters.tag.set(tag)
            }
            return commitForTag.get().toString()
                    .split("\n")
                    .first()?.replaceAll("\n", "")?.toString()
        } catch(Exception e) {
            return null
        }
    }

    List<String> getTagsPointingAt(String commit) {
        try {
            def tagsPointingAtProvider = providers.of(TagsPointingAt.class) {
                it.parameters.rootDir.set(rootDir)
                it.parameters.commit.set(commit)
            }
            return tagsPointingAtProvider.get().toString()
                    .split("\n")
                    .findAll { String tag -> !tag?.replaceAll("\n", "")?.isEmpty() }
                    .collect()
        } catch(Exception e) {
            return null
        }
    }

    /**
     * Checks if the repo has changes
     * @return
     */
    String status() {
        return statusPorcelainProvider.get().toString()
    }

    boolean isCleanStatus() {
        return statusPorcelainProvider.get().toString().replaceAll("\n", "").trim().empty
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
            return isCurrentBranchBehindRemote.get() != "0"
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
            return isTrackingRemoteBranchProvider.get().toString().contains("${remote}/")
        } catch (Exception e) {
            return false
        }
    }

    /**
     * Returns a git config value for a given scope
     * @param scope
     * @param configKey
     * @return
     */
    String getGitConfig(String scope, String configKey) {
        try {
            def getConfigValueProvider = providers.of(GetGitConfigValue.class) {
                it.parameters.rootDir.set(rootDir)
                it.parameters.gitConfigScope.set(scope)
                it.parameters.gitConfigKey.set(configKey)
            }
            return getConfigValueProvider.get().toString()?.
                    replaceAll("\n", "")?.toString()
        } catch(Exception e) {
            logger.debug("Could not get git config {} {} {}", scope, configKey)
            return null
        }
    }

    /**
     * Returns a git config value for a given scope
     * @param scope
     * @param configKey
     * @return
     */
    void setGitConfig(String configKey, String configValue) {
        try {
            def getConfigValueProvider = providers.of(SetGitConfigValue.class) {
                it.parameters.rootDir.set(rootDir)
                it.parameters.gitConfigKey.set(configKey)
                it.parameters.gitConfigValue.set(configValue)
            }
            getConfigValueProvider.get().toString()?.
                    replaceAll("\n", "")?.toString()
        } catch(Exception e) {
            logger.debug("Could not set git config {} {}", configKey, configValue)
        }
    }
}
