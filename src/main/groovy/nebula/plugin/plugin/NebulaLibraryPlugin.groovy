/*
 * Copyright 2014-2021 Netflix, Inc.
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
package nebula.plugin.plugin

import io.github.gradlenexus.publishplugin.AbstractNexusStagingRepositoryTask
import nebula.plugin.publishing.NebulaOssPublishingExtension
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.plugin.devel.tasks.ValidatePlugins
import org.gradle.plugins.signing.Sign

import javax.inject.Inject

/**
 * Provide an environment for a plain java library in the Nebula ecosystem
 */
class NebulaLibraryPlugin implements Plugin<Project> {
    static final NEBULA_PLUGIN_IDS = ['com.netflix.nebula.contacts',
                                      'com.netflix.nebula.dependency-lock',
                                      'com.netflix.nebula.info',
                                      'com.netflix.nebula.javadoc-jar',
                                      'com.netflix.nebula.maven-apache-license',
                                      'com.netflix.nebula.maven-publish',
                                      'com.netflix.nebula.publish-verification',
                                      'com.netflix.nebula.release',
                                      'com.netflix.nebula.oss-publishing',
                                      'com.netflix.nebula.source-jar']

    static final PLUGIN_IDS = NEBULA_PLUGIN_IDS

    private final ProviderFactory providers

    @Inject
    NebulaLibraryPlugin(ProviderFactory providerFactory) {
        this.providers = providerFactory
    }

    @Override
    void apply(Project project) {
        project.plugins.apply("java-library")
        project.group = 'com.netflix.nebula'
        project.plugins.withId("com.netflix.nebula.oss-publishing") {
            NebulaOssPublishingExtension ossPublishingExt = project.rootProject.extensions.findByType(NebulaOssPublishingExtension)
            ossPublishingExt.packageGroup.set("com.netflix")
            ossPublishingExt.netflixOssRepository.set("gradle-plugins")
        }
        project.with {
            PLUGIN_IDS.each { plugins.apply(it) }
            tasks.withType(ValidatePlugins).configureEach {
                it.enableStricterValidation.set(true)
            }

            JavaPluginExtension javaPluginExtension = extensions.getByType(JavaPluginExtension)
            JavaToolchainSpec toolchainSpec = javaPluginExtension.toolchain
            toolchainSpec.languageVersion.convention(JavaLanguageVersion.of(17))

            repositories {
                mavenCentral()
            }

            tasks.withType(Test).configureEach {
                useJUnitPlatform()
            }

            Provider<String> jdkVersionForTestsEnvVariable = providers.environmentVariable("JDK_VERSION_FOR_TESTS")
            Integer jdkVersionForTests = jdkVersionForTestsEnvVariable.isPresent() ? jdkVersionForTestsEnvVariable.get().toInteger() : 21
            JavaToolchainService javaToolchainService = project.extensions.getByType(JavaToolchainService)
            tasks.withType(Test).configureEach { task ->
                minHeapSize = '32m'
                maxHeapSize = '256m'
                /*
                Allows to override the JDK used to execute the test process
                 */
                javaLauncher.set(javaToolchainService.launcherFor {
                    it.languageVersion.set(JavaLanguageVersion.of(jdkVersionForTests))
                })
                if(jdkVersionForTests < 17) {
                    systemProperty('ignoreDeprecations', true)
                }
                doFirst {
                    logger.lifecycle("Executing tests with JDK: ${jdkVersionForTests}")
                }
                testLogging {
                    events "PASSED", "FAILED", "SKIPPED"
                }
            }
        }

        project.afterEvaluate {
            /**
             * Configure signing
             */
            project.tasks.withType(Sign).configureEach {
                it.mustRunAfter(project.tasks.named('check'))
            }
        }
    }
}
