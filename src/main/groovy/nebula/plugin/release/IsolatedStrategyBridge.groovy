/*
 * Copyright 2026 the original author or authors.
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

import groovy.transform.PackageScope
import nebula.plugin.release.git.model.Branch
import nebula.plugin.release.git.model.Commit
import nebula.plugin.release.git.semver.ChangeScope
import nebula.plugin.release.git.semver.NearestVersion
import nebula.plugin.release.git.semver.SemVerStrategyState

/** Keeps construction of Groovy's AST-generated immutable state in Groovy. */
@PackageScope
final class IsolatedStrategyBridge {
    static String infer(NearestVersion nearest, ChangeScope scope, IsolatedReleaseInputs inputs,
                        String currentBranch, String head, boolean dirty) {
        String stage = inputs.stage() ?: 'dev'
        def state = new SemVerStrategyState(nearestVersion: nearest, scopeFromProp: scope,
                stageFromProp: stage, currentBranch: new Branch(fullName: currentBranch),
                currentHead: new Commit(id: head, abbreviatedId: head.take(7)), repoDirty: dirty)
        def strategy = NetflixOssStrategies.strategyFromInputs(stage, inputs.travisBranch())
        state = strategy.normalStrategy.infer(state)
        state = strategy.preReleaseStrategy.infer(state)
        state = strategy.buildMetadataStrategy.infer(state)
        if (stage == 'dev') {
            state = NetflixOssStrategies.metadataFromInputs(state, inputs.releaseBranchPatterns(), inputs.shortenedBranchPattern())
        }
        state.toVersion().toString()
    }
}
