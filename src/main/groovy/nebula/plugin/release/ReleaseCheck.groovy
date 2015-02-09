package nebula.plugin.release

import org.ajoberstar.grgit.Grgit
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

class ReleaseCheck extends DefaultTask {
    Grgit grgit
    ReleaseExtension patterns
    boolean isSnapshotRelease

    @TaskAction
    void check() {
        String branchName = grgit.branch.current.name

        boolean includeMatch = patterns.releaseBranchPatterns.isEmpty()

        patterns.releaseBranchPatterns.each { String pattern ->
            if (branchName ==~ pattern) includeMatch = true
        }

        boolean excludeMatch = false
        patterns.excludeBranchPatterns.each { String pattern ->
            if (branchName ==~ pattern) excludeMatch = true
        }

        if (!includeMatch && !isSnapshotRelease) {
            String message = "${branchName} does not match one of the included patterns: ${patterns.releaseBranchPatterns}"
            logger.error(message)
            throw new GradleException(message)
        }

        if (excludeMatch) {
            String message = "${branchName} matched an excluded pattern: ${patterns.excludeBranchPatterns}"
            logger.error(message)
            throw new GradleException(message)
        }
    }
}
