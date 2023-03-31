/*
 * Copyright 2012-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nebula.plugin.release.git.semver

import groovy.transform.CompileDynamic
import nebula.plugin.release.git.GitOps
import nebula.plugin.release.git.base.ReleasePluginExtension
import nebula.plugin.release.git.base.ReleaseVersion
import nebula.plugin.release.git.base.VersionStrategy
import org.ajoberstar.grgit.Commit
import org.ajoberstar.grgit.Grgit

import org.gradle.api.Project
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Strategy that infers the version based on the tag on the current
 * HEAD.
 */
@CompileDynamic
class RebuildVersionStrategy implements VersionStrategy {
    private static final Logger logger = LoggerFactory.getLogger(RebuildVersionStrategy)
    public static final RebuildVersionStrategy INSTANCE = new RebuildVersionStrategy()

    private RebuildVersionStrategy() {
        // just hiding the constructor
    }

    @Override
    String getName() {
        return 'rebuild'
    }

    /**
     * Determines whether this strategy should be used to infer the version.
     * <ul>
     * <li>Return {@code false}, if any project properties starting with "release." are set.</li>
     * <li>Return {@code false}, if there aren't any tags on the current HEAD that can be parsed as a version.</li>
     * <li>Return {@code true}, otherwise.</li>
     * </ul>
     */
    @Override
    boolean selector(Project project, GitOps gitOps) {
        def clean = gitOps.isCleanStatus()
        def propsSpecified = project.hasProperty(SemVerStrategy.SCOPE_PROP) || project.hasProperty(SemVerStrategy.STAGE_PROP)
        def headVersion = getHeadVersion(project, gitOps)

        if (clean && !propsSpecified && headVersion) {
            logger.info('Using {} strategy because repo is clean, no "release." properties found and head version is {}', name, headVersion)
            return true
        } else {
            logger.info('Skipping {} strategy because clean is {}, "release." properties are {} and head version is {}', name, clean, propsSpecified, headVersion)
            return false
        }
    }

    /**
     * Infers the version based on the version tag on the current HEAD with the
     * highest precendence.
     */
    @Override
    ReleaseVersion infer(Project project, GitOps gitOps) {
        String version = getHeadVersion(project, gitOps)
        def releaseVersion = new ReleaseVersion(version, version, false)
        logger.debug('Inferred version {} by strategy {}', releaseVersion, name)
        return releaseVersion
    }
    

    private String getHeadVersion(Project project, GitOps gitOps) {
        def tagStrategy = project.extensions.getByType(ReleasePluginExtension).tagStrategy
        return gitOps.headTags().collect {
            tagStrategy.parseTag(it)
        }.findAll {
            it != null
        }.max()?.toString()
    }
}
