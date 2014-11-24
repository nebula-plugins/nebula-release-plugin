package nebula.plugins.release

import nebula.core.ProjectType
import org.ajoberstar.gradle.git.release.base.ReleasePluginExtension
import org.ajoberstar.gradle.git.release.base.BaseReleasePlugin
import org.ajoberstar.gradle.git.release.semver.SemVerStrategy
import org.ajoberstar.grgit.Grgit
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin

class ReleasePlugin implements Plugin<Project> {
    Project project

    static final String SNAPSHOT_TASK_NAME = "snapshot"
    static final String CANDIDATE_TASK_NAME = "candidate"
    static final String FINAL_TASK_NAME = "final"
    static final String GROUP = "Nebula Release"

    @Override
    void apply(Project project) {
        this.project = project

        ProjectType type = new ProjectType(project)
        project.plugins.apply(BaseReleasePlugin)
        def releaseExtension = project.extensions.findByType(ReleasePluginExtension)
        releaseExtension.with {
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
                        builder << 'Release of '
                        builder << version.version
                        builder << '\n\n'

                        String previousVersion = "v${version.previousVersion}^{commit}"
                        List excludes = []
                        if (tagExists(grgit, previousVersion)) {
                            excludes << previousVersion
                        }
                        grgit.log(
                            includes: ['HEAD'],
                            excludes: excludes
                        ).inject(builder) { bldr, commit ->
                            bldr << '- '
                            bldr << commit.shortMessage
                            bldr << '\n'
                        }
                        builder.toString()
                    }
                }
            }

            def snapshotTask = project.task(SNAPSHOT_TASK_NAME)
            snapshotTask.group = GROUP
            snapshotTask.finalizedBy "release"

            def candidateTask = project.task(CANDIDATE_TASK_NAME)
            snapshotTask.group = GROUP
            candidateTask.finalizedBy "release"

            def finalTask = project.task(FINAL_TASK_NAME)
            snapshotTask.group = GROUP
            finalTask.finalizedBy "release"

            def cliTasks = project.gradle.startParameter.taskNames
            def hasSnapshot = cliTasks.contains(SNAPSHOT_TASK_NAME)
            def hasCandidate = cliTasks.contains(CANDIDATE_TASK_NAME)
            def hasFinal = cliTasks.contains(FINAL_TASK_NAME)
            if ([hasSnapshot, hasCandidate, hasFinal].count { it } > 2) {
                throw new GradleException("Only one of snapshot, candidate, or final can be specified.")
            }

            if (hasFinal) {
                applyReleaseStage("final")
            } else if (hasCandidate) {
                applyReleaseStage("rc")
            } else {
                applyReleaseStage("dev")
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
        project.ext.set(releaseStage, stage)
        project.subprojects.each { it.ext.set(releaseStage, stage) }
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
