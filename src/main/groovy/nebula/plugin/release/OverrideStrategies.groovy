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

import com.github.zafarkhaja.semver.UnexpectedCharacterException
import com.github.zafarkhaja.semver.Version
import groovy.transform.CompileDynamic
import nebula.plugin.release.git.GitBuildService
import nebula.plugin.release.git.base.ReleasePluginExtension
import nebula.plugin.release.git.base.ReleaseVersion
import nebula.plugin.release.git.base.VersionStrategy
import nebula.plugin.release.git.model.TagRef
import nebula.plugin.release.git.opinion.TimestampPrecision
import nebula.plugin.release.git.opinion.TimestampUtil
import nebula.plugin.release.git.semver.NearestVersionLocator
import nebula.plugin.release.util.ReleaseTasksUtil
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Strategies for setting the version externally from the build.
 */
class OverrideStrategies {


    static class ReleaseLastTagStrategy implements VersionStrategy {
        private static final String NOT_SUPPLIED = 'release-strategy-is-not-supplied'
        private static final Logger logger = LoggerFactory.getLogger(ReleaseLastTagStrategy)

        Project project
        String propertyName

        ReleaseLastTagStrategy(Project project, String propertyName = ReleaseTasksUtil.USE_LAST_TAG_PROPERTY) {
            this.project = project
            this.propertyName = propertyName
        }

        @Override
        String getName() {
            return 'use-last-tag'
        }

        @Override
        boolean selector(Project project, GitBuildService gitBuildService) {
            def shouldSelect = project.hasProperty(propertyName) ? project.property(propertyName).toString().toBoolean() : false

            if (shouldSelect) {
                project.tasks.getByName('release').enabled = false // remove tagging op since already tagged
            }

            shouldSelect
        }


        @CompileDynamic
        @Override
        ReleaseVersion infer(Project project, GitBuildService gitBuildService) {
            def tagStrategy = project.extensions.getByType(ReleasePluginExtension).tagStrategy
            def locate = new NearestVersionLocator(gitBuildService, tagStrategy).locate()
            String releaseStage
            try {
                releaseStage = project.property('release.stage')
            } catch(MissingPropertyException e) {
                releaseStage = NOT_SUPPLIED
                logger.debug("ExtraPropertiesExtension 'release.stage' was not supplied", e.getMessage())
                logger.info("Note: It is recommended to supply a release strategy of <snapshot|immutableSnapshot|devSnapshot|candidate|final> to make 'useLastTag' most explicit. Please add one to your list of tasks.")
            }

            if (releaseStage == 'dev' ||  releaseStage == 'snapshot' || releaseStage == 'SNAPSHOT') {
                throw new GradleException("Cannot use useLastTag with snapshot, immutableSnapshot and devSnapshot tasks")
            }

            if (locate.distanceFromAny == 0) {
                if(releaseStage == NOT_SUPPLIED && (locate.any.toString().contains('-dev.') || locate.any.toString().contains('-SNAPSHOT')  || locate.any.toString().contains('-snapshot.') )) {
                    throw new GradleException("Current commit has a snapshot, immutableSnapshot or devSnapshot tag. 'useLastTag' requires a prerelease or final tag.")
                }

                Version version = locate.any
                def preReleaseVersion = version.preReleaseVersion
                if (releaseStage == 'rc') {
                    if (!(preReleaseVersion ==~ /rc\.\d+/)) {
                        throw new GradleException("Current tag ($version) does not appear to be a pre-release version. A pre-release version MAY be denoted by appending a hyphen and a series of dot separated identifiers immediately following the patch version. For more information, please refer to https://semver.org/")
                    }
                }
                if (releaseStage == 'final') {
                    if (preReleaseVersion) {
                        throw new GradleException("Current tag ($version) does not appear to be a final version. final task can not be used with prerelease versions. A pre-release version MAY be denoted by appending a hyphen and a series of dot separated identifiers immediately following the patch version.  For more information, please refer to https://semver.org/")
                    }
                }

                String inferredVersion = locate.any.toString()
                if(VersionSanitizerUtil.hasSanitizeFlag(project)) {
                    inferredVersion = VersionSanitizerUtil.sanitize(inferredVersion)
                }

                logger.debug("Using version ${inferredVersion} with ${releaseStage == NOT_SUPPLIED ? "a non-supplied release strategy" : "${releaseStage} release strategy"}")
                return new ReleaseVersion(inferredVersion, null, false)
            } else {
                List<TagRef> headTags = gitBuildService.headTags()
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
        static final String VERSION_VERIFICATION_PROPERTY_NAME = 'release.ignoreSuppliedVersionVerification'

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
        boolean selector(Project project, GitBuildService gitBuildService) {
            project.hasProperty(propertyName)
        }


        @Override
        ReleaseVersion infer(Project project, GitBuildService gitBuildService) {
            String requestedVersion = project.property(propertyName).toString()
            if (requestedVersion == null || requestedVersion.isEmpty()) {
                throw new GradleException('Supplied release.version is empty')
            }

            if(VersionSanitizerUtil.hasSanitizeFlag(project)) {
                requestedVersion = VersionSanitizerUtil.sanitize(requestedVersion.toString())
            }

            boolean isValidVersion = validateRequestedVersion(requestedVersion)
            if(!isValidVersion) {
                throw new GradleException("Supplied release.version ($requestedVersion) is not valid per semver spec. For more information, please refer to https://semver.org/")
            }

            new ReleaseVersion(requestedVersion, null, true)
        }

        private boolean validateRequestedVersion(String version) {
            if(project.hasProperty(VERSION_VERIFICATION_PROPERTY_NAME)) {
                return true
            }

            try {
                Version.valueOf(version[0] == 'v' ? version[1..-1] : version)
                return true
            } catch(UnexpectedCharacterException e) {
                return false
            }
        }

    }

    static class NoCommitStrategy implements VersionStrategy {
        @Override
        String getName() {
            'no-commit'
        }

        @Override
        boolean selector(Project project, GitBuildService gitBuildService) {
            return !gitBuildService.hasCommit()
        }

        @Override
        ReleaseVersion infer(Project project, GitBuildService gitBuildService) {
            boolean replaceDevSnapshots = FeatureFlags.isDevSnapshotReplacementEnabled(project)
            if(replaceDevSnapshots) {
                TimestampPrecision immutableSnapshotTimestampPrecision = FeatureFlags.immutableSnapshotTimestampPrecision(project)
                new ReleaseVersion("0.1.0-snapshot.${TimestampUtil.getUTCFormattedTimestamp(immutableSnapshotTimestampPrecision)}.uncommitted", null, false)
            } else {
                new ReleaseVersion('0.1.0-dev.0.uncommitted', null, false)
            }
        }
    }

}
