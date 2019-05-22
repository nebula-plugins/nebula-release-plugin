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

import nebula.plugin.release.git.base.ReleasePluginExtension
import nebula.plugin.release.git.base.ReleaseVersion
import nebula.plugin.release.git.base.VersionStrategy
import nebula.plugin.release.git.semver.NearestVersionLocator
import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.Tag
import org.eclipse.jgit.api.errors.RefNotFoundException
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Strategies for setting the version externally from the build.
 */
class OverrideStrategies {

    static class ReleaseLastTagStrategy implements VersionStrategy {
        static final String PROPERTY_NAME = 'release.useLastTag'
        private static final String NOT_SUPPLIED = 'release-strategy-is-not-supplied'
        private static final Logger logger = LoggerFactory.getLogger(ReleaseLastTagStrategy)

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
                project.tasks.release.enabled = false // remove tagging op since already tagged
            }

            shouldSelect
        }

        @Override
        ReleaseVersion infer(Project project, Grgit grgit) {
            def tagStrategy = project.extensions.getByType(ReleasePluginExtension).tagStrategy
            def locate = new NearestVersionLocator(tagStrategy).locate(grgit)
            String releaseStage
            try {
                releaseStage = project.ext['release.stage']
            } catch(MissingPropertyException e) {
                releaseStage = NOT_SUPPLIED
                logger.debug("ExtraPropertiesExtension 'release.stage' was not supplied", e.getMessage())
                logger.info("Note: It is recommended to supply a release strategy of <snapshot|devSnapshot|candidate|final> to make 'useLastTag' most explicit. Please add one to your list of tasks.")
            }

            if (releaseStage == 'dev' || releaseStage == 'SNAPSHOT') {
                throw new GradleException("Cannot use useLastTag with snapshot and devSnapshot tasks")
            }

            if (locate.distanceFromAny == 0) {
                if(releaseStage == NOT_SUPPLIED && (locate.any.toString().contains('-dev.') || locate.any.toString().contains('-SNAPSHOT'))) {
                    throw new GradleException("Current commit has a snapshot or devSnapshot tag. 'useLastTag' requires a prerelease or final tag.")
                }

                def preReleaseVersion = locate.any.preReleaseVersion
                if (releaseStage == 'rc') {
                    if (!(preReleaseVersion ==~ /rc\.\d+/)) {
                        throw new GradleException("Current tag does not appear to be a prerelease version")
                    }
                }
                if (releaseStage == 'final') {
                    if (preReleaseVersion) {
                        throw new GradleException("Current tag does not appear to be a final version")
                    }
                }

                logger.debug("Using version ${locate.any.toString()} with ${releaseStage == NOT_SUPPLIED ? "a non-supplied release strategy" : "${releaseStage} release strategy"}")
                return new ReleaseVersion(locate.any.toString(), null, false)
            } else {
                List<Tag> headTags = grgit.tag.list().findAll { it.commit == grgit.head()}
                if (headTags.isEmpty()) {
                    throw new GradleException("Current commit does not have a tag")
                } else {
                    throw new GradleException("Current commit has following tags: ${headTags.collect{it.name}} but they were not recognized as valid versions" )
                }
            }
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
            def requestedVersion = project.property(propertyName)
            if (requestedVersion == null || requestedVersion.isEmpty()) {
                throw new GradleException('Supplied release.version is empty')
            }
            new ReleaseVersion(requestedVersion, null, true)
        }
    }

    static class NoCommitStrategy implements VersionStrategy {
        @Override
        String getName() {
            'no-commit'
        }

        @Override
        boolean selector(Project project, Grgit grgit) {
            try {
                grgit.describe()
            } catch (RefNotFoundException ignore) {
                return true
            }

            return false
        }

        @Override
        ReleaseVersion infer(Project project, Grgit grgit) {
            new ReleaseVersion('0.1.0-dev.0.uncommitted', null, false)
        }
    }
}
