package nebula.plugin.release

import nebula.test.IntegrationTestKitSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator

class SnapshotResolutionIntegrationSpec extends IntegrationTestKitSpec {

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
                compile 'test:a:0.+'
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
                compile 'test:a:0.+'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies')

        then:
        result.output.contains("test:a:0.+ -> 0.0.2-dev.1+23sd")
    }

    def 'choose candidate version instead of immutable if release candidate is present'() {
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
                compile 'test:a:0.+'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies')

        then:
        result.output.contains("test:a:0.+ -> 0.0.2-rc.1")
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
                compile 'test:a:0.+'
            }
        """.stripIndent()

        when:
        def result = runTasks('dependencies')

        then:
        result.output.contains("test:a:0.+ -> 0.0.2-rc.1")
    }
}
