package nebula.plugins.release

import nebula.test.ProjectSpec
import org.ajoberstar.gradle.git.release.base.BaseReleasePlugin
import org.ajoberstar.gradle.git.release.base.ReleasePluginExtension
import org.ajoberstar.grgit.Grgit
import java.nio.file.Files

class OverrideStrategiesSpec extends ProjectSpec {

    def cleanup() {
        System.properties.remove(OverrideStrategies.SystemPropertyStrategy.PROPERTY_NAME)
    }

    def 'able to set via system property'() {
        setup:
        System.setProperty('release.version', '1.2.3')

        when:
        project.plugins.apply(BaseReleasePlugin)
        def releaseExtension = project.extensions.findByType(ReleasePluginExtension)
        releaseExtension.with {
            versionStrategy OverrideStrategies.SYSTEM_PROPERTY
        }

        then:
        project.version.toString() == '1.2.3'

    }
}
