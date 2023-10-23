package nebula.plugin.release

import nebula.test.IntegrationTestKitSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator

import org.gradle.util.GradleVersion

class SnapshotResolutionIntegrationSpec extends IntegrationTestKitSpec {

    def setup() {
        if (GradleVersion.current().baseVersion < GradleVersion.version("7.0")) {
            settingsFile << "enableFeaturePreview('VERSION_ORDERING_V2')"
        }
        // Enable configuration cache :)
        new File(projectDir, 'gradle.properties') << '''org.gradle.configuration-cache=true'''.stripIndent()
    }

    def 'choose immutableSnapshot version if no release candidate'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test:a:0.0.1')
                .addModule('test:a:0.0.2-snapshot.20190708102343+23sd')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'
            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }


            dependencies {
                implementation 'test:a:0.+'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies')

        then:
        result.output.contains("test:a:0.+ -> 0.0.2-snapshot.20190708102343+23sd")
    }

    def 'choose devSnapshot version if no release candidate'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test:a:0.0.1')
                .addModule('test:a:0.0.2-dev.1+23sd')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'
            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }


            dependencies {
                implementation 'test:a:0.+'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies')

        then:
        result.output.contains("test:a:0.+ -> 0.0.2-dev.1+23sd")
    }

    def 'choose immutable version instead of candidate if both is present'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test:a:0.0.1')
                .addModule('test:a:0.0.2-rc.1')
                .addModule('test:a:0.0.2-snapshot.20190708102343+23sd')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'
            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }


            dependencies {
                implementation 'test:a:0.+'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies')

        then:
        result.output.contains("test:a:0.+ -> 0.0.2-snapshot.")
    }

    def 'choose candidate version instead of devSnapshot if release candidate is present'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test:a:0.0.1')
                .addModule('test:a:0.0.2-rc.1')
                .addModule('test:a:0.0.2-rc.1.dev.1+23sd')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'
            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }


            dependencies {
                implementation 'test:a:0.+'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies')

        then:
        result.output.contains("test:a:0.+ -> 0.0.2-rc.1")
    }
}
