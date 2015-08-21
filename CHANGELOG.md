3.1.0 / 2015-08-21
==================

* switch publication to be nebula instead of mavenNebula
* move to gradle 2.6
* move to nebula.release version 2.2.7

3.0.5 / 2015-08-17
==================

* Switch order of plugins to fix ordering issue until we can get back around to nebula-bintray

3.0.4 / 2015-08-14
==================

* Fix repo in plugins block of build.gradle

3.0.3 / 2015-08-14
==================

* Use correct keys for plugins.gradle.org

3.0.2 / 2015-08-14
==================

* Apply nebula.apache-license-pom to allow sync with maven central

3.0.1 / 2015-08-14
==================

* Fix to some publishing steps so that this will sync with maven central

3.0.0 / 2015-08-12
==================

* Simplify to just setting up and wiring publishing via travis through bintray.

2.2.0 / 2015-01-30
==================

* Move to gradle 2.2.1

2.0.1 / 2014-10-16
==================

* Update nebula-bintray and nebula-test versions
* Gets gradle-bintray-plugin 0.6

2.0.0 / 2014-09-15
==================

* Update to gradle-2.0

1.12.12 / 2014-10-16
====================

* Update nebula-project and nebula-bintray plugin versions

1.12.11 / 2014-09-15
====================

* Updating nebula-project-plugin version

1.12.10 / 2014-09-12
====================

* Defer url and scm calculation to publishing plugin

1.12.9 / 2014-08-22
===================

* Fix signing order

1.12.8 / 2014-08-22
===================

* Update nebula-publishing plugin version

1.12.7 / 2014-08-21
===================

* Fix ordering of preparePublish

1.12.6 / 2014-08-21
===================

* Add preparePublish

1.12.5 / 2014-08-21
===================

* Signing fix

1.12.4 / 2014-08-15
===================

* Internal test cleanup
* Extract bintray functionality to [nebula-bintray-plugin](https://github.com/nebula-plugins/nebula-bintray-plugin)

1.12.3 / 2014-07-02
===================

* Fix maven publication name
* nebula-project-plugin to 1.12.7

1.12.2 / 2014-06-11
===================

* Using updated nebula plugins
* Correct spelling of gradle-plugin attribute

1.12.1 / 2014-06-11
===================

* Fully qualified plugin names
* Add bintray attibutes

1.12.0 / 2014-06-09
===================

* Upgrade to gradle 1.12

1.9.12 / 2014-04-21
===================

* switch to dynamic versions of dependencies
* use gradle-dependency-lock to lock them to specific version
* pull in nebula-test 1.9.8

1.9.11 / 2014-04-14
===================

* Bump versions, and shifting publishing plugins to project plugin. Which also incorporates contacts and dependency-lock plugin

1.9.10 / 2014-04-08
===================

* Bumping info plugin to 1.9.3 and nebula-test to 1.9.4

1.9.9 / 2014-03-20
==================

* re-releasing using our last release

1.9.8 / 2014-03-20
==================

* Using gradle-info-plugin

1.9.7 / 2014-03-13
==================

* Some type info to help debug

1.9.6 / 2014-01-27
==================

* Use query parameters for central sync
* Introduce nebula-bintray-sync-plugin, to enhance nebula-bintray-plugin to enable maven central syncing

1.9.5 / 2014-01-21
==================

* Bumping to 1.9.5 which has name pom fix

1.9.4 / 2014-01-21
==================

* Following through with 1.9.3 ripple

1.9.3 / 2014-01-20
==================

* Avoid dynamic versions until bintray is fixed

1.9.2 / 2014-01-17
=================

* Add nebula-sign plugin

1.9.1 / 2014-01-14
=================

* Add com.netflix.nebula:nebula-core to the compile classpath
* Start work on a better way to store the version of nebula-core and nebula-test
* Bintray releases are automatically released now

1.9.0 / 2014-01-10
=================

* Initial release
