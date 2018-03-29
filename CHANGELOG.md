6.3.0 / 2018-02-08
==================

* Allow releasing a final release from older commits within a master branch even when there are newer commits tagged as candidates

6.2.0 / 2018-01-09
==================

* Handle ArtifactoryTask if using build-info-extractor 4.6.0 via @lavcraft (Kirill Tolkachev)

6.1.1 / 2017-12-12
==================

* Cleanup a stray println

6.1.0 / 2017-10-25
==================

* Add in capability for travis to specify a special branch e.g. 2.x or 3.2.x and produce properly versioned snapshots

6.0.2 / 2017-09-20
==================

* Remove optional dependencies so they don't leak onto the classpath when resolved from the plugin portal. It was causing bintray to be upgraded to a version that cannot be compiled against on Gradle 3 and later (only affects the plugin at compile time, not at runtime)

6.0.1 / 2017-09-06
==================

* Builds that fail with Final and candidate builds require all changes to be committed into Git. will now list the files that have not been committed.

6.0.0 / 2017-05-23
==================

* BREAKING: Java 8 is now required
* gradle-git upgraded to 1.7.1
* jgit transitively upgraded to 4.6.1.201703071140-r to address [Bug 498759: jgit shows submodule as modified path](https://bugs.eclipse.org/bugs/show_bug.cgi?id=498759)

5.0.0 / 2017-04-19
==================

* Add some tasks and rework task ordering
    * BREAKING: `snapshot`, `devSnapshot`, `candidate`, `final` are no longer finalized by release, they now depend on `release`
    * Added `snapshotSetup`, `devSnapshotSetup`, `candidateSetup`, `finalSetup` added if you need to run specific things early in the process
    * Added `postRelease` task for tasks you want to happen after release (which tags the repo), we have moved publishing to be called by this step

4.2.0 / 2017-03-31
==================

* Calculate version in repository with no commits
* Allow pushing tags from detached branch
* Better handle branch patterns that would error out semver when put in dev version

4.0.1 / 2016-02-05
==================

* Fix tasks task so it can run on multiprojects

4.0.0 / 2016-02-04
==================

* Potential BREAKING change: Removing assumptions on whether to enable bintray and artifactory publishes based on whether a user uses the `devSnapshot`, `snapshot`, `candidate`, or `final` tasks. These should be on more opinionated plugins.

3.2.0 / 2016-02-01
==================

* Use specific versions of dependencies to prevent dynamic versions in the gradle plugin portal
* upgrade nebula-bintray-plugin 3.2.0 -> 3.3.1
* upgrade gradle-gt plugin 1.3.2 -> 1.4.1

3.1.3 / 2016-01-28
==================

* Remove need for initial tag
* Better error message for release/[invalid semver pattern]

3.1.2 / 2015-12-09
==================

* Better error reporting for missing init tag and uncommitted changes

3.1.1 / 2015-12-08
==================

* Allow to customize the location of Git root 

3.1.0 / 2015-10-26
==================

* Update ivy status and project.status on publish
    * `devSnapshot` and `snapshot` will leave status at integration
    * `candidate` will set ivy and project status to candidate
    * `final` will set ivy and project status to release
* Also depend on artifactory tasks if creating devSnapshot
* Warn rather than fail when the project contains no git repository or the git repository has no commits.

3.0.5 / 2015-10-19
==================

* Republish correctly

3.0.4 / 2015-10-19
==================

* gradle-git to 1.3.2 // publishing issue

3.0.3 / 2015-10-06
==================

* gradle-git to 1.3.1

3.0.2 / 2015-09-18
==================

* BUGFIX: Allow release from rc tag

3.0.1 / 2015-09-09
==================

* BUGFIX: fix ordering so we don't release if tests are broken

3.0.0 / 2015-09-04
==================

* Move to gradle-git 1.3.0
* Plugin built against gradle 2.6
* New travis release process

2.2.7 / 2015-07-14
==================

* Move to gradle-git 1.2.0
* Only calculate version once for multiprojects

2.2.6 / 2015-06-18
==================

* Move to gradle-git 1.1.0

2.2.5 / 2015-02-09
==================

* Add ability to use major.minor.x branches along with major.x branches.
* Update nebula dependencies to newest releases on 2.2.x branches.

2.2.4 / 2015-01-19
==================

* Modify -Prelease.useLastTag so that it doesn't attempt to push tags to the remote

2.2.3 / 2014-12-11
==================

* Fix to still have `devSnapshot` task work if a user changes the default versioning strategy

2.2.2 / 2014-12-05
==================

* Minor change to allow users to configure the default versioning scheme via gradle-git's release extension

2.2.1 / 2014-12-05
==================

* Add nebula-release properties file so this can be used as a plugin
* rename package from nebula.plugins.release to nebula.plugin.release

2.2.0 / 2014-12-04 (removed from jcenter)
=========================================

* does not include META-INF/gradle-plugins properties file
* Initial release
* Skip straight to 2.2.x to show built and compatible with gradle 2.2
