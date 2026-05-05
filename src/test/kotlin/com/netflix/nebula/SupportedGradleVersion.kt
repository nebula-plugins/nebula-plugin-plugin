package com.netflix.nebula

import nebula.test.dsl.Gradle

enum class SupportedGradleVersion(val version: Gradle) {
    MIN(Gradle.ofVersion("9.0.0")),
    CURRENT(Gradle.current())
}