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
import nebula.plugin.release.git.base.ReleaseTask;
import nebula.plugin.release.git.base.PrepareTask;
import nebula.plugin.release.git.base.ReleaseVersion;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.ivy.plugins.IvyPublishPlugin;
import org.gradle.api.publish.ivy.IvyPublication;
import org.gradle.api.publish.PublishingExtension;
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

    static String validateWorkflow(Settings settings, boolean replaceDev) {
        var properties = settings.getProviders();
        var tasks = settings.getGradle().getStartParameter().getTaskNames();
        boolean snapshot = tasks.stream().anyMatch(Set.of("snapshot", ":snapshot")::contains);
        boolean development = tasks.stream().anyMatch(Set.of("devSnapshot", ":devSnapshot")::contains);
        boolean immutable = tasks.stream().anyMatch(Set.of("immutableSnapshot", ":immutableSnapshot")::contains);
        boolean candidate = tasks.stream().anyMatch(Set.of("candidate", ":candidate")::contains);
        boolean finalRelease = tasks.stream().anyMatch(Set.of("final", ":final")::contains);
        if ((snapshot ? 1 : 0) + (development ? 1 : 0) + (immutable ? 1 : 0)
                + (candidate ? 1 : 0) + (finalRelease ? 1 : 0) > 1) {
            throw new GradleException("Only one of snapshot, immutableSnapshot, devSnapshot, candidate, or final can be specified.");
        }
        var explicitStage = properties.gradleProperty("release.stage").getOrNull();
        if (explicitStage != null && !Set.of("SNAPSHOT", "dev", "snapshot", "milestone", "rc", "final").contains(explicitStage)) {
            throw unsupported("release.stage must be SNAPSHOT, snapshot, dev, milestone, rc or final");
        }
        if (properties.gradleProperty("release.defaultVersioningStrategy").isPresent()
                || properties.gradleProperty("release.ignoreSuppliedVersionVerification").isPresent()) {
            throw unsupported("custom strategy and supplied-version validation overrides are not implemented");
        }
        return finalRelease ? "final" : candidate ? "rc" : snapshot ? "SNAPSHOT"
                : immutable || development && replaceDev ? "snapshot" : development ? "dev" : explicitStage;
    }

    static boolean isReleaseWorkflow(Settings settings) {
        return settings.getGradle().getStartParameter().getTaskNames().stream()
                .anyMatch(Set.of("candidate", ":candidate", "final", ":final")::contains);
    }

    static String workflowStatus(Settings settings) {
        var tasks = settings.getGradle().getStartParameter().getTaskNames();
        boolean candidate = tasks.stream().anyMatch(Set.of("candidate", ":candidate", "candidateSetup", ":candidateSetup")::contains);
        boolean finalRelease = tasks.stream().anyMatch(Set.of("final", ":final", "finalSetup", ":finalSetup")::contains);
        if (candidate && finalRelease) {
            throw unsupported("candidate and final setup/status cannot be combined");
        }
        return finalRelease ? "release" : candidate ? "candidate" : null;
    }

    static void install(Project project, IsolatedReleaseInputs inputs, List<String> paths, boolean leaf) {
        if (!isEnabled(project.getProviders())) {
            throw unsupported("the rollout flag must be enabled build-wide before settings evaluation");
        }
        var service = project.getGradle().getSharedServices().registerIfAbsent(
                "nebulaIsolatedReleaseGit", GitBuildService.class,
                spec -> spec.getParameters().getGitRootDir().set(inputs.gitRoot()));
        var git = service.get();
        if (git.isGitRepo() && inputs.releaseWorkflow()) {
            if (project.getPath().equals(":") && !inputs.useLastTag()) git.verifyUserGitConfig();
            var status = git.getStatus();
            if (status != null && !status.isEmpty()) {
                throw new GradleException(new ErrorMessageFormatter().format(status));
            }
        }
        var inferred = IsolatedReleaseEngine.infer(inputs, git);
        var version = new LocalVersion(inferred.getVersion());
        var model = new LocalModel(paths, leaf, git.isGitRepo(), git.isGitRepo() && git.hasCommit(),
                git.isGitRepo() ? git.getCurrentBranch() : "", inputs, inferred);
        project.getExtensions().add(MODEL, model);
        project.setVersion(version);
        if (inputs.stage() != null) {
            project.getExtensions().getExtraProperties().set("release.stage", inputs.stage());
        }
        project.getTasks().register(BUILD);
        project.getTasks().register(DESCRIPTORS);
        project.afterEvaluate(local -> {
            // Legacy setup tasks change status after project scripts have configured
            // publications. Preserve those descriptor defaults when assigning locally.
            if (inputs.status() != null) local.setStatus(inputs.status());
            var extras = local.getExtensions().getExtraProperties();
            for (var name : List.of(FLAG, "git.root", "release.scope", "release.version", "release.travisBranch",
                    "release.sanitizeVersion", "release.useLastTag", "release.defaultVersioningStrategy",
                    "release.ignoreSuppliedVersionVerification", "release.disableGitChecks", "release.travisci",
                    FeatureFlags.NEBULA_RELEASE_REPLACE_DEV_SNAPSHOT_WITH_IMMUTABLE_SNAPSHOT,
                    FeatureFlags.NEBULA_RELEASE_IMMUTABLE_SNAPSHOT_TIMESTAMP_PRECISION)) {
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
            if (local.getPath().equals(":")) {
                if (!isParticipant(local)) {
                    throw unsupported("apply com.netflix.nebula.release at the root project");
                }
                validateSelectors(local);
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
            throw unsupported("release orchestration requires a Git repository at the configured gitRoot");
        }
        if (!model.hasCommit() && project.getGradle().getStartParameter().getTaskNames().stream()
                .anyMatch(Set.of("snapshot", ":snapshot", "devSnapshot", ":devSnapshot", "immutableSnapshot", ":immutableSnapshot",
                        "snapshotSetup", ":snapshotSetup", "devSnapshotSetup", ":devSnapshotSetup",
                        "immutableSnapshotSetup", ":immutableSnapshotSetup", "candidate", ":candidate", "final", ":final",
                        "candidateSetup", ":candidateSetup", "finalSetup", ":finalSetup")::contains)) {
            throw unsupported("release orchestration requires an initial Git commit; "
                    + "ordinary help/build commands support no-commit version inference");
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

        if (project.getPath().equals(":") && model.inputs().releaseWorkflow()) {
            project.getPlugins().withType(IvyPublishPlugin.class, ignored ->
                    project.getExtensions().getByType(PublishingExtension.class).getPublications()
                            .withType(IvyPublication.class).configureEach(publication ->
                                    publication.getDescriptor().setStatus(model.inputs().status())));
        }

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
                task.getIsSnapshotRelease().set(!model.inputs().releaseWorkflow());
            });
            var service = project.getGradle().getSharedServices().registerIfAbsent(
                    "nebulaIsolatedReleaseGit", GitBuildService.class,
                    spec -> spec.getParameters().getGitRootDir().set(model.inputs().gitRoot()));
            var tasks = project.getGradle().getStartParameter().getTaskNames();
            var skip = List.of("release.disableGitChecks", "release.travisci").stream().anyMatch(name ->
                    project.getProviders().gradleProperty(name).map(Boolean::parseBoolean).getOrElse(false));
            var skipSnapshot = tasks.stream().anyMatch(Set.of("snapshot", ":snapshot", "devSnapshot", ":devSnapshot")::contains);
            var prepare = project.getTasks().register("prepare", PrepareTask.class, task -> {
                task.setGroup(GROUP);
                task.getProjectVersion().set(model.version());
                task.getRemote().set("origin");
                task.getGitService().set(service);
                task.setEnabled(!skip && !skipSnapshot && model.inputs().checkRemoteBranchOnRelease());
            });
            project.getTasks().configureEach(task -> {
                if (!task.getName().equals("prepare")) task.shouldRunAfter(prepare);
            });
            project.getTasks().register("release", ReleaseTask.class, task -> {
                task.setGroup(GROUP);
                task.dependsOn("prepare");
                task.shouldRunAfter(releaseCheck);
                task.dependsOn(paths(model.paths(), BUILD));
                task.getProjectVersion().set(model.version());
                task.getTagStrategy().set(IsolatedStrategyBridge.tagStrategy(model.inputs().prefixTagsWithV()));
                task.getRemote().set("origin");
                task.getGitService().set(service);
                task.setEnabled(!skip && !skipSnapshot && !model.inputs().useLastTag());
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
            for (var name : List.of("devSnapshot", "immutableSnapshot", "candidate", "final")) {
                var setup = project.getTasks().register(name + "Setup", task -> {
                    task.setGroup(GROUP);
                    task.dependsOn(releaseCheck);
                });
                project.getTasks().register(name, task -> {
                    task.setGroup(GROUP);
                    task.dependsOn(setup, "postRelease");
                });
            }
        }
    }

    private static List<String> paths(List<String> projects, String task) {
        return projects.stream().map(path -> path.equals(":") ? ":" + task : path + ":" + task).toList();
    }

    private static void validateSelectors(Project project) {
        // Stage selection happens before project configuration. Reject workflow
        // abbreviations before task execution can use a different inferred stage.
        // Exact local task names remain available for consumer-defined tasks.
        var names = project.getTasks().getNames();
        var workflows = List.of("snapshot", "devSnapshot", "immutableSnapshot", "snapshotSetup",
                "devSnapshotSetup", "immutableSnapshotSetup", "candidate", "candidateSetup", "final", "finalSetup",
                "release", "postRelease", "prepare", "releaseCheck");
        for (var requested : project.getGradle().getStartParameter().getTaskNames()) {
            var selector = requested.startsWith(":") ? requested.substring(1) : requested;
            if (selector.contains(":") || names.contains(selector)) continue;
            var parts = selector.split("(?=[A-Z])");
            for (var workflow : workflows) {
                var words = workflow.split("(?=[A-Z])");
                boolean matches = !selector.isEmpty() && parts.length <= words.length;
                for (int i = 0; matches && i < parts.length; i++) {
                    matches = words[i].regionMatches(true, 0, parts[i], 0, parts[i].length());
                }
                boolean prefix = !selector.isEmpty()
                        && workflow.regionMatches(true, 0, selector, 0, selector.length());
                if (prefix || matches) {
                    throw unsupported("abbreviated workflow selector '" + requested
                            + "' is not supported; use the full root task name, such as :" + workflow);
                }
            }
        }
    }

    private static boolean isParticipant(Project project) {
        return project.getPluginManager().hasPlugin("com.netflix.nebula.release")
                || project.getPluginManager().hasPlugin("nebula.release");
    }

    public static GradleException unsupported(String reason) {
        return new GradleException("Experimental isolated release: " + reason + ". "
                + "Disable " + FLAG + " to use the existing release implementation.");
    }

    private record LocalModel(List<String> paths, boolean leaf, boolean gitRepo, boolean hasCommit, String branch,
                              IsolatedReleaseInputs inputs, ReleaseVersion version) {}

    public record LocalVersion(String value) implements Serializable {
        @Override
        public String toString() { return value; }
    }
}
