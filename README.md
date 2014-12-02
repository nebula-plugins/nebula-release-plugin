nebula-release-plugin
=====================

This plugin provides opinions and tasks for the release process provided by [gradle-git](https://github.com/ajoberstar/gradle-git).

# Applying the plugin

    plugins {
        id "nebula.nebula-release" version "2.2.0"
    }

-or-

    buildscripts {
        repositories { jcenter() }
        dependencies {
            classpath "com.netflix.nebula:nebula-release-plugin:2.2.0"
        }
    }
    apply plugin: "nebula.nebula-release"

# Optional Configuration

If you want the release plugin to trigger or finalize a publishing task you will need to configure it

    tasks.release.dependsOn tasks.<publish task name>

-or-

    tasks.release.finalizedBy tasks.<publish task name>

# Opinions

Project using this plugin will use [semantic versions](http://semver.org/) style strings. e.g. `major.minor.patch-<prerelease>+<metadata>`

All tasks default to bumping the minor version.

# Tasks Provided

All tasks will trigger gradle-git's release task which is configured to depend on the build task if the project produces JVM based jar or war artifacts.

* final - Sets the version to the appropriate `<major>.<minor>.<patch>`, creates tag `v<major>.<minor>.<patch>`
* candidate - Sets the version to the appropriate `<major>.<minor>.<patch>-rc.#`, creates tag `v<major>.<minor>.<patch>-rc.#` where `#` is the number of release candidates for this version produced so far. 1st 1.0.0 will be 1.0.0-rc.1, 2nd 1.0.0-rc.2 and so on.
* devSnapshot - Sets the version to the appropriate `<major>.<minor>.<patch>-dev.#+<hash>`, does not create a tag. Where `#` is the number of commits since the last release and `hash` is the git hash of the current commit.
* snapshot - Sets the version to the appropriate `<major>.<minor>.<patch>-SNAPSHOT`, does not create a tag.

# Extension Provided

    nebulaRelease {
      Set<String> releaseBranchPatterns = [/master/, /(release(-|\/))?\d+\.x/] as Set
      Set<String> excludeBranchPatterns = [] as Set

      void addReleaseBranchPattern(String pattern)
      void addExcludeBranchPattern(String pattern)
    }

* releaseBranchPatterns - is a field which takes a `Set<String>` it defaults to including master and any branch that matches the release pattern `(release(-|\/))?\d+\.x` e.g. `1.x` or `release/2.x` or `release-42.x`, if this is set to the empty set it will accept any branch name not in the `excludeBranchPatterns` set
* excludeBranchPatterns - is a field which takes a `Set<String>`, if the current branch matches a pattern in this set a release will fail, this defaults to the empty Set,
* addReleaseBranchPattern - is a method which takes a String, calling this method will add a pattern to the set of acceptable releaseBranchPatterns, usage: `nebulaRelease { addReleaseBranchPattern(/myregex/)`
* addExcludeBranchPattern - is a method which takes a String, calling this method will add a pattern to the set of unacceptable excludeBranchPatterns, usage: `nebulaRelease { addExcludeBranchPattern(/myregex/)`

# Releasing: Bumping major or patch versions

There are many cases where a project may want to bump a part of the version string besides the minor number.

* *bump the major number*: `./gradlew <snapshot|devSnapshot|candidate|final> -Prelease.scope=major`
* *bump the patch number*: `./gradlew <snapshot|devSnapshot|candidate|final> -Prelease.scope=patch`

# Caveats

### First release with this plugin

If this is the first time releasing with this plugin and you've released in the past you may need to create a tag in your repository. You should find the hash of your latest release and create a tag of the format `v<major>.<minor>.<patch>` e.g. `v1.4.2`

### Initial version not 0.1.0 or 1.0.0

Options:

1. Use your existing publish methods and set the version to what you want to release with and manually create the tag.
2. Create a dummy tag with a value one less than you want to release with. Example, I want to start releasing at 1.4.0 create a tag `v1.3.0`. Another example, I want to start release 2.0.0 so I create a tag `v1.0.0`
