package nebula.plugin.release.git.command

import nebula.plugin.release.git.model.TagRef
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

class GitReadOnlyCommandUtil implements Serializable {
    private final ProviderFactory providers

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

    Boolean isGitRepo() {
        try {
            return Boolean.valueOf(isGitRepoProvider.get().toString())
        } catch (Exception e) {
            return false
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

    /**
     * Returns the tags that point to the current HEAD
     */
    List<String> refTags() {
        try {
            return refTagsProvider.get().toString()
                    .split("\n")
                    .findAll { String tag -> !tag?.replaceAll("\n", "")?.isEmpty() }
                    .toList()
        } catch (Exception e) {
            return Collections.emptyList()
        }
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

    String describeTagForHead(String tagName) {
        try {
            def describeTagInHeadProvider = providers.of(DescribeTagForHead.class) {
                it.parameters.rootDir.set(rootDir)
                it.parameters.tagForSearch.set(tagName)
            }
            return describeTagInHeadProvider.get().toString()
                    .split("\n")
                    .first()?.replaceAll("\n", "")?.toString()
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
}
