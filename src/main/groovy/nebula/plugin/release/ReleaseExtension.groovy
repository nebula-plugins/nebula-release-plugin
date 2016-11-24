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

import org.ajoberstar.grgit.util.ConfigureUtil

class ReleaseExtension {
    Set<String> releaseBranchPatterns = [/master/, /HEAD/, /(release(-|\/))?\d+(\.\d+)?\.x/, /v?\d+\.\d+\.\d+/] as Set
    Set<String> excludeBranchPatterns = [] as Set

    BranchNameInSnapshot branchNameInSnapshot = new BranchNameInSnapshot()

    /**
     * This should be a regex pattern with one(1) capture group
     */
    String shortenedBranchPattern = /(?:feature(?:-|\/))?(.+)/

    void addReleaseBranchPattern(String pattern) {
        releaseBranchPatterns.add(pattern)
    }

    void addExcludeBranchPattern(String pattern) {
        excludeBranchPatterns.add(pattern)
    }

    void branchNameInSnapshot(Closure closure){
        ConfigureUtil.configure(branchNameInSnapshot, closure)
    }

    /**
     * Whether to include the branch name in the version for maven style snapshots
     */
    class BranchNameInSnapshot {
        boolean enabled = false
        Set<String> includeBranchPatterns = [/feature(-|\/).*/] as Set
        Set<String> excludeBranchPatterns = [] as Set

        void enabled(boolean value){
            enabled = value
        }

        void addIncludeBranchPattern(String pattern) {
            includeBranchPatterns.add(pattern)
        }

        void addExcludeBranchPattern(String pattern) {
            excludeBranchPatterns.add(pattern)
        }

    }
}
