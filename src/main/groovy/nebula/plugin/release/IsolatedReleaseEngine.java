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
package nebula.plugin.release;

import com.github.zafarkhaja.semver.Version;
import nebula.plugin.release.git.GitBuildService;
import nebula.plugin.release.git.base.TagStrategy;
import nebula.plugin.release.git.base.ReleaseVersion;
import nebula.plugin.release.git.semver.ChangeScope;
import nebula.plugin.release.git.semver.NearestVersionLocator;
import org.gradle.api.GradleException;

import java.util.Locale;

/** Built-in inference with explicit inputs and no Project dependency. */
final class IsolatedReleaseEngine {
    private IsolatedReleaseEngine() {}

    static ReleaseVersion infer(IsolatedReleaseInputs inputs, GitBuildService git) {
        if (!inputs.baseRelease() && !git.isGitRepo()) {
            return IsolatedStrategyBridge.version("0.1.0-dev.0.uncommitted", null, false);
        }
        var branch = git.isGitRepo() ? git.getCurrentBranch() : "";
        if (!inputs.baseRelease()) ReleasePlugin.checkBranchName(branch);
        if (!inputs.baseRelease() && !git.hasCommit()) {
            return IsolatedStrategyBridge.version(inputs.replaceDevWithImmutableSnapshot()
                    ? "0.1.0-snapshot." + inputs.timestamp() + ".uncommitted" : "0.1.0-dev.0.uncommitted", null, false);
        }
        var tags = new TagStrategy();
        tags.setPrefixNameWithV(inputs.prefixTagsWithV());
        // Last-tag reuse precedes supplied versions, just as on the legacy path.
        if (inputs.useLastTag()) {
            requireCommit(inputs, git);
            return IsolatedStrategyBridge.lastTag(new NearestVersionLocator(git, tags).locate(), inputs, git);
        }
        if (inputs.suppliedVersion() != null) {
            var version = inputs.suppliedVersion();
            if (version.isEmpty()) {
                throw new GradleException("Supplied release.version is empty");
            }
            if (inputs.sanitize()) {
                version = VersionSanitizerUtil.sanitize(version);
            }
            if (inputs.verifySuppliedVersion()) {
                try {
                    Version.valueOf(version.startsWith("v") ? version.substring(1) : version);
                } catch (RuntimeException e) {
                    throw new GradleException("Supplied release.version (" + version + ") is not valid per semver spec.", e);
                }
            }
            return IsolatedStrategyBridge.version(version, null, true);
        }
        requireCommit(inputs, git);
        if (inputs.stage() == null && inputs.defaultStage() == null) {
            throw IsolatedReleaseSupport.unsupported("release-base requires isolatedRelease.defaultStage, "
                    + "release.stage, release.version, or release.useLastTag to select version inference");
        }
        var nearest = new NearestVersionLocator(git, tags).locate();
        var scope = inputs.scope() == null ? null : ChangeScope.valueOf(inputs.scope().toUpperCase(Locale.ROOT));
        return IsolatedStrategyBridge.infer(nearest, scope, inputs, branch, git.getHead(), !git.isCleanStatus());
    }

    private static void requireCommit(IsolatedReleaseInputs inputs, GitBuildService git) {
        if (inputs.baseRelease() && (!git.isGitRepo() || !git.hasCommit())) {
            throw IsolatedReleaseSupport.unsupported("release-base built-in inference and last-tag reuse require "
                    + "a Git repository with an initial commit; a supplied version can be used for ordinary commands");
        }
    }
}
