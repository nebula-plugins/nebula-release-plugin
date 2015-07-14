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