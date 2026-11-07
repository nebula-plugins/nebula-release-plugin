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

import nebula.plugin.release.git.GitBuildService;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.ivy.plugins.IvyPublishPlugin;
import org.gradle.api.publish.ivy.tasks.GenerateIvyDescriptor;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.api.publish.maven.tasks.GenerateMavenPom;

import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.Objects;

/** Internal wiring for the experimental isolated implementation. */
public final class IsolatedReleaseSupport {
    public static final String FLAG = "nebula.release.isolatedProjectsSupport";
    private static final String MODEL = "__nebulaIsolatedRelease";
    private static final String BUILD = "nebulaReleaseBuild";
    private static final String DESCRIPTORS = "nebulaReleaseDescriptors";
    private static final String GROUP = "Nebula Release";

    private IsolatedReleaseSupport() {}

    public static boolean isEnabled(ProviderFactory providers) {
        return providers.gradleProperty(FLAG).map(Boolean::parseBoolean).getOrElse(false);
    }

    static String validateWorkflow(Settings settings) {
        var properties = settings.getProviders();
        var tasks = settings.getGradle().getStartParameter().getTaskNames();
        boolean snapshot = tasks.stream().anyMatch(Set.of("snapshot", ":snapshot")::contains);
        for (var task : tasks) {
            var name = task.substring(task.lastIndexOf(':') + 1);
            if (Set.of("candidate", "final", "devSnapshot", "immutableSnapshot", "candidateSetup",
                    "finalSetup", "devSnapshotSetup", "immutableSnapshotSetup").contains(name)) {
                throw unsupported("workflow '" + task + "' is not implemented");
            }
        }
        var explicitStage = properties.gradleProperty("release.stage").getOrNull();
        if (explicitStage != null && !Set.of("SNAPSHOT", "dev").contains(explicitStage)) {
            throw unsupported("release.stage must be SNAPSHOT or dev");
        }
        if (properties.gradleProperty("release.defaultVersioningStrategy").isPresent()
                || properties.gradleProperty("release.ignoreSuppliedVersionVerification").isPresent()) {
            throw unsupported("custom strategy and supplied-version validation overrides are not implemented");
        }
        for (var name : List.of("release.useLastTag", FeatureFlags.NEBULA_RELEASE_REPLACE_DEV_SNAPSHOT_WITH_IMMUTABLE_SNAPSHOT)) {
            if (properties.gradleProperty(name).map(Boolean::parseBoolean).getOrElse(false)) {
                throw unsupported(name + " is not implemented");
            }
        }
        if (!snapshot && tasks.stream().anyMatch(task -> Set.of("release", "postRelease", "prepare")
                .contains(task.substring(task.lastIndexOf(':') + 1)))) {
            throw unsupported("direct release orchestration is not implemented yet");
        }
        return snapshot ? "SNAPSHOT" : explicitStage;
    }

    static void install(Project project, IsolatedReleaseInputs inputs, List<String> paths, boolean leaf) {
        if (!isEnabled(project.getProviders())) {
            throw unsupported("the rollout flag must be enabled build-wide before settings evaluation");
        }
        var service = project.getGradle().getSharedServices().registerIfAbsent(
                "nebulaIsolatedReleaseGit", GitBuildService.class,
                spec -> spec.getParameters().getGitRootDir().set(inputs.gitRoot()));
        var git = service.get();
        var version = new LocalVersion(IsolatedReleaseEngine.infer(inputs, git));
        var model = new LocalModel(paths, leaf, git.isGitRepo(), git.isGitRepo() ? git.getCurrentBranch() : "", inputs);
        project.getExtensions().add(MODEL, model);
        project.setVersion(version);
        if (inputs.stage() != null) {
            project.getExtensions().getExtraProperties().set("release.stage", inputs.stage());
        }
        project.getTasks().register(BUILD);
        project.getTasks().register(DESCRIPTORS);
        project.afterEvaluate(local -> {
            var extras = local.getExtensions().getExtraProperties();
            for (var name : List.of(FLAG, "git.root", "release.scope", "release.version", "release.travisBranch",
                    "release.sanitizeVersion", "release.useLastTag", "release.defaultVersioningStrategy",
                    "release.ignoreSuppliedVersionVerification",
                    FeatureFlags.NEBULA_RELEASE_REPLACE_DEV_SNAPSHOT_WITH_IMMUTABLE_SNAPSHOT)) {
                var gradleProperty = local.getProviders().gradleProperty(name).getOrNull();
                if (extras.has(name) && !String.valueOf(extras.get(name)).equals(gradleProperty)) {
                    throw unsupported("project " + local.getPath() + " sets extra property '" + name
                            + "'; use Gradle properties or supported isolatedRelease settings instead");
                }
            }
            var localStage = extras.has("release.stage") ? extras.get("release.stage") : null;
            if (!Objects.equals(inputs.stage(), localStage)) {
                throw unsupported("project " + local.getPath() + " changes release.stage; configure it through Gradle properties");
            }
            if (local.getVersion() != version) {
                throw unsupported("project " + local.getPath() + " assigns project.version directly; "
                        + "use -Prelease.version instead");
            }
            if (local.getPath().equals(":") && !isParticipant(local)) {
                throw unsupported("apply com.netflix.nebula.release at the root project");
            }
        });
    }

    public static void apply(Project project) {
        var model = (LocalModel) project.getExtensions().findByName(MODEL);
        if (model == null) {
            throw unsupported("apply com.netflix.nebula.release-settings in settings.gradle before "
                    + "applying the project plugin");
        }
        if (!model.gitRepo()) {
            throw unsupported("snapshot orchestration requires a Git repository at the configured gitRoot");
        }
        project.getPluginManager().withPlugin("com.jfrog.artifactory", ignored -> {
            throw unsupported("Artifactory integration has not been validated");
        });
        if (model.leaf()) {
            project.getPlugins().withType(JavaPlugin.class, ignored ->
                    project.getTasks().named(BUILD).configure(task -> task.dependsOn("build")));
        }
        project.getPlugins().withType(MavenPublishPlugin.class, ignored ->
                project.getTasks().named(DESCRIPTORS).configure(task ->
                        task.dependsOn(project.getTasks().withType(GenerateMavenPom.class))));
        project.getPlugins().withType(IvyPublishPlugin.class, ignored ->
                project.getTasks().named(DESCRIPTORS).configure(task ->
                        task.dependsOn(project.getTasks().withType(GenerateIvyDescriptor.class))));

        if (project.getPath().equals(":")) {
            project.getExtensions().add("release", new RejectedReleaseDsl("release"));
            project.getExtensions().add("nebulaRelease", new RejectedReleaseDsl("nebulaRelease"));
            var releaseCheck = project.getTasks().register("releaseCheck", ReleaseCheck.class, task -> {
                task.setGroup(GROUP);
                task.getBranchName().set(model.branch());
                var patterns = project.getObjects().newInstance(ReleaseExtension.class);
                patterns.getReleaseBranchPatterns().set(model.inputs().releaseBranchPatterns());
                patterns.getExcludeBranchPatterns().set(model.inputs().excludeBranchPatterns());
                patterns.getAllowReleaseFromDetached().set(model.inputs().allowReleaseFromDetached());
                task.getPatterns().set(patterns);
                task.getIsSnapshotRelease().set(true);
            });
            project.getTasks().register("prepare", task -> { task.setGroup(GROUP); task.setEnabled(false); });
            project.getTasks().register("release", task -> {
                task.setGroup(GROUP);
                task.dependsOn("prepare");
                task.dependsOn(paths(model.paths(), BUILD));
                task.setEnabled(false);
            });
            project.getTasks().register("postRelease", task -> {
                task.setGroup(GROUP);
                task.dependsOn("release");
                task.dependsOn(paths(model.paths(), DESCRIPTORS));
            });
            project.getTasks().register("snapshotSetup", task -> {
                task.setGroup(GROUP);
                task.dependsOn(releaseCheck);
            });
            project.getTasks().register("snapshot", task -> {
                task.setGroup(GROUP);
                task.dependsOn("snapshotSetup", "postRelease");
            });
        }
    }

    private static List<String> paths(List<String> projects, String task) {
        return projects.stream().map(path -> path.equals(":") ? ":" + task : path + ":" + task).toList();
    }

    private static boolean isParticipant(Project project) {
        return project.getPluginManager().hasPlugin("com.netflix.nebula.release")
                || project.getPluginManager().hasPlugin("nebula.release");
    }

    public static GradleException unsupported(String reason) {
        return new GradleException("Experimental isolated release: " + reason + ". "
                + "Disable " + FLAG + " to use the existing release implementation.");
    }

    private record LocalModel(List<String> paths, boolean leaf, boolean gitRepo, String branch, IsolatedReleaseInputs inputs) {}

    public record LocalVersion(String value) implements Serializable {
        @Override
        public String toString() { return value; }
    }
}
