import org.gradle.plugin.compatibility.compatibility

/*
 * Copyright 2015-2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
plugins {
    id("com.netflix.nebula.plugin-plugin")
    `kotlin-dsl`
}

description = "Project plugin for Nebula plugins"

contacts {
    addPerson("nebula-plugins-oss@netflix.com") {
        moniker = "Nebula Plugins Maintainers"
        github = "nebula-plugins"
    }
}

dependencies {
    compileOnly("io.github.gradle-nexus:publish-plugin:2.0.0")
    implementation("com.netflix.nebula:nebula-archrules-gradle-plugin:1.+")
    implementation("com.netflix.nebula:nebula-oss-publishing-plugin:latest.release")
    implementation("com.netflix.nebula:gradle-contacts-plugin:latest.release")
    implementation("com.netflix.nebula:gradle-dependency-lock-plugin:latest.release")
    implementation("com.netflix.nebula:gradle-info-plugin:latest.release")
    implementation("com.netflix.nebula:gradle-java-cross-compile-plugin:latest.release")
    implementation("com.netflix.nebula:nebula-publishing-plugin:latest.release")
    implementation("com.netflix.nebula:nebula-project-plugin:latest.release")
    implementation("com.netflix.nebula:nebula-release-plugin:latest.release")
    implementation("com.netflix.nebula:nebula-gradle-interop:latest.release")
    implementation("com.netflix.nebula:gradle-info-plugin:latest.release")
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.14.+"))

    implementation("com.gradle.publish:plugin-publish-plugin:2.+")
    implementation("org.gradle.plugin:compatibility-plugin:1.+")
    implementation("com.gradle.develocity:com.gradle.develocity.gradle.plugin:4.+")

    testImplementation("com.netflix.nebula:nebula-test:latest.release")
    testImplementation("org.ajoberstar.grgit:grgit-core:4.1.1") {
        exclude(group = "org.codehaus.groovy", module = "groovy")
    }
    testImplementation("org.mock-server:mockserver-netty:5.15.0")
}

gradlePlugin {
    plugins {
        create("pluginPlugin") {
            id = "com.netflix.nebula.plugin-plugin"
            displayName = "Nebula Plugin Plugin"
            description = "Sets up publishing and release process for all of the other nebula plugins"
            implementationClass = "nebula.plugin.plugin.NebulaPluginPlugin"
            tags.set(listOf("nebula", "nebula-plugin"))
        }
        create("com.netflix.nebula.library") {
            id = "com.netflix.nebula.library"
            displayName = "Nebula Library Plugin"
            description = "Sets up publishing and release process for Nebula Libraries"
            implementationClass = "nebula.plugin.plugin.NebulaLibraryPlugin"
            tags.set(listOf("nebula"))
        }
        create("com.netflix.nebula.root") {
            id = "com.netflix.nebula.root"
            displayName = "Nebula Root Plugin"
            description = "Sets up publishing and release process for Nebula Multiproject Repos"
            implementationClass = "nebula.plugin.plugin.NebulaRootPlugin"
            tags.set(listOf("nebula"))
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
        create("com.netflix.nebula.oss.settings") {
            id = "com.netflix.nebula.oss.settings"
            displayName = "Nebula OSS Settings Plugin"
            description = "Sets up build scans"
            implementationClass = "com.netflix.nebula.oss.settings.NebulaSettingsPlugin"
            tags.set(listOf("nebula"))
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
        create("com.netflix.nebula.resolve") {
            id = "com.netflix.nebula.resolve"
            displayName = "Nebula Dependency Resolve Plugin"
            description = "Used to trigger dependency resolution on all configurations of all projects"
            implementationClass = "com.netflix.nebula.convention.ResolvePlugin"
            tags.set(listOf("nebula"))
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter()
            targets.all {
                testTask.configure {
                    maxParallelForks = 2
                }
            }
        }
    }
}
kotlin {
    compilerOptions {
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
    }
}
tasks.wrapper {
    distributionType = Wrapper.DistributionType.ALL
    gradleVersion = "9.5.0"
    distributionSha256Sum = "a3c4ba4aca8f0075688b9c5b18939fd28e8cb4357c227da5c1d9f38343791439"
}