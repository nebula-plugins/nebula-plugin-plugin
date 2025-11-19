Nebula Plugin Plugin
====================

![Support Status](https://img.shields.io/badge/nebula-active-green.svg)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/com.netflix.nebula.plugin-plugin?style=for-the-badge&color=01AF01)](https://plugins.gradle.org/plugin/com.netflix.nebula.plugin-plugin)
[![Maven Central](https://img.shields.io/maven-central/v/com.netflix.nebula/nebula-plugin-plugin)](https://maven-badges.herokuapp.com/maven-central/com.netflix.nebula/nebula-plugin-plugin)
![Build](https://github.com/nebula-plugins/nebula-plugin-plugin/actions/workflows/build.yml/badge.svg)
[![Apache 2.0](https://img.shields.io/github/license/nebula-plugins/nebula-hollpluginow-plugin.svg)](http://www.apache.org/licenses/LICENSE-2.0)

Plugin to establish conventions for a nebula-plugins plugin, e.g. publishing, licenses. This plugin is used to help setup our other plugins. It can be used as an example of setting up similar conventions for an organization's gradle plugins.


Conventions
-----------
* Defaults group to com.netflix.nebula
* Applies nebula.maven-publish
* Applies and configures nebula.nebula-release and nebula.nebula-bintray

Usages
-----------

To apply this plugin 


    plugins {
      id 'com.netflix.nebula.plugin-plugin' version '<current version>'
    }


### Multi-project Repo

To use this plugin in a multiproject repo, apply this root plugin to the root project:
```kotlin
plugins {
    id("com.netflix.nebula.root")
}
```
Then, in each subproject, if it is a plugin project, apply the plugin plugin:
```kotlin
plugins {
    id("com.netflix.nebula.plugin-plugin")
}
```
Otherwise, if it is a plain Java library project, apply the library plugin:
```kotlin
plugins {
    id("com.netflix.nebula.library")
}
```
Note that the library plugin is designed to work only in subprojects. If you want to generate a single-project Nebula library, apply both the root plugin and library plugin to the root project.

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
