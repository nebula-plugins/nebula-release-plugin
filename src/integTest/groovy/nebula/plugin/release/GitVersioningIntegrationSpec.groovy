package nebula.plugin.release

import com.github.zafarkhaja.semver.Version
import nebula.test.IntegrationSpec
import org.ajoberstar.grgit.Grgit

import java.nio.file.Files

abstract class GitVersioningIntegrationSpec extends IntegrationSpec {
    protected Grgit git
    protected Grgit originGit

    def setup() {
        def origin = new File(projectDir.parent, "${projectDir.name}.git")
        origin.mkdirs()

        ['build.gradle', 'settings.gradle'].each {
            Files.move(new File(projectDir, it).toPath(), new File(origin, it).toPath())
        }

        originGit = Grgit.init(dir: origin)
        originGit.add(patterns: ['build.gradle', 'settings.gradle', '.gitignore'] as Set)
        originGit.commit(message: 'Initial checkout')

        git = Grgit.clone(dir: projectDir, uri: origin.absolutePath) as Grgit

        new File(projectDir, '.gitignore') << '''
            .gradle-test-kit
            .gradle
            build/
        '''.stripIndent()

        setupBuild()

        git.commit(message: 'Setup')
        git.push()
    }

    abstract def setupBuild()

    def cleanup() {
        if (git) git.close()
        if (originGit) originGit.close()
    }

    def Version normal(String version) {
        Version.valueOf(version)
    }

    def Version dev(String version) {
        normal("${version}${git.head().abbreviatedId}")
    }

    def Version inferredVersionForTask(String... args) {
        def result = runTasksSuccessfully(args)
        inferredVersion(result.standardOutput)
    }

    def Version inferredVersion(String standardOutput) {
        def matcher = standardOutput =~ /Inferred project: (.*), version: (.*)/
        if (matcher.size() > 0) {
            def project = matcher[0][1] as String
            def version = matcher[0][2] as String
            assert project == moduleName
            normal(version)
        } else {
            throw new IllegalArgumentException("Could not find inferred version using $matcher")
        }
    }
}
