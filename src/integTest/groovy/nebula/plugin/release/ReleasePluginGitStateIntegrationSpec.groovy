package nebula.plugin.release


class ReleasePluginGitStateIntegrationSpec extends GitVersioningIntegrationTestKitSpec {

    @Override
    def setupBuild() {
        buildFile << """
            plugins {
                id 'com.netflix.nebula.release'
                id 'java'
            }
        """
    }

    def 'test that final and candidate do not work with uncommitted changes'() {
        setup:
        buildFile << "// force an uncommitted change to file"

        when:
        def finalFail = runTasksAndFail("final")

        then:
        finalFail.output.contains('require all changes to be committed into Git')

        when:
        def candidateFail = runTasksAndFail("candidate")

        then:
        candidateFail.output.contains('require all changes to be committed into Git')
    }

    def 'ensure plugin does NOT throw an error when a good init tag is present'() {
        setup:
        ['my-feature-branch', 'super-duper', 'v1.0', 'v0.1.0'].each { git.tag.add(name: it) }

        expect:
        runTasks("devSnapshot")
    }

}
