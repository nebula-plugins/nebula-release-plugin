package nebula.plugin.release

import nebula.plugin.release.git.opinion.TimestampPrecision
import org.gradle.api.Project

class FeatureFlags {
    public static final String NEBULA_RELEASE_REPLACE_DEV_SNAPSHOT_WITH_IMMUTABLE_SNAPSHOT = "nebula.release.features.replaceDevWithImmutableSnapshot"
    public static final String NEBULA_RELEASE_IMMUTABLE_SNAPSHOT_TIMESTAMP_PRECISION = "nebula.release.features.immutableSnapshot.timestampPrecision"

    static boolean isDevSnapshotReplacementEnabled(Project project) {
        return project.findProperty(NEBULA_RELEASE_REPLACE_DEV_SNAPSHOT_WITH_IMMUTABLE_SNAPSHOT)?.toString()?.toBoolean()
    }

    static TimestampPrecision immutableSnapshotTimestampPrecision(Project project) {
        return project.hasProperty(NEBULA_RELEASE_IMMUTABLE_SNAPSHOT_TIMESTAMP_PRECISION) ?
                TimestampPrecision.from(project.findProperty(NEBULA_RELEASE_IMMUTABLE_SNAPSHOT_TIMESTAMP_PRECISION).toString())
                : TimestampPrecision.MINUTES
    }
}
