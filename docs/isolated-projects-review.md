# Experimental Isolated Projects compatibility review

This review covers the development stack in
[PR #314](https://github.com/nebula-plugins/nebula-release-plugin/pull/314) through
[PR #319](https://github.com/nebula-plugins/nebula-release-plugin/pull/319), plus
the cache-boundary fixture in `IsolatedCacheBoundaryTest`. It records the
supported experimental boundary; it is not a general Isolated Projects
compatibility declaration or a claim about an already published release.

## Source and behavior review

| Contract | Review result and evidence |
| --- | --- |
| Rollout | The flag defaults off. The full plugin checks it in its constructor before legacy root access and again on application. Base checks it before constructing the legacy extension. Tests cover same-daemon mode changes and inconsistent adoption. |
| Existing API | The original plugin IDs, public constructors, extension/strategy signatures, and task types remain. The settings plugin/DSL are additive. Existing legacy strategies still delegate to the shared calculations through their original methods. This is a source/API review, not an automated binary-ABI certification. |
| Legacy behavior | Flag-disabled fixtures retain root DSL, built-in/custom strategy support, default version selection, and lazy inference. Legacy unit/integration tests and legacy/adopted comparisons remain part of the full build. |
| Common inputs | Settings finalize provider-backed inputs before project configuration. Public immutable records carry values and hierarchy metadata into state-isolating lifecycle actions. Each project sets its own version/status. The Git service shares tracked Git reads, rather than handing a mutable root extension or Project to children. |
| Participation | Forwarding tasks use explicit paths and local plugin/publication state. Flat/nested fixtures cover leaf builds, Java aggregators, root-as-leaf, version-only projects, and explicit participants. Base mode installs only its root coordinator tasks. |
| Release effects | Disposable remotes verify versions, tag names/messages/targets, pushed refs, last-tag reuse, skip policies, remote checks, and validation before tagging. Full and base policies are compared separately. |
| Publishing | Native Maven/Ivy repositories contain real versioned JARs and descriptors. Dependencies/finalizers preserve Gradle scheduling, including skipped or failed release execution. Descriptor-only requests do not trigger publishing. |
| No Git | Full-plugin participants receive the literal legacy fallback and no release tasks/extensions; nonparticipants retain their versions. Direct overrides remain valid. Git discovery invalidates cached mode selection in both directions. |
| Adoption failures | Missing settings/root setup, mixed modes, frozen settings, local version/stage changes, unsupported DSL/strategies, and Artifactory receive failures rather than silently discarded customization. |

The Git process exit-status correction in
[PR #316](https://github.com/nebula-plugins/nebula-release-plugin/pull/316)
intentionally affects both modes: successful stderr progress no longer causes a
failure, and nonzero exits fail even without stderr. It is isolated from the
feature changes for independent review and has real-remote and exit-status
regression coverage.

## Configuration-cache and timestamp evidence

The main functional matrix uses Gradle 9.0.0 and 9.5.0 with strict configuration
cache and Isolated Projects, including cold storage, whole-build reuse, and
Git/property/settings invalidation. CI runs the full build on test JVMs 17, 21,
and 25; plugin compilation uses a Java 17 toolchain.

`IsolatedCacheBoundaryTest` additionally pins Gradle 9.9.0. Its fixture has a root,
sibling, nested aggregator/leaf, and version-only project. A delayed child crosses
a real seconds-precision boundary. Configuration markers distinguish settings
and project evaluation from task execution or up-to-date outcomes.

| Invocation | Settings/project configuration | Version/timestamp result |
| --- | --- | --- |
| Cold | Settings and all projects execute. | All projects, a real JAR, and a POM use the same immutable-snapshot version. |
| Whole-build warm hit | No settings or project configuration executes. | The original timestamp remains, even after the clock crosses a precision boundary. |
| Edit only a Git-ignored child applied script | Git remains clean, but settings and all projects execute on 9.0.0/9.5.0/9.9.0. | The changed child input is observed and every version/artifact uses one refreshed timestamp. |
| Next warm hit | No settings or project configuration executes. | The refreshed timestamp is retained. |

This avoids treating a tracked build-script edit, which also changes shared Git
dirty state, as evidence of project-local invalidation. It also avoids treating a
whole-build cache hit as partial project configuration reuse.

## Remaining acceptance gaps

Partial project-cache reuse cannot currently be established through the supported
task-build invocation. The latest release checked for this review, Gradle 9.9.0,
states: "all projects are always configured; there is no project configuration
avoidance yet, whether by skipping unneeded projects or by using cached results."
Its version history records tooling model caching as disabled since 9.3.0 due to
known correctness problems. This review does not enable internal cache switches
or use diagnostics mode to substitute for the missing behavior.

References:

- [Gradle 9.9.0 task-build performance](https://docs.gradle.org/9.9.0/userguide/isolated_projects.html#sec:perf_tasks)
- [Pinned Gradle 9.9.0 source documentation](https://github.com/gradle/gradle/blob/v9.9.0/platforms/documentation/docs/src/docs/userguide/optimizing-builds/isolated_projects.adoc)

The partial-reuse acceptance criterion remains open. When Gradle provides the
mode, a successful test must show an unchanged root/sibling configuration being
reused while the child reconfigures, and still compare all versions and artifacts.
The current whole-graph assertion must not be relabeled as that proof.

Artifactory 6.0.0/6.0.4 fail isolation independently of this plugin. A supported
Artifactory range remains dependent on upstream compatibility. Arbitrary
Project-based custom strategies, tag callbacks, composite builds, independent
release trains, and unvalidated custom workflow entry points remain outside the
experimental guarantee; legacy behavior remains available with the flag off.

## Review and rollout decision

Keep the flag off by default and retain the experimental support wording. The
documented Gradle 9.0/9.5 workflow boundary can be reviewed bottom-up through the
stack. The additional 9.9 cache probe does not expand the complete release
workflow matrix. Existing release APIs have been retained, but the outstanding
partial-reuse and Artifactory criteria prevent a general compatibility claim.

Review each slice under the existing repository policy, including the separate
Git correctness fix. This review does not merge PRs, change repository checks, or
enable a merge queue.
