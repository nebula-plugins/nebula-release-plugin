/*
 * Copyright 2014-2017 Netflix, Inc.
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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault
class ReleaseCheck extends DefaultTask {
    @Input
    String branchName
    @Input
    ReleaseExtension patterns
    @Input
    boolean isSnapshotRelease

    @TaskAction
    void check() {
        if (patterns.allowReleaseFromDetached) {
            return
        }
        boolean includeMatch = patterns.releaseBranchPatterns.isEmpty()

        patterns.releaseBranchPatterns.each { String pattern ->
            if (getBranchName() ==~ pattern) includeMatch = true
        }

        boolean excludeMatch = false
        patterns.excludeBranchPatterns.each { String pattern ->
            if (getBranchName() ==~ pattern) excludeMatch = true
        }

        if (!includeMatch && !isSnapshotRelease) {
            String message = "Branch ${getBranchName()} does not match one of the included patterns: ${patterns.releaseBranchPatterns}"
            logger.error(message)
            throw new GradleException(message)
        }

        if (excludeMatch) {
            String message = "Branch ${getBranchName()} matched an excluded pattern: ${patterns.excludeBranchPatterns}"
            logger.error(message)
            throw new GradleException(message)
        }
    }
}
