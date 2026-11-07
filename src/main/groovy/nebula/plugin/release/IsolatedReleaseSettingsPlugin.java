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
import nebula.plugin.release.git.GitBuildService;
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
        extension.getBaseRelease().convention(false);
        extension.getVersion().convention(settings.getProviders().gradleProperty("release.version"));
        extension.getRemote().convention("origin");
        // The legacy flag disables verification by presence, even when its value is false.
        extension.getVerifySuppliedVersion().convention(!settings.getProviders()
                .gradleProperty("release.ignoreSuppliedVersionVerification").isPresent());
        extension.getReplaceDevWithImmutableSnapshot().convention(settings.getProviders()
                .gradleProperty(FeatureFlags.NEBULA_RELEASE_REPLACE_DEV_SNAPSHOT_WITH_IMMUTABLE_SNAPSHOT)
                .map(Boolean::parseBoolean).orElse(false));
        extension.getTimestampPrecision().convention(settings.getProviders()
                .gradleProperty(FeatureFlags.NEBULA_RELEASE_IMMUTABLE_SNAPSHOT_TIMESTAMP_PRECISION).orElse("MINUTES"));

        settings.getGradle().settingsEvaluated(evaluated -> {
            // Settings and the root project may live in different directories.
            // Resolve defaults only after the root descriptor has been configured.
            if (!extension.getGitRoot().isPresent()) {
                var projectDir = evaluated.getRootProject().getProjectDir();
                extension.getGitRoot().fileValue(evaluated.getProviders().gradleProperty("git.root")
                        .map(value -> resolveRoot(projectDir, value)).getOrElse(projectDir));
            }
            extension.getGitRoot().finalizeValue();
            extension.getBaseRelease().finalizeValue();
            extension.getReplaceDevWithImmutableSnapshot().finalizeValue();
            extension.getTimestampPrecision().finalizeValue();
            extension.getVersion().finalizeValue();
            extension.getRemote().finalizeValue();
            extension.getVerifySuppliedVersion().finalizeValue();
            var baseRelease = extension.getBaseRelease().get();
            var replaceDev = extension.getReplaceDevWithImmutableSnapshot().get();
            if (!baseRelease && !extension.getDefaultStage().isPresent()) {
                extension.getDefaultStage().convention(replaceDev ? "snapshot" : "dev");
            }
            extension.getDefaultStage().finalizeValue();
            // Track repository discovery in settings as well as project inference.
            // A no-Git build must ignore full-plugin release policies, as the legacy fallback does.
            var git = evaluated.getGradle().getSharedServices().registerIfAbsent("nebulaIsolatedReleaseGit",
                    GitBuildService.class, spec -> spec.getParameters().getGitRootDir()
                            .set(extension.getGitRoot().get().getAsFile())).get();
            var active = baseRelease || git.isGitRepo();
            var stage = active ? IsolatedReleaseSupport.validateWorkflow(evaluated, replaceDev, baseRelease) : null;
            if (active && extension.getDefaultStage().isPresent()) {
                IsolatedReleaseSupport.validateStage(extension.getDefaultStage().get(), "isolatedRelease.defaultStage");
            }
            if (active && extension.getRemote().get().isBlank()) {
                throw IsolatedReleaseSupport.unsupported("isolatedRelease.remote must not be blank");
            }
            if (baseRelease && replaceDev) {
                throw IsolatedReleaseSupport.unsupported("development replacement is a full-plugin convention; "
                        + "use isolatedRelease.defaultStage = 'snapshot' for release-base");
            }
            // One timestamp is retained with cached settings and copied to every project.
            var effectiveStage = stage == null ? extension.getDefaultStage().getOrNull() : stage;
            var timestamp = git.isGitRepo() && (!baseRelease && replaceDev || "snapshot".equals(effectiveStage))
                    ? TimestampPrecision.from(extension.getTimestampPrecision().get())
                            .getDateTimeFormatter().format(Instant.now()) : "";
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
                    extension.getVersion().getOrNull(), stage,
                    Set.copyOf(extension.getReleaseBranchPatterns().get()),
                    Set.copyOf(extension.getExcludeBranchPatterns().get()),
                    extension.getShortenedBranchPattern().get(), extension.getAllowReleaseFromDetached().get(),
                    replaceDev, timestamp, extension.getCheckRemoteBranchOnRelease().get(),
                    active && !baseRelease && IsolatedReleaseSupport.isReleaseWorkflow(evaluated),
                    active && !baseRelease ? IsolatedReleaseSupport.workflowStatus(evaluated) : null,
                    evaluated.getProviders().gradleProperty("release.useLastTag").map(Boolean::parseBoolean).getOrElse(false),
                    baseRelease, extension.getDefaultStage().getOrNull(), extension.getRemote().get(),
                    extension.getVerifySuppliedVersion().get());
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
