/*
 * Copyright 2012-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nebula.plugin.release.git.base

import nebula.plugin.release.git.command.GitWriteCommandsUtil
import spock.lang.Specification

class TagStrategySpec extends Specification {
    def 'maybeCreateTag with version create tag true will create a tag'() {
        given:
        GitWriteCommandsUtil gitWriteCommandsUtil = GroovyMock()
        1 * gitWriteCommandsUtil.createTag('v1.2.3', 'Release of 1.2.3')

        expect:
        new TagStrategy().maybeCreateTag(gitWriteCommandsUtil, new ReleaseVersion('1.2.3', null, true)) == 'v1.2.3'
    }

    def 'maybeCreateTag with version create tag false does not create a tag'() {
        given:
        GitWriteCommandsUtil gitWriteCommandsUtil = GroovyMock()
        0 * gitWriteCommandsUtil.createTag(*_)

        expect:
        new TagStrategy().maybeCreateTag(gitWriteCommandsUtil, new ReleaseVersion('1.2.3', null, false)) == null
    }

    def 'maybeCreateTag with version create tag true and prefix name with v false will create a tag'() {
        setup:
        GitWriteCommandsUtil gitWriteCommandsUtil = GroovyMock()

        when:
        def strategy = new TagStrategy()
        strategy.prefixNameWithV = false
        strategy.maybeCreateTag(gitWriteCommandsUtil, new ReleaseVersion('1.2.3', null, true)) == '1.2.3'

        then:
        1 * gitWriteCommandsUtil.createTag('1.2.3', 'Release of 1.2.3')
    }
}
