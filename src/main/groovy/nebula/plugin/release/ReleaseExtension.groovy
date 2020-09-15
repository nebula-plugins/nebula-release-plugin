/*
 * Copyright 2014-2017 Netflix, Inc.
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

class ReleaseExtension {
    Set<String> releaseBranchPatterns = [/master/, /HEAD/, /main/, /(release(-|\/))?\d+(\.\d+)?\.x/, /v?\d+\.\d+\.\d+/] as Set<String>
    Set<String> excludeBranchPatterns = [] as Set<String>

    /**
     * This should be a regex pattern with one(1) capture group. By default shortens the typical
     * {bugfix|feature|hotfix|release}/branch-name to branch-name. The prefix is optional and a
     * dash may be used instead of the forward slash.
     */
    String shortenedBranchPattern = /(?:(?:bugfix|feature|hotfix|release)(?:-|\/))?(.+)/

    Boolean allowReleaseFromDetached = false

    Boolean checkRemoteBranchOnRelease = false

    void addReleaseBranchPattern(String pattern) {
        releaseBranchPatterns.add(pattern)
    }

    void addExcludeBranchPattern(String pattern) {
        excludeBranchPatterns.add(pattern)
    }
}
