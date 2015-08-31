/*
 * Copyright 2014-2015 Netflix, Inc.
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

/**
 * Tests for {@link GitVersioningIntegrationSpec}.
 */
class GitVersioningIntegrationSpecSpec extends GitVersioningIntegrationSpec {
    @Override def setupBuild() {}

    def 'inferred version is parsed'() {
        when:
        def version = inferredVersion('Inferred project: inferred-version-is-parsed, version: 1.0.0')

        then:
        version == normal('1.0.0')
    }

    def 'inferred version is parsed from multiline input'() {
        when:
        def version = inferredVersion('Some\nDummy\nText\nInferred project: inferred-version-is-parsed-from-multiline-input, version: 1.0.0\nAfter\nText')

        then:
        version == normal('1.0.0')
    }

    def 'missing inferred version throws IAE'() {
        when:
        inferredVersion('')

        then:
        thrown(IllegalArgumentException)
    }
}