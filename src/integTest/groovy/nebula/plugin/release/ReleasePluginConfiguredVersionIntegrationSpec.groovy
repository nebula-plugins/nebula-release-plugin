package nebula.plugin.release

class ReleasePluginConfiguredVersionIntegrationSpec extends GitVersioningIntegrationTestKitSpec {

    @Override
    def setupBuild() {
        buildFile << """
            plugins {
                id 'com.netflix.nebula.release'
            }
            allprojects {
                apply plugin: 'com.netflix.nebula.release'
            }

            subprojects {
                ext.dryRun = true
                group = 'test'
                apply plugin: 'java'
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
        def results = runTasksAndFail('final')

        then:
        results.output.contains('version should not be set in build file when using nebula-release plugin. Instead use `-Prelease.version` parameter')
    }

}