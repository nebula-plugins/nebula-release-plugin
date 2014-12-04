package nebula.plugins.release

import nebula.core.ProjectType
import org.ajoberstar.gradle.git.release.base.ReleasePluginExtension
import org.ajoberstar.gradle.git.release.base.BaseReleasePlugin
import org.ajoberstar.grgit.Grgit
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin

class ReleasePlugin implements Plugin<Project> {
    Project project

    static final String SNAPSHOT_TASK_NAME = "snapshot"
    static final String DEV_SNAPSHOT_TASK_NAME = "devSnapshot"
    static final String CANDIDATE_TASK_NAME = "candidate"
    static final String FINAL_TASK_NAME = "final"
    static final String RELEASE_CHECK_TASK_NAME = "releaseCheck"
    static final String NEBULA_RELEASE_EXTENSION_NAME = "nebulaRelease"
    static final String GROUP = "Nebula Release"

    @Override
    void apply(Project project) {
        this.project = project

        ProjectType type = new ProjectType(project)
        project.plugins.apply(BaseReleasePlugin)
        def releaseExtension = project.extensions.findByType(ReleasePluginExtension)
        releaseExtension.with {
            versionStrategy OverrideStrategies.SYSTEM_PROPERTY
            versionStrategy NetflixOssStrategies.SNAPSHOT
            versionStrategy NetflixOssStrategies.DEVELOPMENT
            versionStrategy NetflixOssStrategies.PRE_RELEASE
            versionStrategy NetflixOssStrategies.FINAL
            defaultVersionStrategy = NetflixOssStrategies.DEVELOPMENT
        }

        if (type.isRootProject) {

            releaseExtension.with {
                grgit = Grgit.open(project.projectDir)
                tagStrategy {
                    generateMessage = { version ->
                        StringBuilder builder = new StringBuilder()
                        builder << "Release of ${version.version}\n\n"

                        String previousVersion = "v${version.previousVersion}^{commit}"
                        List excludes = []
                        if (tagExists(grgit, previousVersion)) {
                            excludes << previousVersion
                        }
                        grgit.log(
                            includes: ["HEAD"],
                            excludes: excludes
                        ).inject(builder) { bldr, commit ->
                            bldr << "- ${commit.id}: ${commit.shortMessage}\n"
                        }
                        builder.toString()
                    }
                }
            }

            def nebulaReleaseExtension = project.extensions.create(NEBULA_RELEASE_EXTENSION_NAME, ReleaseExtension)

            def releaseCheck = project.tasks.create(RELEASE_CHECK_TASK_NAME, ReleaseCheck)
            releaseCheck.group = GROUP
            releaseCheck.grgit = releaseExtension.grgit
            releaseCheck.patterns = nebulaReleaseExtension

            def snapshotTask = project.task(SNAPSHOT_TASK_NAME)
            def devSnapshotTask = project.task(DEV_SNAPSHOT_TASK_NAME)
            def candidateTask = project.task(CANDIDATE_TASK_NAME)
            def finalTask = project.task(FINAL_TASK_NAME)

            [snapshotTask, devSnapshotTask, candidateTask, finalTask].each {
                it.group = GROUP
                it.finalizedBy project.tasks.release
                it.dependsOn releaseCheck
            }

            def cliTasks = project.gradle.startParameter.taskNames
            def hasSnapshot = cliTasks.contains(SNAPSHOT_TASK_NAME)
            def hasDevSnapshot = cliTasks.contains(DEV_SNAPSHOT_TASK_NAME)
            def hasCandidate = cliTasks.contains(CANDIDATE_TASK_NAME)
            def hasFinal = cliTasks.contains(FINAL_TASK_NAME)
            if ([hasSnapshot, hasDevSnapshot, hasCandidate, hasFinal].count { it } > 2) {
                throw new GradleException("Only one of snapshot, devSnapshot, candidate, or final can be specified.")
            }

            if (hasFinal) {
                applyReleaseStage("final")
            } else if (hasCandidate) {
                applyReleaseStage("rc")
            } else if (hasDevSnapshot) {
                applyReleaseStage("dev")
            } else {
                applyReleaseStage("SNAPSHOT")
            }
        } else {
            releaseExtension.grgit = Grgit.open(project.rootProject.projectDir)    
        }

        if (type.isLeafProject) {
            project.plugins.withType(JavaPlugin) {
                project.rootProject.tasks.release.dependsOn project.tasks.build
            }
        }
    }

    void applyReleaseStage(String stage) {
        final String releaseStage = "release.stage"
        project.allprojects.each { it.ext.set(releaseStage, stage) }
    }

    private boolean tagExists(Grgit grgit, String revStr) {
        try {
            grgit.resolve.toCommit(revStr)
            return true
        } catch (e) {
            return false
        }
    }
}
