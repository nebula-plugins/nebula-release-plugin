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

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input

import javax.inject.Inject

abstract class ReleaseExtension {
    @Input
    abstract SetProperty<String> getReleaseBranchPatterns()

    @Input
    abstract SetProperty<String> getExcludeBranchPatterns()

    /**
     * This should be a regex pattern with one(1) capture group. By default shortens the typical
     * {bugfix|feature|hotfix|release}/branch-name to branch-name. The prefix is optional and a
     * dash may be used instead of the forward slash.
     */
    @Input
    abstract Property<String> getShortenedBranchPattern()

    @Input
    abstract Property<Boolean> getAllowReleaseFromDetached()

    @Input
    abstract Property<Boolean> getCheckRemoteBranchOnRelease()

    @Inject
    ReleaseExtension(ObjectFactory objects) {
        releaseBranchPatterns.convention([/master/, /HEAD/, /main/, /(release(-|\/))?\d+(\.\d+)?\.x/, /v?\d+\.\d+\.\d+/] as Set<String>)
        excludeBranchPatterns.convention([] as Set<String>)
        shortenedBranchPattern.convention(/(?:(?:bugfix|feature|hotfix|release)(?:-|\/))?(.+)/)
        allowReleaseFromDetached.convention(false)
        checkRemoteBranchOnRelease.convention(false)
    }

    void addReleaseBranchPattern(String pattern) {
        releaseBranchPatterns.add(pattern)
    }

    void addExcludeBranchPattern(String pattern) {
        excludeBranchPatterns.add(pattern)
    }
}
