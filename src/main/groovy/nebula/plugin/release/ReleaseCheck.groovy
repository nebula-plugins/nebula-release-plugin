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
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault
abstract class ReleaseCheck extends DefaultTask {
    @Input
    abstract Property<String> getBranchName()

    @Nested
    abstract Property<ReleaseExtension> getPatterns()

    @Input
    abstract Property<Boolean> getIsSnapshotRelease()

    @TaskAction
    void check() {
        ReleaseExtension patternsValue = patterns.get()
        String branch = branchName.get()
        boolean isSnapshot = isSnapshotRelease.get()

        if (patternsValue.allowReleaseFromDetached.get()) {
            return
        }
        boolean includeMatch = patternsValue.releaseBranchPatterns.get().isEmpty()

        patternsValue.releaseBranchPatterns.get().each { String pattern ->
            if (branch ==~ pattern) includeMatch = true
        }

        boolean excludeMatch = false
        patternsValue.excludeBranchPatterns.get().each { String pattern ->
            if (branch ==~ pattern) excludeMatch = true
        }

        if (!includeMatch && !isSnapshot) {
            String message = "Branch ${branch} does not match one of the included patterns: ${patternsValue.releaseBranchPatterns.get()}"
            logger.error(message)
            throw new GradleException(message)
        }

        if (excludeMatch) {
            String message = "Branch ${branch} matched an excluded pattern: ${patternsValue.excludeBranchPatterns.get()}"
            logger.error(message)
            throw new GradleException(message)
        }
    }
}
