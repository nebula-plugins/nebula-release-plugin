/*
 * Copyright 2014-2015 Netflix, Inc.
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

import org.ajoberstar.gradle.git.release.base.ReleaseVersion
import org.ajoberstar.gradle.git.release.base.VersionStrategy
import org.ajoberstar.gradle.git.release.semver.NearestVersionLocator
import org.ajoberstar.grgit.Grgit
import org.gradle.api.Project

/**
 * Strategies for setting the version externally from the build.
 */
class OverrideStrategies {

    static class ReleaseLastTagStrategy implements VersionStrategy {
        static final String PROPERTY_NAME = 'release.useLastTag'

        Project project
        String propertyName

        ReleaseLastTagStrategy(Project project, String propertyName = PROPERTY_NAME) {
            this.project = project
            this.propertyName = propertyName
        }

        @Override
        String getName() {
            return 'use-last-tag'
        }

        @Override
        boolean selector(Project project, Grgit grgit) {
            def shouldSelect = project.hasProperty(propertyName) ? project.property(propertyName).toBoolean() : false

            if (shouldSelect) {
                project.tasks.release.deleteAllActions() // remove tagging op since already tagged
            }

            shouldSelect
        }

        @Override
        ReleaseVersion infer(Project project, Grgit grgit) {
            def locate = new NearestVersionLocator().locate(grgit)
            return new ReleaseVersion(locate.getNormal().normalVersion, null, false)
        }
    }

    static class GradlePropertyStrategy implements VersionStrategy {
        static final String PROPERTY_NAME = 'release.version'
        Project project
        String propertyName

        GradlePropertyStrategy(Project project, String propertyName = PROPERTY_NAME) {
            this.project = project
            this.propertyName = propertyName
        }

        @Override
        String getName() {
            'gradle-properties'
        }

        @Override
        boolean selector(Project project, Grgit grgit) {
            project.hasProperty(propertyName)
        }

        @Override
        ReleaseVersion infer(Project project, Grgit grgit) {
            new ReleaseVersion(project.property(propertyName), null, true)
        }
    }
}
