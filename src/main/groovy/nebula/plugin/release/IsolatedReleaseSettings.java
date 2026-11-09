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

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;

/**
 * Experimental common inputs for isolated release workflows.
 * Configure this extension in settings, before project configuration starts.
 */
public abstract class IsolatedReleaseSettings extends ReleaseExtension {
    /**
     * Creates the common settings extension.
     * @param objects Gradle's object factory
     */
    @Inject
    public IsolatedReleaseSettings(ObjectFactory objects) {
        super(objects);
    }

    /** @return the Git worktree directory, defaulting to git.root or the root project's directory */
    public abstract DirectoryProperty getGitRoot();

    /** @return the optional version scope, defaulting to the release.scope Gradle property */
    public abstract Property<String> getScope();

    /** @return whether version tags use the v prefix, for both reading and creation; defaults to true */
    public abstract Property<Boolean> getPrefixTagsWithV();

    /** @return whether default and devSnapshot versions use immutable snapshots; defaults to the existing feature flag */
    public abstract Property<Boolean> getReplaceDevWithImmutableSnapshot();

    /** @return DAYS, HOURS, MINUTES, SECONDS, or MILLISECONDS; defaults to the existing precision property or MINUTES */
    public abstract Property<String> getTimestampPrecision();

    /** @return whether the root coordinator is release-base instead of the full plugin; defaults to false */
    public abstract Property<Boolean> getBaseRelease();

    /**
     * @return the built-in default stage: dev, SNAPSHOT, snapshot, milestone, rc, or final.
     * Full mode defaults to dev (or snapshot with development replacement); base mode requires an explicit choice
     * when neither release.stage, a supplied version, nor last-tag reuse selects inference.
     */
    public abstract Property<String> getDefaultStage();

    /** @return an optional supplied version, defaulting to the release.version Gradle property */
    public abstract Property<String> getVersion();

    /** @return the remote used for preparation and tag pushes; defaults to origin */
    public abstract Property<String> getRemote();

    /** @return whether supplied versions must be valid semver; defaults to the existing verification policy */
    public abstract Property<Boolean> getVerifySuppliedVersion();
}
