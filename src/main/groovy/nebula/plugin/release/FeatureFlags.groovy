package nebula.plugin.release


import org.gradle.api.Project

class FeatureFlags {
    public static final String NEBULA_RELEASE_REPLACE_DEV_SNAPSHOT_WITH_IMMUTABLE_SNAPSHOT = "nebula.release.features.replaceDevWithImmutableSnapshot"

    static boolean isDevSnapshotReplacementEnabled(Project project) {
        return project.findProperty(NEBULA_RELEASE_REPLACE_DEV_SNAPSHOT_WITH_IMMUTABLE_SNAPSHOT)?.toString()?.toBoolean()
    }
}
