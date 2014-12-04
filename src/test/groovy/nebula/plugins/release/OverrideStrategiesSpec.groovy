package nebula.plugins.release

import nebula.test.ProjectSpec
import org.ajoberstar.gradle.git.release.base.BaseReleasePlugin
import org.ajoberstar.gradle.git.release.base.ReleasePluginExtension
import org.ajoberstar.grgit.Grgit
import java.nio.file.Files

class OverrideStrategiesSpec extends ProjectSpec {
    def "able to set via gradle property"() {
        setup:
        project.ext.set("release.version", "42.5.0")

        when:
        project.plugins.apply(BaseReleasePlugin)
        def releaseExtension = project.extensions.findByType(ReleasePluginExtension)
        releaseExtension.with {
            versionStrategy new OverrideStrategies.GradlePropertyStrategy(project, "release.version")
        }

        then:
        project.version.toString() == '42.5.0'
    }
}
