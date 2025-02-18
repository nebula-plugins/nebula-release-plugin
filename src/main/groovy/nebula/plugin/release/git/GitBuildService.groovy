package nebula.plugin.release.git

import nebula.plugin.release.git.command.AnyCommit
import nebula.plugin.release.git.command.CommitFromTag
import nebula.plugin.release.git.command.CurrentBranch
import nebula.plugin.release.git.command.DescribeHeadWithTag
import nebula.plugin.release.git.command.DescribeHeadWithTagWithExclude
import nebula.plugin.release.git.command.HeadTags
import nebula.plugin.release.git.command.IsCurrentBranchBehindRemote
import nebula.plugin.release.git.command.IsGitRepo
import nebula.plugin.release.git.command.IsTrackingRemoteBranch
import nebula.plugin.release.git.command.RevListCountHead
import nebula.plugin.release.git.command.RevParseHead
import nebula.plugin.release.git.command.StatusPorcelain
import nebula.plugin.release.git.command.TagsPointingAt
import nebula.plugin.release.git.model.TagRef
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

import javax.inject.Inject

// TODO: migrate GitReadOnlyCommandUtil funcionality to build service when possible
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

    boolean isGitRepo() {
        return this.isGitRepo
    }

    String getCurrentBranch() {
        return this.currentBranch
    }

    boolean hasCommit() {
        return this.hasCommit
    }

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
}
