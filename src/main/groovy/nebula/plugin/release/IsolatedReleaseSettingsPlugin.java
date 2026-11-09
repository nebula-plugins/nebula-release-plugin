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

import nebula.plugin.release.git.opinion.TimestampPrecision;
import org.gradle.api.GradleException;
import org.gradle.api.IsolatedAction;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.initialization.Settings;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Experimental settings entry point for isolated release workflows.
 * The existing project plugin must still be applied to each release participant.
 */
public final class IsolatedReleaseSettingsPlugin implements Plugin<Settings> {
    @Override
    public void apply(Settings settings) {
        if (!IsolatedReleaseSupport.isEnabled(settings.getProviders())) {
            throw new GradleException("com.netflix.nebula.release-settings requires "
                    + IsolatedReleaseSupport.FLAG + "=true in gradle.properties or -P.");
        }
        var extension = settings.getExtensions().create("isolatedRelease", IsolatedReleaseSettings.class);
        extension.getScope().convention(settings.getProviders().gradleProperty("release.scope"));
        extension.getPrefixTagsWithV().convention(true);
        extension.getReplaceDevWithImmutableSnapshot().convention(settings.getProviders()
                .gradleProperty(FeatureFlags.NEBULA_RELEASE_REPLACE_DEV_SNAPSHOT_WITH_IMMUTABLE_SNAPSHOT)
                .map(Boolean::parseBoolean).orElse(false));
        extension.getTimestampPrecision().convention(settings.getProviders()
                .gradleProperty(FeatureFlags.NEBULA_RELEASE_IMMUTABLE_SNAPSHOT_TIMESTAMP_PRECISION).orElse("MINUTES"));

        settings.getGradle().settingsEvaluated(evaluated -> {
            extension.getReplaceDevWithImmutableSnapshot().finalizeValue();
            extension.getTimestampPrecision().finalizeValue();
            var replaceDev = extension.getReplaceDevWithImmutableSnapshot().get();
            var stage = IsolatedReleaseSupport.validateWorkflow(evaluated, replaceDev);
            // One settings snapshot is copied into every isolated project action and
            // retained with the cached configuration. No per-project clock reads.
            var timestamp = replaceDev || "snapshot".equals(stage)
                    ? TimestampPrecision.from(extension.getTimestampPrecision().get())
                            .getDateTimeFormatter().format(Instant.now()) : "";
            // Settings and the root project may live in different directories.
            // Resolve defaults only after the root descriptor has been configured.
            if (!extension.getGitRoot().isPresent()) {
                var projectDir = evaluated.getRootProject().getProjectDir();
                extension.getGitRoot().fileValue(evaluated.getProviders().gradleProperty("git.root")
                        .map(value -> resolveRoot(projectDir, value)).getOrElse(projectDir));
            }
            extension.getGitRoot().finalizeValue();
            extension.getScope().finalizeValue();
            extension.getPrefixTagsWithV().finalizeValue();
            extension.getReleaseBranchPatterns().finalizeValue();
            extension.getExcludeBranchPatterns().finalizeValue();
            extension.getShortenedBranchPattern().finalizeValue();
            extension.getAllowReleaseFromDetached().finalizeValue();
            extension.getCheckRemoteBranchOnRelease().finalizeValue();
            var inputs = new IsolatedReleaseInputs(extension.getGitRoot().get().getAsFile(),
                    extension.getScope().getOrNull(),
                    evaluated.getProviders().gradleProperty("release.travisBranch").getOrNull(),
                    extension.getPrefixTagsWithV().get(),
                    evaluated.getProviders().gradleProperty("release.sanitizeVersion")
                            .map(Boolean::parseBoolean).getOrElse(false),
                    evaluated.getProviders().gradleProperty("release.version").getOrNull(), stage,
                    Set.copyOf(extension.getReleaseBranchPatterns().get()),
                    Set.copyOf(extension.getExcludeBranchPatterns().get()),
                    extension.getShortenedBranchPattern().get(), extension.getAllowReleaseFromDetached().get(),
                    replaceDev, timestamp, extension.getCheckRemoteBranchOnRelease().get());
            var paths = new ArrayList<String>();
            var leaves = new HashSet<String>();
            collectProjects(evaluated.getRootProject(), paths, leaves);
            evaluated.getGradle().getLifecycle().beforeProject(
                    new ConfigureProject(inputs, List.copyOf(paths), Set.copyOf(leaves)));
        });
    }

    private static File resolveRoot(File projectDir, String path) {
        var file = new File(path);
        return file.isAbsolute() ? file : new File(projectDir, path);
    }

    private static void collectProjects(ProjectDescriptor project, List<String> paths, Set<String> leaves) {
        paths.add(project.getPath());
        if (project.getChildren().isEmpty()) {
            leaves.add(project.getPath());
        }
        project.getChildren().forEach(child -> collectProjects(child, paths, leaves));
    }

    // Gradle's record codec requires a public canonical constructor.
    public record ConfigureProject(IsolatedReleaseInputs inputs, List<String> paths, Set<String> leaves)
            implements IsolatedAction<Project> {
        @Override
        public void execute(Project project) {
            IsolatedReleaseSupport.install(project, inputs, paths, leaves.contains(project.getPath()));
        }
    }
}
