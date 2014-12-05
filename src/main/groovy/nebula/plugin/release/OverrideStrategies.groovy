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
        static final String PROPERTY_NAME = "release.useLastTag"

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
            project.hasProperty(propertyName) ? project.property(propertyName).toBoolean() : false
        }

        @Override
        ReleaseVersion infer(Project project, Grgit grgit) {
            def locate = new NearestVersionLocator().locate(grgit)
            return new ReleaseVersion(locate.getNormal().normalVersion, null, false)
        }
    }

    static class GradlePropertyStrategy implements VersionStrategy {
        static final String PROPERTY_NAME = "release.version"
        Project project
        String propertyName

        GradlePropertyStrategy(Project project, String propertyName = PROPERTY_NAME) {
            this.project = project
            this.propertyName = propertyName
        }

        @Override
        String getName() {
            "gradle-properties"
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
