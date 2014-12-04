package nebula.plugins.release

import org.ajoberstar.gradle.git.release.base.ReleaseVersion
import org.ajoberstar.gradle.git.release.base.VersionStrategy
import org.ajoberstar.grgit.Grgit
import org.gradle.api.Project

/**
 * Strategies for setting the version externally from the build.
 */
class OverrideStrategies {

    static class SystemPropertyStrategy implements VersionStrategy {

        public static final String PROPERTY_NAME = 'release.version'
        @Override
        String getName() {
            return 'system-properties'
        }

        @Override
        boolean selector(Project project, Grgit grgit) {
            System.getProperty(PROPERTY_NAME) as boolean
        }

        @Override
        ReleaseVersion infer(Project project, Grgit grgit) {
            def version = System.getProperty(PROPERTY_NAME)
            //def locate = new NearestVersionLocator().locate(grgit)
            //def previousVersion = locate.normal.normalVersion
            return new ReleaseVersion(version, null, false)
        }
    }

    static final VersionStrategy SYSTEM_PROPERTY = new SystemPropertyStrategy()
}
