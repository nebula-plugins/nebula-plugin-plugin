Nebula Plugin Plugin
====================

![Support Status](https://img.shields.io/badge/nebula-internal-lightgray.svg)
[![Build Status](https://travis-ci.org/nebula-plugins/nebula-plugin-plugin.svg)](https://travis-ci.org/nebula-plugins/nebula-plugin-plugin)
[![Coverage Status](https://coveralls.io/repos/nebula-plugins/nebula-plugin-plugin/badge.svg?branch=master&service=github)](https://coveralls.io/github/nebula-plugins/nebula-plugin-plugin?branch=master)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/nebula-plugins/nebula-plugin-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
[![Apache 2.0](https://img.shields.io/github/license/nebula-plugins/nebula-plugin-plugin.svg)](http://www.apache.org/licenses/LICENSE-2.0)

Plugin to establish conventions for a nebula-plugins plugin, e.g. publishing, licenses. This plugin is used to help setup our other plugins. It can be used as an example of setting up similar conventions for an organization's gradle plugins.

![Yo Dawg](https://s3.amazonaws.com/uploads.hipchat.com/25234/334670/CgbXYbDuUzuV3JP/plugins.png)

Conventions
-----------
* Defaults group to com.netflix.nebula
* Applies nebula.maven-publish
* Applies and configures nebula.nebula-release and nebula.nebula-bintray

Usages
-----------

To apply this plugin if using Gradle 2.1 or newer


    plugins {
      id 'nebula.plugin-plugin' version '5.1.1'
    }

If using an older version of Gradle

    buildscript {
      repositories { jcenter() }
      dependencies {
        classpath 'com.netflix.nebula:nebula-plugin-plugin:5.1.1'
      }
    }

    apply plugin: 'nebula.plugin-plugin'
    
Gradle Compatibility Tested
---------------------------

* Built with Oracle JDK7
* Tested with Oracle JDK8

| Gradle Version | Works |
| :------------: | :---: |
| < 3.3          | no    |
| 3.3            | yes   |

LICENSE
=======

Copyright 2014-2017 Netflix, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

<http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
