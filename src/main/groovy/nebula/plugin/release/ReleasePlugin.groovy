/*
 * Copyright 2014-2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nebula.plugin.release

import groovy.transform.CompileDynamic
import nebula.plugin.release.git.base.BaseReleasePlugin
import nebula.plugin.release.git.base.ReleasePluginExtension
import nebula.plugin.release.git.base.ReleaseVersion
import nebula.plugin.release.git.base.TagStrategy
import nebula.plugin.release.git.command.GitReadOnlyCommandUtil
import nebula.plugin.release.git.command.GitWriteCommandsUtil
import nebula.plugin.release.git.semver.SemVerStrategy
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.ivy.plugins.IvyPublishPlugin
import org.gradle.api.publish.ivy.tasks.GenerateIvyDescriptor
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskProvider
import org.gradle.process.ExecOperations

import javax.inject.Inject

import static nebula.plugin.release.util.ReleaseTasksUtil.*

class ReleasePlugin implements Plugin<Project> {
    public static final String DISABLE_GIT_CHECKS = 'release.disableGitChecks'
    public static final String DEFAULT_VERSIONING_STRATEGY = 'release.defaultVersioningStrategy'
    Project project
    static Logger logger = Logging.getLogger(ReleasePlugin)
    static final String GROUP = 'Nebula Release'

    private final GitReadOnlyCommandUtil gitCommandUtil
    private final GitWriteCommandsUtil gitWriteCommandsUtil

    @Inject
    ReleasePlugin(ExecOperations execOperations, ProviderFactory providerFactory) {
        this.gitCommandUtil = new GitReadOnlyCommandUtil(providerFactory)
        this.gitWriteCommandsUtil = new GitWriteCommandsUtil(execOperations)
    }

    @CompileDynamic
    @Override
    void apply(Project project) {
        this.project = project

        File gitRoot = project.hasProperty('git.root') ? new File(project.property('git.root')) : project.rootProject.projectDir

        // Verify user git config only when using release tags and 'release.useLastTag' property is not used
        boolean shouldVerifyUserGitConfig = isReleaseTaskThatRequiresTagging(project.gradle.startParameter.taskNames) && !isUsingLatestTag(project)
        gitCommandUtil.configure(gitRoot, shouldVerifyUserGitConfig)
        gitWriteCommandsUtil.configure(gitRoot)

        boolean isGitRepo = gitCommandUtil.isGitRepo()
        if(!isGitRepo) {
            this.project.version = '0.1.0-dev.0.uncommitted'
            logger.warn("Git repository not found at $gitRoot -- nebula-release tasks will not be available. Use the git.root Gradle property to specify a different directory.")
            return
        }
        checkForBadBranchNames()
        boolean replaceDevSnapshots = FeatureFlags.isDevSnapshotReplacementEnabled(project)
        if (project == project.rootProject) {
            project.plugins.apply(BaseReleasePlugin)
            ReleasePluginExtension releaseExtension = project.extensions.findByType(ReleasePluginExtension)

            SemVerStrategy defaultStrategy = replaceDevSnapshots ? NetflixOssStrategies.IMMUTABLE_SNAPSHOT(project) : NetflixOssStrategies.DEVELOPMENT(project)
            def propertyBasedStrategy
            if (project.hasProperty(DEFAULT_VERSIONING_STRATEGY)) {
                propertyBasedStrategy = getPropertyBasedVersioningStrategy()
            }
            releaseExtension.with {
                versionStrategy new OverrideStrategies.NoCommitStrategy()
                versionStrategy new OverrideStrategies.ReleaseLastTagStrategy(project)
                versionStrategy new OverrideStrategies.GradlePropertyStrategy(project)
                if (propertyBasedStrategy) {
                    versionStrategy propertyBasedStrategy
                }
                versionStrategy NetflixOssStrategies.SNAPSHOT(project)
                versionStrategy NetflixOssStrategies.IMMUTABLE_SNAPSHOT(project)
                versionStrategy NetflixOssStrategies.DEVELOPMENT(project)
                versionStrategy NetflixOssStrategies.PRE_RELEASE(project)
                versionStrategy NetflixOssStrategies.FINAL(project)
                if (propertyBasedStrategy) {
                    defaultVersionStrategy = propertyBasedStrategy
                } else {
                    defaultVersionStrategy = defaultStrategy
                }
            }

            releaseExtension.with {extension ->
                gitReadCommands = gitCommandUtil
                gitWriteCommands = gitWriteCommandsUtil
                tagStrategy { TagStrategy tagStrategy ->
                    tagStrategy.generateMessage = { ReleaseVersion version ->
                        StringBuilder builder = new StringBuilder()
                        builder << "Release of ${version.version}\n\n"
                        builder.toString()
                    }
                }
            }

            ReleaseExtension nebulaReleaseExtension = project.extensions.create(NEBULA_RELEASE_EXTENSION_NAME, ReleaseExtension)

            TaskProvider<ReleaseCheck> releaseCheck = project.tasks.register(RELEASE_CHECK_TASK_NAME, ReleaseCheck) {
                it.group = GROUP
                it.branchName = gitCommandUtil.currentBranch()
                it.patterns = nebulaReleaseExtension
            }

            TaskProvider<Task> postReleaseTask = project.tasks.register(POST_RELEASE_TASK_NAME) {
                it.group = GROUP
                it.dependsOn project.tasks.named('release')
            }

            TaskProvider snapshotSetupTask = project.tasks.register(SNAPSHOT_SETUP_TASK_NAME)
            TaskProvider immutableSnapshotSetupTask = project.tasks.register(IMMUTABLE_SNAPSHOT_SETUP_TASK_NAME)
            TaskProvider devSnapshotSetupTask = project.tasks.register(DEV_SNAPSHOT_SETUP_TASK_NAME)
            TaskProvider candidateSetupTask = project.tasks.register(CANDIDATE_SETUP_TASK_NAME) {
                it.configure {
                    project.allprojects.each { it.status = 'candidate' }
                }
            }
            TaskProvider finalSetupTask = project.tasks.register(FINAL_SETUP_TASK_NAME) {
                it.configure {
                    project.allprojects.each { it.status = 'release' }
                }
            }
            [snapshotSetupTask, immutableSnapshotSetupTask, devSnapshotSetupTask, candidateSetupTask, finalSetupTask].each {
                it.configure {
                    it.group = GROUP
                    it.dependsOn releaseCheck
                }
            }

            TaskProvider<Task> snapshotTask = project.tasks.register(SNAPSHOT_TASK_NAME) {
                it.dependsOn snapshotSetupTask
            }
            TaskProvider<Task> immutableSnapshotTask = project.tasks.register(IMMUTABLE_SNAPSHOT_TASK_NAME) {
                it.dependsOn immutableSnapshotSetupTask
            }
            TaskProvider<Task> devSnapshotTask = project.tasks.register(DEV_SNAPSHOT_TASK_NAME) {
                it.dependsOn devSnapshotSetupTask
            }
            TaskProvider<Task> candidateTask = project.tasks.register(CANDIDATE_TASK_NAME) {
                it.dependsOn candidateSetupTask
            }
            TaskProvider<Task> finalTask = project.tasks.register(FINAL_TASK_NAME) {
                it.dependsOn finalSetupTask
            }

            [snapshotTask, immutableSnapshotTask, devSnapshotTask, candidateTask, finalTask].each {
                it.configure {
                    it.group = GROUP
                    it.dependsOn postReleaseTask
                }
            }

            List<String> cliTasks = project.gradle.startParameter.taskNames
            def isSnapshotRelease = determineStage(cliTasks, releaseCheck, replaceDevSnapshots)
            checkStateForStage(isSnapshotRelease)

            if (shouldSkipGitChecks()) {
                removeReleaseAndPrepLogic(project)
            }

            project.gradle.taskGraph.whenReady { TaskExecutionGraph g ->
                if (!nebulaReleaseExtension.checkRemoteBranchOnRelease) {
                    removePrepLogic(project)
                }
            }

        } else {
            project.version = project.rootProject.version
        }


        boolean isParent = project.rootProject.subprojects.any { it.parent == project }
        if (!isParent) {
            project.plugins.withType(JavaPlugin) {
                project.rootProject.tasks.named('release').configure {
                    it.dependsOn project.tasks.named('build')
                }
            }
        }

        project.gradle.taskGraph.whenReady { TaskExecutionGraph g ->
            List<String> tasks = [DEV_SNAPSHOT_TASK_NAME, SNAPSHOT_TASK_NAME].collect { project.getPath() + it }
            if (tasks.any { g.hasTask(it) }) {
                removeReleaseAndPrepLogic(project)
            }
        }

        configurePublishingIfPresent()
        configureBintrayTasksIfPresent()
    }

    private Object getPropertyBasedVersioningStrategy() {
        String clazzName = project.property(DEFAULT_VERSIONING_STRATEGY).toString()
        try {
            return Class.forName(clazzName).getDeclaredConstructor().newInstance()
        } catch (ClassNotFoundException e) {
            logger.error("Could not initialize a versioning strategy using class: $clazzName", e)
            return null
        }
    }

    private void removeReleaseAndPrepLogic(Project project) {
        removeReleaseLogic(project)
        removePrepLogic(project)
    }

    private void removeReleaseLogic(Project project) {
        project.tasks.named('release').configure {
            it.enabled = false
        }
    }

    private void removePrepLogic(Project project) {
        project.tasks.named('prepare').configure {
            it.enabled = false
        }
    }

    private boolean determineStage(List<String> cliTasks, TaskProvider<ReleaseCheck> releaseCheck, boolean replaceDevSnapshots) {
        def hasSnapshot = cliTasks.contains(SNAPSHOT_TASK_NAME) || cliTasks.contains(SNAPSHOT_TASK_NAME_OPTIONAL_COLON)
        def hasDevSnapshot = cliTasks.contains(DEV_SNAPSHOT_TASK_NAME) || cliTasks.contains(DEV_SNAPSHOT_SETUP_TASK_NAME_OPTIONAL_COLON)
        def hasImmutableSnapshot = cliTasks.contains(IMMUTABLE_SNAPSHOT_TASK_NAME) || cliTasks.contains(IMMUTABLE_SNAPSHOT_TASK_NAME_OPTIONAL_COLON)
        def hasCandidate = cliTasks.contains(CANDIDATE_TASK_NAME) || cliTasks.contains(CANDIDATE_TASK_NAME_OPTIONAL_COLON)
        def hasFinal = cliTasks.contains(FINAL_TASK_NAME) || cliTasks.contains(FINAL_TASK_NAME_WITH_OPTIONAL_COLON)
        if ([hasSnapshot, hasImmutableSnapshot, hasDevSnapshot, hasCandidate, hasFinal].count { it } > 2) {
            throw new GradleException('Only one of snapshot, immutableSnapshot, devSnapshot, candidate, or final can be specified.')
        }

        def isSnapshotRelease = hasSnapshot || hasDevSnapshot || hasImmutableSnapshot || (!hasCandidate && !hasFinal)

        releaseCheck.configure {
            it.isSnapshotRelease = isSnapshotRelease
        }

        if (hasFinal) {
            setupStatus('release')
            applyReleaseStage('final')
        } else if (hasCandidate) {
            setupStatus('candidate')
            applyReleaseStage('rc')
        } else if (hasImmutableSnapshot) {
            applyReleaseStage('snapshot')
        } else if (hasSnapshot) {
            applyReleaseStage('SNAPSHOT')
        } else if (hasDevSnapshot) {
            if (replaceDevSnapshots) {
                applyReleaseStage('snapshot')
            } else {
                applyReleaseStage('dev')
            }
        }

        return isSnapshotRelease
    }

    private void checkStateForStage(boolean isSnapshotRelease) {
        if (!isSnapshotRelease) {
            String status = gitCommandUtil.status()
            if (!status.empty) {
                String message = new ErrorMessageFormatter().format(status)
                throw new GradleException(message)
            }
        }
    }

    private boolean shouldSkipGitChecks() {
        def disableGit = project.hasProperty(DISABLE_GIT_CHECKS) && project.property(DISABLE_GIT_CHECKS) as Boolean
        def travis = project.hasProperty('release.travisci') && project.property('release.travisci').toString().toBoolean()
        disableGit || travis
    }

    @CompileDynamic
    void setupStatus(String status) {
        project.plugins.withType(IvyPublishPlugin) {
            project.publishing {
                publications.withType(IvyPublication) {
                    descriptor.status = status
                }
            }
        }
    }

    @CompileDynamic
    void applyReleaseStage(String stage) {
        final String releaseStage = 'release.stage'
        project.allprojects.each { it.ext.set(releaseStage, stage) }
    }

    void configurePublishingIfPresent() {
        project.plugins.withType(MavenPublishPlugin) {
            def tasks = project.tasks.withType(GenerateMavenPom)
            project.rootProject.tasks.named('postRelease').configure {
                it.dependsOn(tasks)
            }
        }

        project.plugins.withType(IvyPublishPlugin) {
            TaskCollection tasks = project.tasks.withType(GenerateIvyDescriptor)
            project.rootProject.tasks.named('postRelease').configure {
                it.dependsOn(tasks)
            }
        }
    }

    @CompileDynamic
    void configureBintrayTasksIfPresent() {
        project.plugins.withId('com.jfrog.artifactory') {
            logger.info('Configuring jfrog artifactory plugin to work with release plugin')
            Class taskClass = null
            if (isClassPresent('org.jfrog.gradle.plugin.artifactory.task.BuildInfoBaseTask')) {
                project.logger.warn 'Please upgrade com.jfrog.artifactory (org.jfrog.buildinfo:build-info-extractor-gradle:) to version 4.6.0 or above'
                taskClass = Class.forName('org.jfrog.gradle.plugin.artifactory.task.BuildInfoBaseTask')
            } else if (isClassPresent('org.jfrog.gradle.plugin.artifactory.task.ArtifactoryTask')) {
                // JFrog removed BuildInfoBaseTask see https://www.jfrog.com/jira/browse/GAP-281
                taskClass = Class.forName('org.jfrog.gradle.plugin.artifactory.task.ArtifactoryTask')
            }
            if (taskClass != null) {
                TaskCollection artifactoryTasks = project.tasks.withType(taskClass)
                artifactoryTasks.configureEach { Task task ->
                    project.plugins.withType(JavaPlugin) {
                        task.dependsOn(project.tasks.named('build'))
                    }
                }
                project.rootProject.tasks.named('postRelease').configure {
                    it.dependsOn(artifactoryTasks)
                }
            }
        }
    }

    boolean isClassPresent(String name) {
        try {
            Class.forName(name)
            return true
        } catch (Throwable ex) {
            logger.debug("Class $name is not present")
            return false
        }
    }

    void checkForBadBranchNames() {
        String currentBranch = gitCommandUtil.currentBranch()
        if (!currentBranch) {
            return
        }
        if (currentBranch.endsWith('-')) {
            throw new GradleException('Nebula Release plugin does not support branches that end with dash (-).' +
                    'Please rename your branch')
        }
        if (currentBranch.contains('--')) {
            throw new GradleException("Branch ${currentBranch} is invalid. Nebula Release plugin does not support branches that contain double dash (-) as it leads to bad build metadata for SemVer." +
                    " Please rename your branch")
        }
        if (currentBranch ==~ /release\/\d+(\.\d+)?/) {
            throw new GradleException('Branches with pattern release/<version> are used to calculate versions. The version must be of form: <major>.x, <major>.<minor>.x, or <major>.<minor>.<patch>')
        }
    }
}
