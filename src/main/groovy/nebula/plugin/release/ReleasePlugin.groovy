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

import nebula.plugin.release.git.base.BaseReleasePlugin
import nebula.plugin.release.git.base.ReleasePluginExtension
import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.Status
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.ivy.plugins.IvyPublishPlugin
import org.gradle.api.publish.ivy.tasks.GenerateIvyDescriptor
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

class ReleasePlugin implements Plugin<Project> {
    public static final String DISABLE_GIT_CHECKS = 'release.disableGitChecks'
    Project project
    Grgit git
    static Logger logger = Logging.getLogger(ReleasePlugin)

    static final String SNAPSHOT_TASK_NAME = 'snapshot'
    static final String SNAPSHOT_SETUP_TASK_NAME = 'snapshotSetup'
    static final String DEV_SNAPSHOT_TASK_NAME = 'devSnapshot'
    static final String DEV_SNAPSHOT_SETUP_TASK_NAME = 'devSnapshotSetup'
    static final String IMMUTABLE_SNAPSHOT_TASK_NAME = 'immutableSnapshot'
    static final String IMMUTABLE_SNAPSHOT_SETUP_TASK_NAME = 'immutableSnapshotSetup'
    static final String CANDIDATE_TASK_NAME = 'candidate'
    static final String CANDIDATE_SETUP_TASK_NAME = 'candidateSetup'
    static final String FINAL_TASK_NAME = 'final'
    static final String FINAL_SETUP_TASK_NAME = 'finalSetup'
    static final String RELEASE_CHECK_TASK_NAME = 'releaseCheck'
    static final String NEBULA_RELEASE_EXTENSION_NAME = 'nebulaRelease'
    static final String POST_RELEASE_TASK_NAME = 'postRelease'
    static final String GROUP = 'Nebula Release'

    @Override
    void apply(Project project) {
        this.project = project

        def gitRoot = project.hasProperty('git.root') ? project.property('git.root') : project.rootProject.projectDir

        try {
            git = Grgit.open(dir: gitRoot)
        }
        catch(RepositoryNotFoundException e) {
            this.project.version = '0.1.0-dev.0.uncommitted'
            logger.warn("Git repository not found at $gitRoot -- nebula-release tasks will not be available. Use the git.root Gradle property to specify a different directory.")
            return
        }
        checkForBadBranchNames()
        if (project == project.rootProject) {
            project.plugins.apply(BaseReleasePlugin)
            ReleasePluginExtension releaseExtension = project.extensions.findByType(ReleasePluginExtension)

            releaseExtension.with {
                versionStrategy new OverrideStrategies.NoCommitStrategy()
                versionStrategy new OverrideStrategies.ReleaseLastTagStrategy(project)
                versionStrategy new OverrideStrategies.GradlePropertyStrategy(project)
                versionStrategy NetflixOssStrategies.SNAPSHOT(project)
                versionStrategy NetflixOssStrategies.IMMUTABLE_SNAPSHOT(project)
                versionStrategy NetflixOssStrategies.DEVELOPMENT(project)
                versionStrategy NetflixOssStrategies.PRE_RELEASE(project)
                versionStrategy NetflixOssStrategies.FINAL(project)
                defaultVersionStrategy = NetflixOssStrategies.DEVELOPMENT(project)
            }

            releaseExtension.with {
                grgit = git
                tagStrategy {
                    generateMessage = { version ->
                        StringBuilder builder = new StringBuilder()
                        builder << "Release of ${version.version}\n\n"

                        if (version.previousVersion) {
                            String previousVersion = "v${version.previousVersion}^{commit}"
                            List excludes = []
                            if (tagExists(grgit, previousVersion)) {
                                excludes << previousVersion
                            }
                            grgit.log(
                                    includes: ['HEAD'],
                                    excludes: excludes
                            ).inject(builder) { bldr, commit ->
                                bldr << "- ${commit.id}: ${commit.shortMessage}\n"
                            }
                        }
                        builder.toString()
                    }
                }
            }

            def nebulaReleaseExtension = project.extensions.create(NEBULA_RELEASE_EXTENSION_NAME, ReleaseExtension)
            NetflixOssStrategies.BuildMetadata.nebulaReleaseExtension = nebulaReleaseExtension

            def releaseCheck = project.tasks.create(RELEASE_CHECK_TASK_NAME, ReleaseCheck)
            releaseCheck.group = GROUP
            releaseCheck.grgit = releaseExtension.grgit
            releaseCheck.patterns = nebulaReleaseExtension

            def postReleaseTask = project.task(POST_RELEASE_TASK_NAME)
            postReleaseTask.group = GROUP
            postReleaseTask.dependsOn project.tasks.release

            def snapshotSetupTask = project.task(SNAPSHOT_SETUP_TASK_NAME)
            def immutableSnapshotSetupTask = project.task(IMMUTABLE_SNAPSHOT_SETUP_TASK_NAME)
            def devSnapshotSetupTask = project.task(DEV_SNAPSHOT_SETUP_TASK_NAME)
            def candidateSetupTask = project.task(CANDIDATE_SETUP_TASK_NAME)
            candidateSetupTask.doLast {
                project.allprojects.each { it.status = 'candidate' }
            }
            def finalSetupTask = project.task(FINAL_SETUP_TASK_NAME)
            finalSetupTask.doLast {
                project.allprojects.each { it.status = 'release' }
            }

            [snapshotSetupTask, immutableSnapshotSetupTask, devSnapshotSetupTask, candidateSetupTask, finalSetupTask].each {
                it.group = GROUP
                it.dependsOn releaseCheck
            }

            def snapshotTask = project.task(SNAPSHOT_TASK_NAME)
            snapshotTask.dependsOn snapshotSetupTask
            def immutableSnapshotTask = project.task(IMMUTABLE_SNAPSHOT_TASK_NAME)
            immutableSnapshotTask.dependsOn immutableSnapshotSetupTask
            def devSnapshotTask = project.task(DEV_SNAPSHOT_TASK_NAME)
            devSnapshotTask.dependsOn devSnapshotSetupTask
            def candidateTask = project.task(CANDIDATE_TASK_NAME)
            candidateTask.dependsOn candidateSetupTask
            def finalTask = project.task(FINAL_TASK_NAME)
            finalTask.dependsOn finalSetupTask

            [snapshotTask, immutableSnapshotTask, devSnapshotTask, candidateTask, finalTask].each {
                it.group = GROUP
                it.dependsOn postReleaseTask
            }

            def cliTasks = project.gradle.startParameter.taskNames
            determineStage(nebulaReleaseExtension, cliTasks, releaseCheck)
            checkStateForStage()

            if (shouldSkipGitChecks()) {
                removeReleaseAndPrepLogic(project)
            }
        } else {
            project.version = project.rootProject.version
        }

        def isParent = project.rootProject.subprojects.any { it.parent == project }
        if (!isParent) {
            project.plugins.withType(JavaPlugin) {
                project.rootProject.tasks.release.dependsOn project.tasks.build
            }
        }

        project.gradle.taskGraph.whenReady { g ->
            def tasks = [DEV_SNAPSHOT_TASK_NAME, SNAPSHOT_TASK_NAME].collect { project.getPath() + it }
            if(tasks.any { g.hasTask(it) }) {
                removeReleaseAndPrepLogic(project)
            }
        }

        configurePublishingIfPresent()
        configureBintrayTasksIfPresent()
    }

    private void removeReleaseAndPrepLogic(Project project) {
        project.tasks.release.enabled = false
        project.tasks.prepare.enabled = false
    }

    private void determineStage(ReleaseExtension releaseExtension, List<String> cliTasks, ReleaseCheck releaseCheck) {
        def hasSnapshot = cliTasks.contains(SNAPSHOT_TASK_NAME)
        def hasDevSnapshot = cliTasks.contains(DEV_SNAPSHOT_TASK_NAME)
        def hasImmutableSnapshot = cliTasks.contains(IMMUTABLE_SNAPSHOT_TASK_NAME)
        def hasCandidate = cliTasks.contains(CANDIDATE_TASK_NAME)
        def hasFinal = cliTasks.contains(FINAL_TASK_NAME)
        if ([hasSnapshot, hasImmutableSnapshot, hasDevSnapshot, hasCandidate, hasFinal].count { it } > 2) {
            throw new GradleException('Only one of snapshot, immutableSnapshot, devSnapshot, candidate, or final can be specified.')
        }

        releaseCheck.isSnapshotRelease = hasSnapshot || hasDevSnapshot || (!hasCandidate && !hasFinal)

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
            applyReleaseStage('dev')
        }
    }

    private void checkStateForStage() {
        if (!project.tasks.releaseCheck.isSnapshotRelease) {
            Status status = git.status()
            if (!status.isClean()) {
                String message = new ErrorMessageFormatter().format(status)
                throw new GradleException(message)
            }
        }
    }

    private boolean shouldSkipGitChecks() {
        def disableGit = project.hasProperty(DISABLE_GIT_CHECKS) && project.property(DISABLE_GIT_CHECKS) as Boolean
        def travis = project.hasProperty('release.travisci') && project.property('release.travisci').toBoolean()
        disableGit || travis
    }

    void setupStatus(String status) {
        project.plugins.withType(IvyPublishPlugin) {
            project.publishing {
                publications.withType(IvyPublication) {
                    descriptor.status = status
                }
            }
        }
    }

    void applyReleaseStage(String stage) {
        final String releaseStage = 'release.stage'
        project.allprojects.each { it.ext.set(releaseStage, stage) }
    }

    void configurePublishingIfPresent() {
        project.plugins.withType(MavenPublishPlugin) {
            project.tasks.withType(GenerateMavenPom) { task ->
                project.rootProject.tasks.postRelease.dependsOn(task)
            }
        }

        project.plugins.withType(IvyPublishPlugin) {
            project.tasks.withType(GenerateIvyDescriptor) { task ->
                project.rootProject.tasks.postRelease.dependsOn(task)
            }
        }
    }

    void configureBintrayTasksIfPresent() {

        if (isClassPresent('nebula.plugin.bintray.NebulaBintrayPackageTask')) {
            project.tasks.withType(PublishToMavenRepository) { Task task ->
                project.plugins.withType(JavaPlugin) {
                    task.dependsOn(project.tasks.build)
                }
                project.rootProject.tasks.postRelease.dependsOn(task)
            }
        } else {
            logger.info('Skipping configuration of nebula bintray task since it is not present')
        }

        if (isClassPresent('com.jfrog.bintray.gradle.tasks.BintrayUploadTask')) {
            project.tasks.withType(Class.forName('com.jfrog.bintray.gradle.tasks.BintrayUploadTask')) { Task task ->
                project.plugins.withType(JavaPlugin) {
                    task.dependsOn(project.tasks.build)
                }
                project.rootProject.tasks.postRelease.dependsOn(task)
            }
        } else {
            logger.info('Skipping configuration of bintray task since it is not present')
        }

        if (isClassPresent('org.jfrog.gradle.plugin.artifactory.task.BuildInfoBaseTask')) {
            project.logger.warn 'Please upgrade com.jfrog.artifactory (org.jfrog.buildinfo:build-info-extractor-gradle:) to version 4.6.0 or above'
            project.tasks.withType(Class.forName('org.jfrog.gradle.plugin.artifactory.task.BuildInfoBaseTask')) { Task task ->
                project.plugins.withType(JavaPlugin) {
                    task.dependsOn(project.tasks.build)
                }
                project.rootProject.tasks.postRelease.dependsOn(task)
            }
        } else if(isClassPresent('org.jfrog.gradle.plugin.artifactory.task.ArtifactoryTask')) {
            // JFrog remove BuildInfoBaseTask see https://www.jfrog.com/jira/browse/GAP-281
            project.tasks.withType(Class.forName('org.jfrog.gradle.plugin.artifactory.task.ArtifactoryTask')) { Task task ->
                project.plugins.withType(JavaPlugin) {
                    task.dependsOn(project.tasks.build)
                }
                project.rootProject.tasks.postRelease.dependsOn(task)
            }
        } else {
            logger.info('Skipping configuration of artifactoryPublish task since it is not present')
        }
    }

    private boolean tagExists(Grgit grgit, String revStr) {
        try {
            grgit.resolve.toCommit(revStr)
            return true
        } catch (e) {
            return false
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
        if (git.branch.current.name ==~ /release\/\d+(\.\d+)?/) {
            throw new GradleException('Branches with pattern release/<version> are used to calculate versions. The version must be of form: <major>.x, <major>.<minor>.x, or <major>.<minor>.<patch>')
        }
    }
}
