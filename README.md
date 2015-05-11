Nebula Plugin Plugin
====================
Plugin to establish conventions for a nebula-plugins plugin, e.g. publishing, licenses. The goal is to require the absolute minimum configuration to 
get start writing a plugin, without sacrificing known best practices.

![Yo Dawg](https://s3.amazonaws.com/uploads.hipchat.com/25234/334670/CgbXYbDuUzuV3JP/plugins.png)

Conventions
-----------
* Defaults group to com.netflix.nebula
* Adds gradleApi() and localGroovy()
* Applies nebula-project (to build sources, tests, javadoc), nebula-publishing (resolved versions and excludes in the pom)
* Via netflix-test, provides framework for project tests and GradleLauncher tests
* Provides sensible POM values for scm, url, license
* Sets up a configurable createWrapper task
* Create a testLocal task run tests which have Local in their name
* Populate MANIFEST with the version of Gradle used, Implementation-Title, Specification-Version
* Configure com.github.townsfolk:gradle-release
* Publish to Bintray for consuption in jcenter

Usages
-----------
```
buildscript {
    repositories { jcenter() }
    dependencies { classpath "com.netflix.nebula:nebula-plugin-plugin:2.4.+" }
}

description 'Example Gradle plugin'
apply plugin: 'nebula-plugin'

dependencies {
    // will get nebula-test into testCompile
}
```
