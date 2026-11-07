package nebula.plugin.release

import nebula.test.IntegrationTestKitSpec
import nebula.test.dependencies.Coordinate
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.DependencyGraphNode
import nebula.test.dependencies.GradleDependencyGenerator
import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion

class SnapshotResolutionIntegrationSpec extends IntegrationTestKitSpec {

    private static versionGraph(String... versions) {
        def builder = new DependencyGraphBuilder()
        versions.each { version ->
            builder.addModule(new DependencyGraphNode(
                    coordinate: new Coordinate(group: 'test', artifact: 'a', version: version),
                    targetCompatibility: JavaVersion.current().majorVersion.toInteger()))
        }
        builder.build()
    }

    private File generateRepository(graph) {
        def generator = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        // These resolution tests need only the JDK running the test, including in CI.
        new File(generator.gradleRoot, 'gradle.properties').text = """\
            org.gradle.java.installations.auto-detect=false
            org.gradle.java.installations.auto-download=false
            org.gradle.java.installations.paths=${System.getProperty('java.home')}
        """.stripIndent()
        // Fail here if publication fails; generateTestMavenRepo() discards its build result.
        GradleRunner.create().withProjectDir(generator.gradleRoot)
                .withArguments('publishMavenPublicationToMavenRepository', '--stacktrace').build()
        generator.mavenRepoDir
    }

    def setup() {
        if (GradleVersion.current().baseVersion < GradleVersion.version("7.0")) {
            settingsFile << "enableFeaturePreview('VERSION_ORDERING_V2')"
        }
        // Enable configuration cache :)
        new File(projectDir, 'gradle.properties') << '''org.gradle.configuration-cache=true'''.stripIndent()
    }

    def 'choose immutableSnapshot version if no release candidate'() {
        def graph = versionGraph('0.0.1', '0.0.2-snapshot.20190708102343+23sd')
        File mavenrepo = generateRepository(graph)

        buildFile << """\
            apply plugin: 'java'
            repositories {
                maven { url = '${mavenrepo.absolutePath}' }
            }


            dependencies {
                implementation 'test:a:0.+'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'runtimeClasspath')

        then:
        result.output.contains("test:a:0.+ -> 0.0.2-snapshot.20190708102343+23sd\n")
    }

    def 'choose devSnapshot version if no release candidate'() {
        def graph = versionGraph('0.0.1', '0.0.2-dev.1+23sd')
        File mavenrepo = generateRepository(graph)

        buildFile << """\
            apply plugin: 'java'
            repositories {
                maven { url = '${mavenrepo.absolutePath}' }
            }


            dependencies {
                implementation 'test:a:0.+'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'runtimeClasspath')

        then:
        result.output.contains("test:a:0.+ -> 0.0.2-dev.1+23sd\n")
    }

    def 'choose immutable version instead of candidate if both is present'() {
        def graph = versionGraph('0.0.1', '0.0.2-rc.1', '0.0.2-snapshot.20190708102343+23sd')
        File mavenrepo = generateRepository(graph)

        buildFile << """\
            apply plugin: 'java'
            repositories {
                maven { url = '${mavenrepo.absolutePath}' }
            }


            dependencies {
                implementation 'test:a:0.+'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'runtimeClasspath')

        then:
        result.output.contains("test:a:0.+ -> 0.0.2-snapshot.20190708102343+23sd\n")
    }

    def 'choose candidate version instead of devSnapshot if release candidate is present'() {
        def graph = versionGraph('0.0.1', '0.0.2-rc.1', '0.0.2-rc.1.dev.1+23sd')
        File mavenrepo = generateRepository(graph)

        buildFile << """\
            apply plugin: 'java'
            repositories {
                maven { url = '${mavenrepo.absolutePath}' }
            }


            dependencies {
                implementation 'test:a:0.+'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies', '--configuration', 'runtimeClasspath')

        then:
        result.output.contains("test:a:0.+ -> 0.0.2-rc.1\n")
    }
}
