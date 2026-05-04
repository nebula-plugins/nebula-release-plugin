package com.netflix.nebula.release;

import nebula.test.dsl.Gradle;

public enum SupportedGradleVersion {
    MIN(Gradle.ofVersion("9.0.0")), CURRENT(Gradle.current());

    public final Gradle version;

    SupportedGradleVersion(Gradle version) {
        this.version = version;
    }
}
