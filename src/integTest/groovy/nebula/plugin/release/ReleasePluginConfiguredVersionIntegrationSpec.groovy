package nebula.plugin.release

import org.gradle.api.plugins.JavaPlugin

class ReleasePluginConfiguredVersionIntegrationSpec extends GitVersioningIntegrationSpec {

    @Override
    def setupBuild() {
        buildFile << """
            allprojects {
                ${applyPlugin(ReleasePlugin)}
            }

            subprojects {
                ext.dryRun = true
                group = 'test'
                ${applyPlugin(JavaPlugin)}
            }

            version = '1.0.0'
        """

        addSubproject('test-release-common', '// hello')
        addSubproject('test-release-client', '''\
            dependencies {
                implementation project(':test-release-common')
            }
        '''.stripIndent())

        git.tag.add(name: 'v0.0.1')
        git.commit(message: 'Another commit')
        git.add(patterns: ['build.gradle', '.gitignore', 'settings.gradle',
                           'test-release-common/build.gradle', 'test-release-client/build.gradle'] as Set)
    }

    def 'should fail build if version is set in build file'() {
        when:
        def results = runTasksWithFailure('final')

        then:
        results.standardError.contains('version should not be set in build file when using nebula-release plugin. Instead use `-Prelease.version` parameter')
    }

}