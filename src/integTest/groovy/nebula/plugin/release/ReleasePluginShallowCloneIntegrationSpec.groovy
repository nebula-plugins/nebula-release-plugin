package nebula.plugin.release

import nebula.test.IntegrationTestKitSpec
import spock.lang.Subject

@Subject(ReleasePlugin)
class ReleasePluginShallowCloneIntegrationSpec extends IntegrationTestKitSpec {

    def 'shallow clone without unshallow flag produces default dev version'() {
        given:
        def origin = setupOriginRepoWithTag('1.0.0')
        shallowClone(origin, projectDir, 1)
        writeBuildFile()

        when:
        def results = runTasks('devSnapshot')

        then:
        results.output.contains('0.1.0-dev.')
    }

    def 'shallow clone with unshallowEnabled finds correct tag and infers proper version'() {
        given:
        def origin = setupOriginRepoWithTag('1.0.0')
        shallowClone(origin, projectDir, 1)
        writeBuildFile()
        enableUnshallow()

        when:
        def results = runTasks('devSnapshot')

        then:
        results.output.contains('Shallow clone detected: deepening by 30 commits')
        results.output.contains('Found version tag after deepening')
        results.output.contains('1.0.1-dev.')
    }

    def 'shallow clone that already has tag within depth works without deepening'() {
        given:
        def origin = setupOriginRepoWithTagOnHead('2.0.0')
        shallowClone(origin, projectDir, 1)
        writeBuildFile()
        enableUnshallow()

        when:
        def results = runTasks('devSnapshot')

        then:
        !results.output.contains('Shallow clone detected')
        results.output.contains('2.0.0')
    }

    private File setupOriginRepoWithTag(String tagVersion) {
        File origin = new File(projectDir.parent, "${projectDir.name}-origin")
        if (origin.exists()) origin.deleteDir()
        origin.mkdirs()

        git(origin, 'init')
        git(origin, 'checkout', '-b', 'master')
        configureGitUser(origin)

        new File(origin, 'initial.txt').text = 'initial'
        git(origin, 'add', '.')
        git(origin, 'commit', '-m', 'Initial commit')

        // Create a tag
        git(origin, 'tag', '-a', "v${tagVersion}", '-m', "Release ${tagVersion}")

        // Add more commits after the tag so that shallow clone won't see it
        (1..5).each { i ->
            new File(origin, "file${i}.txt").text = "content ${i}"
            git(origin, 'add', '.')
            git(origin, 'commit', '-m', "Commit ${i} after tag")
        }

        return origin
    }

    private File setupOriginRepoWithTagOnHead(String tagVersion) {
        File origin = new File(projectDir.parent, "${projectDir.name}-origin")
        if (origin.exists()) origin.deleteDir()
        origin.mkdirs()

        git(origin, 'init')
        git(origin, 'checkout', '-b', 'master')
        configureGitUser(origin)

        new File(origin, 'initial.txt').text = 'initial'
        git(origin, 'add', '.')
        git(origin, 'commit', '-m', 'Initial commit')

        // Tag on the latest commit (HEAD)
        git(origin, 'tag', '-a', "v${tagVersion}", '-m', "Release ${tagVersion}")

        return origin
    }

    private void shallowClone(File origin, File targetDir, int depth) {
        // Clean the target dir but keep it existing (IntegrationTestKitSpec needs it)
        targetDir.listFiles()?.each {
            if (it.isDirectory()) it.deleteDir() else it.delete()
        }
        def process = new ProcessBuilder('git', 'clone', '--depth', "${depth}", '--branch', 'master', origin.absolutePath, targetDir.absolutePath)
                .redirectErrorStream(true)
                .start()
        def output = process.inputStream.text
        process.waitFor()
        if (process.exitValue() != 0) {
            throw new RuntimeException("Failed to shallow clone: ${output}")
        }
    }

    private void writeBuildFile() {
        buildFile << """\
            plugins {
                id 'com.netflix.nebula.release'
                id 'java'
            }

            ext.dryRun = true
            group = 'test'

            task showVersion {
                doLast {
                    logger.lifecycle "Version in task: \${version.toString()}"
                }
            }
        """.stripIndent()
        new File(projectDir, '.gitignore') << '''.gradle-test-kit
.gradle
build/
gradle.properties'''.stripIndent()

        configureGitUser(projectDir)
        git(projectDir, 'add', '.')
        git(projectDir, 'commit', '-m', 'Add build files')
    }

    private void enableUnshallow() {
        new File(projectDir, "gradle.properties").text = "nebula.release.features.unshallowEnabled=true\n"
    }

    private static void configureGitUser(File dir) {
        git(dir, 'config', 'user.email', 'test@example.com')
        git(dir, 'config', 'user.name', 'Test User')
        git(dir, 'config', 'commit.gpgsign', 'false')
        git(dir, 'config', 'tag.gpgsign', 'false')
    }

    private static String git(File dir, String... args) {
        def command = ['git'] + args.toList()
        def process = new ProcessBuilder(command)
                .directory(dir)
                .redirectErrorStream(true)
                .start()
        def output = process.inputStream.text
        process.waitFor()
        if (process.exitValue() != 0) {
            throw new RuntimeException("Git command failed: ${command.join(' ')}\n${output}")
        }
        return output
    }
}
