package nebula.plugin.release.util

import nebula.test.ProjectSpec
import spock.lang.Unroll

class ReleaseTasksUtilSpec extends ProjectSpec {

    def 'verify isUsingLatestTag reads the project property accordingly'() {
        given:
        project

        expect:
        !ReleaseTasksUtil.isUsingLatestTag(project)

        when:
        project.ext.'release.useLastTag' = false

        then:
        !ReleaseTasksUtil.isUsingLatestTag(project)

        when:
        project.ext.'release.useLastTag' = true

        then:
        ReleaseTasksUtil.isUsingLatestTag(project)
    }

    @Unroll
    def 'checking release task for #tasks results in #expected'() {
        expect:
        ReleaseTasksUtil.isReleaseTaskThatRequiresTagging(tasks) == expected

        where:
        tasks                          || expected
        ['build']                      || false
        ['build', 'devSnapshot']       || false
        ['build', 'candidate']         || true
        ['build', 'immutableSnapshot'] || false
        ['build', 'final']             || true
        [':devSnapshot']               || false
        ['devSnapshot']                || false
        [':snapshot']                  || false
        ['snapshot']                   || false
        ['candidate']                  || true
        [':candidate']                 || true
        ['immutableSnapshot']          || false
        [':immutableSnapshot']         || false
        ['final']                      || true
        [':final']                     || true
    }

}
