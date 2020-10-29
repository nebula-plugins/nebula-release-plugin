nebula-release-plugin
=====================

![Support Status](https://img.shields.io/badge/nebula-supported-brightgreen.svg)
[![Build Status](https://travis-ci.com/nebula-plugins/nebula-release-plugin.svg?branch=master)](https://travis-ci.com/nebula-plugins/nebula-release-plugin)
[![Coverage Status](https://coveralls.io/repos/nebula-plugins/nebula-release-plugin/badge.svg?branch=master&service=github)](https://coveralls.io/github/nebula-plugins/nebula-release-plugin?branch=master)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/nebula-plugins/nebula-release-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
[![Apache 2.0](https://img.shields.io/github/license/nebula-plugins/nebula-release-plugin.svg)](http://www.apache.org/licenses/LICENSE-2.0)

This plugin provides opinions and tasks for the release process provided by [gradle-git](https://github.com/ajoberstar/gradle-git).

# Applying the plugin

    plugins {
        id 'nebula.release' version '15.2.0'
    }

-or-

    buildscript {
        repositories { jcenter() }
        dependencies {
            classpath 'com.netflix.nebula:nebula-release-plugin:15.2.0'
        }
    }
    apply plugin: 'nebula.release'

# Optional Configuration

If you want the release plugin to trigger or finalize a publishing task you will need to configure it:

    tasks.release.dependsOn tasks.<publish task name>

-or-

    tasks.release.finalizedBy tasks.<publish task name>

This plugin also detects the presence of the [nebula-bintray-plugin](https://github.com/nebula-plugins/nebula-bintray-plugin)
and wires itself to depend on the task types used by `bintrayUpload` and `artifactoryUpload`.

# Opinions

Project using this plugin will use [semantic versions](http://semver.org/) style strings. e.g. `major.minor.patch-<prerelease>+<metadata>`

All tasks default to bumping the minor version.

# Extension Provided
    nebulaRelease {
      Set<String> releaseBranchPatterns = [/master/, /HEAD/, /main/, /(release(-|\/))?\d+(\.\d+)?\.x/, /v?\d+\.\d+\.\d+/] as Set
      Set<String> excludeBranchPatterns = [] as Set
      String shortenedBranchPattern = /(?:(?:bugfix|feature|hotfix|release)(?:-|\/))?(.+)/

      void addReleaseBranchPattern(String pattern)
      void addExcludeBranchPattern(String pattern)
    }

| Property                | Type          | Default                                                                   | Description                                                                                                                                                                                                                                                           |
| ----------------------- | ------------- | ------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| releaseBranchPatterns   | `Set<String>` | `[master, HEAD, main, (release(-\|\/))?\d+(\.\d+)?\.x, v?\d+\.\d+\.\d+]` | Branch patterns that are acceptable to release from. The default pattern will match things like `master`, `1.2.x`, `release-42.x`, `release/2.x`, `v1.2.3`. If the set is empty releases will be possible from any branch that doesn't match `excludeBranchPatterns`. |
| excludeBranchPatterns   | `Set<String>` | `[]`                                                                      | Branch patterns that you cannot release from. If a branch matches both `releaseBranchPatterns` and `excludeBranchPatterns` it will be excluded.                                                                                                                       |
| shortenedBranchPattern  | `String`      | `(?:(?:bugfix\|feature\|hotfix\|release)(?:-\|\/))?(.+)`                    | Branch `widget1` will append `widget1` to snapshot version numbers, and branch `(feature\|bugfix\|release\|hotfix)/widget2` will append `widget2` to snapshot version numbers. You may configure this field, the regex is expected to have exactly one capture group.    |


| Method                  | Arguments        | Description                                                                                                                               |
| ----------------------- | ---------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| addReleaseBranchPattern | `String pattern` | Calling this method will add a pattern to the set of `releaseBranchPatterns`, usage: `nebulaRelease { addReleaseBranchPattern(/myregex/)` |
| addExcludeBranchPattern | `String pattern` | Calling this method will add a pattern to the set of `excludeBranchPatterns`, usage: `nebulaRelease { addExcludeBranchPattern(/myregex/)` |

# Tasks Provided

All tasks will trigger gradle-git's release task which is configured to depend on the build task if the project produces JVM based jar or war artifacts.

* final - Sets the version to the appropriate `<major>.<minor>.<patch>`, creates tag `v<major>.<minor>.<patch>`
* candidate - Sets the version to the appropriate `<major>.<minor>.<patch>-rc.#`, creates tag `v<major>.<minor>.<patch>-rc.#` where `#` is the number of release candidates for this version produced so far. 1st 1.0.0 will be 1.0.0-rc.1, 2nd 1.0.0-rc.2 and so on.
* devSnapshot - Sets the version to the appropriate `<major>.<minor>.<patch>-dev.#+<hash>`, does not create a tag. Where `#` is the number of commits since the last release and `hash` is the git hash of the current commit.  If releasing a devSnapshot from a branch not listed in the `releaseBranchPatterns` and not excluded by `excludeBranchPatterns` the version will be `<major>.<minor>.<patch>-dev.#+<branchname>.<hash>`
  * You can use `nebula.release.features.replaceDevWithImmutableSnapshot=true` in your `gradle.properties` file to change pattern of version to `<major>.<minor>.<patch>-snapshot.<timestamp>+<hash>`. Where `timestamp` is UTC time in `YYYYMMddHHmm` format, ex. `201907052105`  and `hash` is the git hash of the current commit.  If releasing a immutableSnapshot from a branch not listed in the `releaseBranchPatterns` and not excluded by `excludeBranchPatterns` the version will be `<major>.<minor>.<patch>-snapshot.<timestamp>+<branchname>.<hash>`
* snapshot - Sets the version to the appropriate `<major>.<minor>.<patch>-SNAPSHOT`, does not create a tag.

Use of devSnapshot vs snapshot is a project by project choice of whether you want maven style versioning (snapshot) or semantic versioned snapshots (devSnapshot).

# Versioning Notes

We attempt to pick up on the fact that you're on certain release branches.

Examples:

* On release/1.x - The plugin will default to versioning you 1.0.0-SNAPSHOT
* On 3.x - The plugin will default to versioning you 3.0.0-SNAPSHOT
* On 4.2.x - The plugin will default to versioning you 4.2.0-SNAPSHOT

# Releasing: Bumping major or patch versions

There are many cases where a project may want to bump a part of the version string besides the minor number.

* *bump the major number*: `./gradlew <snapshot|devSnapshot|candidate|final> -Prelease.scope=major`
* *bump the patch number*: `./gradlew <snapshot|devSnapshot|candidate|final> -Prelease.scope=patch`

# Releasing: Using last tag

In the scenario, where the tag is already created but you want to go through the release process, you can use the "Last Tag Strategy".
When enabled, the plugin will respect the last tag as the version. This works well in a CI environment where a user will tag the code, and
CI is configured to do just the release upon finding a new tag. The tag names need to follow the versioning scheme, e.g. "v1.0.1". To enact,
provide the release.useLastTag project property, e.g.

    git tag v1.0.0
    ./gradlew -Prelease.useLastTag=true final

# Overriding version from the command line

To set the version from the command line, set the release.version system property:

    ./gradlew -Prelease.version=1.2.3 final

# Caveats

### First release with this plugin

If this is the first time releasing with this plugin and you've released in the past you may need to create a tag in your repository. You should find the hash of your latest release and create a tag of the format `v<major>.<minor>.<patch>` e.g. `v1.4.2`. For example, if the hash of your last release was `da5d4f9`:

    git tag v1.4.2 da5d4f9
    git push --tags
    ./gradlew final

### Initial version not 0.1.0 or 1.0.0

This will create a tag `v<string>` where String is whatever you set on `release.version`. If you want the plugin to work from here on out you should choose a version that matches semantic versioning described above.

    ./gradlew -Prelease.version=42.5.0 final

### Git root is in a different location from Gradle root

The plugin assumes Git root is in the same location as Gradle root. If this isn't the case, you may specify a different path for Git root via the `git.root` Gradle property. For example:

    ./gradlew -Pgit.root=<git root path> final

### Sanitize versions

Historically, this plugin generated versions using build metadata which comes after the `+` sign. While we suggest using this approach to be semver compliant, some people has raised their need for removing the build medata and make it part of the pre-release string.

For this, starting on version `11.0.0` we have introduced a `sanitizeVersion` flag. Ex.

`./gradlew -Prelease.sanitizeVersion=true devSnapshot`

This will generate a version string similar to: `0.1.0-dev.2.e1c43c7`

Please note that now the build metadata is part of the pre-release information and could affect how versions are handled by a SemVer library/tool. 


# Lifecycle Hooks

### For `devSnapshot`

* `devSnapshotSetup` - any kind of setup is good to live around here, e.g. setting status, various checks
* `release` - probably have `release` dependOn any `assemble` and `check` tasks, dependsOn `devSnapshotSetup`
* `postRelease` - any steps you want to happen after the repo tag, in our case this is where publishing happens since if the publish partially fails we don't want to fix the tags, dependsOn `release`
* `devSnapshot` - command line task to kick off devSnapshot workflow, dependsOn `postRelease`

### For `snapshot`

* `snapshotSetup` - any kind of setup is good to live around here, e.g. setting status, various checks
* `release` - probably have `release` dependOn any `assemble` and `check` tasks, dependsOn `snapshotSetup`
* `postRelease` - any steps you want to happen after the repo tag, in our case this is where publishing happens since if the publish partially fails we don't want to fix the tags, dependsOn `release`
* `snapshot` - command line task to kick off snapshot workflow, dependsOn `postRelease`

### For `candidate`

* `candidateSetup` - any kind of setup is good to live around here, e.g. setting status, various checks
* `release` - this is where the tag is pushed to the repo, so probably have `release` dependOn any `assemble` and `check` tasks, dependsOn `candidateSetup`
* `postRelease` - any steps you want to happen after the repo tag, in our case this is where publishing happens since if the publish partially fails we don't want to fix the tags, dependsOn `release`
* `candidate` - command line task to kick off candidate workflow, dependsOn `postRelease`

### For `final`

* `finalSetup` - any kind of setup is good to live around here, e.g. setting status, various checks
* `release` - this is where the tag is pushed to the repo, so probably have `release` dependOn any `assemble` and `check` tasks, dependsOn `finalSetup`
* `postRelease` - any steps you want to happen after the repo tag, in our case this is where publishing happens since if the publish partially fails we don't want to fix the tags, dependsOn `release`
* `final` - command line task to kick off final workflow, dependsOn `postRelease`

Gradle and Java Compatibility
=============================

Built with OpenJDK8
Tested with OpenJDK8

| Gradle Version | Works |
| :------------: | :---: |
| 5.0            | yes   |
| 5.1            | yes   |
| 5.2            | yes   |
| 5.3            | yes   |
| 5.4            | yes   |
| 5.5            | yes   |

LICENSE
=======

Copyright 2014-2019 Netflix, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

<http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
