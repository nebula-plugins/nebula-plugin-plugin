package com.netflix.nebula.oss.settings

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.kotlin.dsl.develocity

/**
 * Configure develocity
 */
class NebulaSettingsPlugin : Plugin<Settings> {

    @Override
    override fun apply(settings: Settings) {
        settings.pluginManager.apply("com.gradle.develocity")
        settings.plugins.withId("com.gradle.develocity") {
            val terms = settings.providers.gradleProperty("nebula.buildScanTerms")
                .map {it.toBoolean() }
                .getOrElse(false)
            if (terms) {
                settings.develocity {
                    buildScan {
                        termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
                        termsOfUseAgree.set("yes")
                    }
                }
            }
        }
        settings.gradle.allprojects {
            pluginManager.apply("com.netflix.nebula.resolve")
        }
    }
}