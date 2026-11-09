# Isolated Projects support

Status: proposed. This document specifies an opt-in implementation; it does not
describe support available in a released version.

Implementation status: the working prototype covers ordinary `help`/`build`, root
`snapshot`, `devSnapshot`, `immutableSnapshot`, `candidate`, `final`, and default
version selection for their setup tasks with a Git repository, common inputs,
local version/status assignment, leaf Java builds, and Maven/Ivy descriptors.
Last-tag reuse, direct root release orchestration, root release-base, and the
full plugin's no-Git fallback are covered. Arbitrary custom strategies/legacy
extension DSL and active full-plugin Artifactory integration are rejected with
adoption diagnostics. Artifactory 6.0.0/6.0.4 have been tested and fail isolation
independently of this plugin; no supported Artifactory range is established. The
remaining milestones below are still required before claiming general Isolated
Projects compatibility.

In this prototype, explicit `isolatedRelease` settings override property
conventions. `gitRoot`, `scope`, and `version` default to `git.root`,
`release.scope`, and `release.version`. The `version` and `remote` settings accept
providers; `remote` defaults to `origin`. Common inputs freeze at the end of
settings evaluation, before project configuration. `verifySuppliedVersion`
defaults to true unless `release.ignoreSuppliedVersionVerification` is present,
even with value `false`; explicit settings configuration overrides that convention.
Supplied versions override built-in inference, except that full mode's existing
no-commit strategy still takes precedence. Last-tag reuse precedes supplied
versions in both modes.
No-commit full inference is supported for ordinary commands; primary release workflows
and their setup tasks require an initial commit and report an adoption diagnostic
when it is absent.
Current workflow coverage uses full root task names, with and without a leading
colon; abbreviated workflow selectors are diagnosed before task execution.
Custom workflow entry points still need validation.

Inferred immutable snapshots execute the root release task without creating a
tag. An explicit `release.version` retains the existing tagging/pushing behavior
for `immutableSnapshot`; `snapshot` and `devSnapshot` skip both preparation and
tagging. Settings expose the existing optional `checkRemoteBranchOnRelease`
policy. Existing Git skip controls still skip full-plugin preparation and tagging.

Release-base adoption requires `isolatedRelease.baseRelease = true` and applies
`com.netflix.nebula.release-base` only at the root. Settings assign common versions
to all projects without installing forwarding tasks. Only root `prepare` and
`release` are provided; Java builds, descriptors, status selection, and full
workflow branch validation are not added. Explicit consumer dependencies such as
`tasks.named('release') { dependsOn ':alpha:build' }` remain usable. Consumer task
names do not select a base stage.

The built-in adapter `defaultStage` accepts `dev`, `SNAPSHOT`, `snapshot`,
`milestone`, `rc`, and `final`. Full mode defaults to `dev` (or `snapshot` with
development replacement). Base mode requires an explicit default, `release.stage`,
a supplied version, or last-tag reuse. It uses stock base strategies: patch scope
except uppercase `SNAPSHOT` (minor), and commit-only development metadata.
Development replacement is a full-plugin convention; base consumers select
`defaultStage = 'snapshot'` instead. Base preparation remains enabled for last-tag
reuse and ignores full-plugin Git skip flags. Release still skips tagging for
last-tag reuse or a strategy that does not create tags. Built-in base inference
and last-tag reuse require an initial commit, while supplied base versions work
for ordinary commands without Git or commits. Arbitrary Project-based strategy
registration and tag callbacks remain legacy-only.

With no Git repository, full mode preserves the legacy fallback: only projects
applying the full plugin receive the literal String version
`0.1.0-dev.0.uncommitted`; other projects retain their versions. No release tasks,
extensions, forwarding tasks, or publication hooks are installed. Release
inference policies are ignored and later direct version assignment remains
allowed. Ordinary Java/Maven/Ivy tasks work independently. Repository discovery
is tracked in settings so creating or removing `.git` invalidates cached mode
selection. Base mode has no full-plugin no-Git fallback.

Candidate/final inference reuses the existing scope and prerelease strategies,
including precedence checks. Primary workflows check Git identity, dirty state,
and release branches with the existing policies; skip controls do not bypass
those validations. Only the root owns preparation, tagging, and pushing.
Release validation precedes tagging when both are in the graph. Direct
`release`/`postRelease`/`prepare` retain the existing distinction from primary
workflows: an explicit stage chooses inference without implicitly enabling their
branch validation or status selection.

Candidate/final setup assigns each project's status locally after its script is
evaluated, without selecting a version stage. Primary workflows also configure
the root Ivy descriptor status; child descriptor defaults retain legacy behavior.
Last-tag validation is shared with the legacy implementation, takes precedence
over supplied versions, and suppresses tagging/pushing. Snapshot stages reject
last-tag reuse; untagged commits and incompatible candidate/final tags fail.
Conflicting candidate/final setup statuses and abbreviated selectors are
diagnosed rather than selecting a status or stage from task realization order.

Immutable snapshots capture one UTC timestamp during settings evaluation using
the existing precision formats. Every project's immutable inputs carry that
same timestamp. Configuration-cache reuse retains it; settings reevaluation
captures a new timestamp. Delayed project configuration and local script
invalidation are covered on Gradle 9.0/9.5, with a dedicated cache-boundary probe
also covering 9.9. This does not establish consistency under partial project-cache
reuse on a future Gradle implementation.

The cache-boundary fixture records settings/project configuration separately from
task outcomes. Cold configuration evaluates every project; whole-build warm reuse
evaluates none and retains the common timestamp across a real precision boundary.
It then edits a Git-ignored applied script in one child, verifies Git remains
clean, and observes settings plus every project reconfiguring on Gradle
9.0.0/9.5.0/9.9.0. All project versions, a real JAR, and a POM agree on the new
timestamp, and the next invocation reuses it. The Git-ignored edit separates
Gradle input invalidation from shared Git-state invalidation.

Gradle 9.9.0 still documents that task builds have no project configuration
avoidance through cached results. Its documentation also records that tooling
model caching was disabled in 9.3.0 because of correctness issues. Neither a
whole-build cache hit nor these invalidation tests proves partial project-cache
reuse. That acceptance criterion remains dependent on an upstream implementation
or supported invocation mode that actually provides it. See the
[compatibility review](isolated-projects-review.md) and the pinned
[Gradle 9.9.0 documentation](https://docs.gradle.org/9.9.0/userguide/isolated_projects.html#sec:perf_tasks).

## Objective

Support Gradle Isolated Projects in single-project and multi-project builds while
preserving the existing release APIs and behavior for consumers that do not opt
in. Supported isolated workflows must produce equivalent versions, artifacts,
release participation, validation, and Git effects.

The scope includes the public plugin IDs `com.netflix.nebula.release`,
`nebula.release`, and `com.netflix.nebula.release-base`.

## Current behavior and constraints

The implementation currently shares a root-owned delayed version object with
every project, mutates stage and status across projects, reads root plugin and
task state from children, and inspects tasks from other projects through task
graph callbacks. These operations conflict with project isolation.

Configuration-cache compatibility alone does not establish Isolated Projects
compatibility. Single-project success and the no-Git fallback also do not prove
that multi-project release workflows work under isolation.

The public `VersionStrategy` contract receives a `Project`. Custom strategies
may read arbitrary mutable project state. Keeping this API compatible is
required; transparently supporting every implementation under isolation is not
possible without an explicit adoption contract.

## Rollout flag

Introduce a build-wide Gradle property:

```properties
nebula.release.isolatedProjectsSupport=true
```

Consumers may set it in root `gradle.properties` or pass
`-Pnebula.release.isolatedProjectsSupport=true` before plugin application.

Requirements:

- Default to `false`; only `true`, ignoring case, enables the new path.
- Read through `ProviderFactory.gradleProperty`. Do not use a static cached
  decision, inherited extra properties, or mutable project state to select mode.
- Select mode before constructors or extension initialization perform legacy
  cross-project access. Cover all three existing plugin entry points.
- Keep selection consistent throughout the build. Project-local changes after
  plugin application and mixed legacy/isolated configuration are unsupported and
  must not produce a partially selected implementation.
- Keep the flag independent of Gradle's own Isolated Projects setting. Neither
  enables the other, and the new implementation must also work with ordinary
  configuration cache when Gradle isolation is disabled.
- Track flag changes as configuration-cache inputs. Switching modes in the same
  daemon must not retain state from the previous implementation.

| Plugin flag | Gradle isolation | Contract |
| --- | --- | --- |
| Off | Off | Existing behavior and APIs, without consumer changes. |
| On | Off | New implementation and its explicit adoption requirements. |
| On | On | Supported isolated workflows, with strict validation. |
| Off | On | No new compatibility guarantee; do not silently enable the flag or disable isolation. |

This follows the explicit opt-in pattern used by
[dependency-recommender PR #162](https://github.com/nebula-plugins/nebula-dependency-recommender-plugin/pull/162).
The flag permits staged delivery; it does not by itself establish compatibility.

## Compatibility contract

### Legacy mode

With the flag disabled, preserve:

- Existing plugin IDs, task names, extension methods and properties, strategy
  interfaces, and aliases.
- Root-only plugin application, implicit common version propagation, root DSL
  customization, and custom `Project`-based strategies.
- Lazy version inference and supported late configuration behavior.
- Default version selection, scope/stage handling, explicit version overrides,
  sanitization, branch metadata, timestamp precision, and last-tag reuse.
- Release participation, publishing integration, task hooks, validation failures,
  and tagging/pushing behavior.

No settings changes or new plugin applications are required in legacy mode.
Intentional behavior corrections must be reviewed separately from this feature.

### Opt-in mode

Consumers provide common configuration through an isolation-safe settings or
convention entry point. Arbitrary root build-script state is not automatically
imported into children.

Keep existing public signatures. Add an isolation-safe strategy/configuration
surface where needed; do not replace the existing `VersionStrategy` parameters
or silently substitute a built-in strategy for a custom one.

Existing root customization must either have a documented, validated mapping to
the common input model or fail with a migration diagnostic. It must not be
accepted and ignored. Consumer scripts that configure other projects directly
also require migration.

## Proposed architecture

The following architecture must pass the prototype milestone before its public
names and DSL are finalized.

### 1. Common inputs and release engine

Extract built-in inference into a project-independent engine. Preserve existing
semantic-version calculations where possible rather than reimplementing them.

Use immutable input and result models, provisionally `ReleaseInputs` and
`ReleasePlan`. Separate common build configuration, tracked Git facts, and local
project participation. Inputs must cover:

- Effective stage, scope, explicit version, and default strategy selection.
- Git root, tag-reading rules, remote, and last-tag behavior.
- Branch inclusion/exclusion and metadata rules.
- Sanitization, immutable-snapshot timestamp precision, and a common timestamp.
- Git-check policy, including existing skip/check controls.

The result must include the inferred version and the decisions needed for
validation, status, preparation, and tagging. Preserve the existing
`ReleaseVersion` contract through compatible adapters.

Do not change root-based semantics by accidentally reading a child's name,
properties, or extension defaults. Define property precedence explicitly. Use
tracked Gradle properties for shared inputs; support local extra properties only
where the adoption contract explicitly permits them.

### 2. Settings companion and local configuration

Provide an additive settings companion, provisionally
`com.netflix.nebula.release-settings`, as the common configuration source.
Configuration belongs in settings or a reusable isolation-safe convention;
existing root DSL remains available in legacy mode.

The companion must:

- Establish common inputs before dependent project configuration consumes them.
- Use `gradle.lifecycle.beforeProject` with state-isolating actions to install
  local configuration, without capturing live `Settings`, other projects, or
  mutable extensions.
- Supply the necessary implementation classpath from the settings entry point.
- Use immutable hierarchy/path metadata to support nested and leaf projects.
- Require the rollout flag, and diagnose missing adoption configuration when the
  opted-in project plugin needs it.

Each project assigns its own version, stage, status, and publication metadata.
Root and children must receive equivalent common inputs regardless of
configuration order or whether root configuration is reused from cache.

The common model must distinguish version-only projects from release
participants. Installing local version support must not automatically apply the
full release plugin, build Java projects, or publish additional projects.

The public settings DSL, its freeze point, and the mapping from existing root
extension properties are prototype deliverables. Examples must be added only
after that surface is validated.

### 3. Root orchestration and local forwarding tasks

Keep release coordination and tag creation/pushing at the root. Introduce local
forwarding tasks, provisionally `nebulaReleaseBuild` and
`nebulaReleaseDescriptors`, in each included project.

Configure forwarding dependencies using only local plugin, task, and publication
state. Leave tasks empty for projects that do not participate. Root orchestration
references explicit task paths rather than opening another project's task
container or requiring children to mutate root tasks.

```text
root release
  -> :child:nebulaReleaseBuild
       -> local build, if that leaf participates

root postRelease
  -> root release
  -> :child:nebulaReleaseDescriptors
       -> local POM, Ivy, or supported Artifactory work
```

Preserve existing leaf selection, including nested aggregators with Java and the
single-project root-as-leaf case. An extra forwarding task must not change which
builds, publications, or descriptors run.

Replace `taskGraph.whenReady` inspection with explicit workflow inputs and local
task wiring. Preserve snapshot skipping, remote-check policy, setup tasks,
last-tag behavior, and supported task invocation forms. Do not introduce a new
ordering guarantee between publication and tagging; retain required legacy
dependency and ordering relationships.

### 4. Git tracking, shared state, and timestamps

Retain tracked Git reads through appropriate providers/value sources and task
inputs. Tag creation, fetching, and pushing remain task execution effects.

A build service may memoize Git reads or immutable calculations from explicit
inputs. It must not act as a mailbox containing a root `Project`, a live root
extension, or a result whose availability depends on root configuration running
first. Memoization must be thread-safe and must not hide cache invalidation.

All projects must use the same immutable-snapshot timestamp and version. Reading
the clock separately during each project's inference is insufficient. Specify
the timestamp's capture, persistence, and invalidation policy in the prototype:
reused and reconfigured projects must never disagree about the common version.

### 5. Customization and diagnostics

Continue supporting arbitrary existing strategies on the legacy path. Initially,
the isolated path supports built-in strategies and only explicitly validated
isolation-safe adapters. Arbitrary custom strategies, tag callbacks, or task
hooks must not be advertised as supported without evidence.

For unsupported configuration, report the project, affected customization, why
it cannot be used, and the supported migration route. Diagnostics must cover
known unsupported strategy registration, conflicting common/local inputs,
missing settings setup, and attempts to alter frozen common configuration.

Preserve meaningful failures such as invalid versions, direct assignment of
`project.version`, dirty or invalid branches, and a missing required root
coordinator. Do not suppress isolation errors, ignore customization, or weaken
validation to make a build pass.

## Initial support boundary

The completed initial implementation must cover built-in release behavior for
single, flat multi-project, and nested builds; Maven/Ivy descriptor integration;
version-only projects; and explicitly configured release participants.

Native Maven/Ivy publishing hooks are supported for the full and base
coordinators. Configure publications locally and use explicit task paths with
`dependsOn`/`finalizedBy` on root `release`. Dependencies publish before release;
finalizers publish after it, including skipped or failed execution. Last-tag
reuse does not suppress these hooks. This preserves Gradle scheduling rather
than adding success-only finalization. Direct descriptor requests do not publish,
and version-only projects are not added to publishing automatically.

Artifactory support requires an explicit tested version range. Its compatibility
cannot be inferred from Maven/Ivy tests or from the release plugin alone. Real
plugin characterization on Gradle 9.0/9.5 establishes this boundary:

| Integration | Ordinary strict cache | Strict Isolated Projects | Release support |
| --- | --- | --- | --- |
| Native Maven/Ivy task hooks | Publishing and warm reuse covered | Publishing and warm reuse covered | Explicit dependencies/finalizers in full and base modes. |
| Artifactory 6.0.0 and 6.0.4 alone | `help` stores and reuses cache | `help` fails on cross-project configuration, state, callbacks, and tasks | No supported Artifactory range established; active full mode rejects adoption with migration guidance. |

The Artifactory tests apply the real Maven Central artifacts without the release
plugin first, then verify release adoption diagnostics in either plugin order.
They do not claim Artifactory publication or execution-cache compatibility.
Source inspection of [Artifactory 6.0.4](https://github.com/jfrog/artifactory-gradle-plugin/blob/405eac9627c7c0cfc83da910724d08a8c99e13ae/src/main/java/org/jfrog/gradle/plugin/artifactory/ArtifactoryPlugin.java)
also shows root-to-child configuration access. A release-side task adapter cannot
remove those accesses. Upstream compatibility is required before adding a
supported version range. Consumers can use native Maven/Ivy hooks or retain the
legacy release path with Gradle isolation disabled for Artifactory builds.

Composite builds, independent release trains within one build, arbitrary
cross-project consumer scripts, and unvalidated custom strategy/hook adapters
are outside the initial guarantee. Existing supported behavior remains available
with the flag disabled.

## Validation

### Equivalence harness

Run equivalent legacy and adopted fixtures in separate local Git worktrees and
remotes. Tags, dirty state, and publication outputs from one run must not affect
the other. Use local artifact repositories and Git remotes.

For time-dependent versions, give both fixtures the same controlled inference
timestamp through an internal test seam. Also test real clock progression across
precision boundaries and partial cache reuse; identical fixture timestamps must
not conceal inconsistent production timestamp handling.

Compare observable behavior:

- Version strings, JAR names, POM/Ivy versions, and Ivy status.
- Participating tasks and required dependency/ordering relationships.
- Tag names, messages, target commits, pushed refs, and absence of duplicate tags.
- Expected validation failures and snapshot/last-tag skipping behavior.

Do not compare incidental log ordering or nondeterministic tag-object timestamps.

### Required coverage

| Dimension | Cases |
| --- | --- |
| Mode | Legacy; flag enabled with ordinary cache; flag enabled with isolation. |
| Cache | No cache baseline; strict cold storage; warm reuse; input invalidation; partial project reuse. |
| Layout | Single project; siblings; nested projects; Java aggregators; root as leaf; version-only and mixed participants. |
| Workflow | Default build, `devSnapshot`, `snapshot`, `immutableSnapshot`, `candidate`, `final`, setup tasks, `release`, and `postRelease`. |
| Invocation | Qualified paths and supported aliases/abbreviations; multiple requested tasks; changed task order and parallel configuration. |
| Version inputs | Major/minor/patch scope, explicit version, last tag, sanitization, branch rules, tag configuration, and timestamp precision. |
| Git | Custom Git root, no repository, no commits, changed HEAD/tags/branch/dirty state, behind-remote checks, and skip controls. |
| Integration | Maven/Ivy descriptors and status, declared Artifactory versions, supported `dependsOn`/`finalizedBy` hooks. |
| Compatibility | Plugin IDs, published signatures, legacy custom strategies/root DSL, flag toggling in one daemon, and adoption diagnostics. |

Parameterize representative scenarios rather than multiplying every dimension
indiscriminately. Test on the repository's minimum and current supported Gradle
versions and supported JDKs; select the isolation switch accepted by each Gradle
version. Newer Gradle coverage is additional evidence, not a replacement.

Require zero isolation/configuration-cache problems, strict failure mode, cold
storage, and warm reuse. Verify changes to common settings, project configuration,
release properties, and Git facts invalidate affected state and update results.
Specifically exercise children reconfiguring while root configuration is reused.

Existing expected-failure isolation characterizations are diagnostic coverage.
Convert each to successful behavior/cache assertions as its blocker is fixed.
Retain legacy unit, integration, and API compatibility coverage.

## Delivery milestones

1. **Architecture prototype:** flat multi-project snapshot with the flag, common
   version inputs, and forwarding tasks. Demonstrate artifact/participation
   equivalence, cold/warm cache behavior, partial reuse, and timestamp consistency.
   Finalize or revise the settings DSL, freeze point, and state model from this
   evidence before expanding implementation.
2. **Built-in inference and layouts:** nested/leaf behavior, version-only projects,
   tracked Git/property inputs, overrides, branch metadata, and common timestamps.
3. **Release and integrations:** real candidate/final tagging and pushing to local
   remotes, preparation/remote checks, last-tag behavior, Maven/Ivy, and the agreed
   Artifactory range. Artifactory 6.0.0/6.0.4 are characterized as unsupported;
   this part of the milestone remains dependent on upstream compatibility.
4. **Compatibility and rollout:** customization diagnostics/adapters, full
   regression and API checks, supported version matrix, and public adoption
   instructions. Advertise only the workflows demonstrated by successful tests.

Planning estimate: a 2–3 engineer-day prototype and approximately 3–5
engineer-weeks total for the defined initial scope, including validation. This is
a preliminary estimate, not a delivery commitment. Settings/input-freezing
feasibility, partial-cache timestamp semantics, Artifactory compatibility, and
custom adapter scope may change it. Adding the flag is small; maintaining and
validating both execution paths is part of the larger cost.

## Acceptance criteria

- Flag-disabled consumers require no build/settings changes and retain supported
  APIs, lazy configuration behavior, release outcomes, and validation.
- Flag-enabled supported workflows have equivalent versions, artifacts,
  statuses, participation, and Git effects with strict Isolated Projects.
- Cache storage, reuse, invalidation, partial reuse, and configuration-order
  independence are demonstrated without a mutable root-to-child handoff.
- Immutable-snapshot versions remain consistent across all affected projects.
- Tagging/pushing stays root-owned, occurs only when required, and does not add
  child builds or publications.
- New public configuration is additive. Unsupported customization and
  integrations receive actionable diagnostics without silently discarded state.
- Supported Gradle/JDK/integration versions and adoption steps are documented;
  no general compatibility claim precedes successful workflow evidence.

## Public references

- [Gradle Isolated Projects](https://docs.gradle.org/current/userguide/isolated_projects.html)
- [Gradle Configuration Cache](https://docs.gradle.org/current/userguide/configuration_cache.html)
- [Dependency-recommender opt-in adoption example](https://github.com/nebula-plugins/nebula-dependency-recommender-plugin/pull/162)
