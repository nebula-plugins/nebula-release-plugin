/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nebula.plugin.release

import nebula.test.ProjectSpec
import org.ajoberstar.grgit.Grgit
import spock.lang.Unroll

class ReleasePluginOptionalDepsSpec extends ProjectSpec {
    Grgit git

    def setup() {
        git = Grgit.init(dir: projectDir)
        git.commit(message: 'initial commit')
        git.tag.add(name: 'v0.0.1')
    }

    @Unroll('verify isClassPresent determines #className #presenceString present')
    def 'verify isClassPresent'() {
        given:
        def myPlugin = project.plugins.apply(ReleasePlugin)

        expect:
        myPlugin.isClassPresent(className) == presence

        where:
        className                     | presence
        'example.nebula.DoesNotExist' | false
        'java.util.List'              | true

        presenceString = presence ? 'is' : 'is not'
    }
}
