package nebula.plugin.release.util

import org.gradle.api.Project

class ReleaseTasksUtil {

    static final String SNAPSHOT_TASK_NAME = 'snapshot'
    static final String SNAPSHOT_TASK_NAME_OPTIONAL_COLON = ":$SNAPSHOT_TASK_NAME"
    static final String SNAPSHOT_SETUP_TASK_NAME = 'snapshotSetup'
    static final String DEV_SNAPSHOT_TASK_NAME = 'devSnapshot'
    static final String DEV_SNAPSHOT_SETUP_TASK_NAME = 'devSnapshotSetup'
    static final String DEV_SNAPSHOT_TASK_NAME_OPTIONAL_COLON = ":$DEV_SNAPSHOT_TASK_NAME"
    static final String DEV_SNAPSHOT_SETUP_TASK_NAME_OPTIONAL_COLON = ":$DEV_SNAPSHOT_SETUP_TASK_NAME"
    static final String IMMUTABLE_SNAPSHOT_TASK_NAME = 'immutableSnapshot'
    static final String IMMUTABLE_SNAPSHOT_SETUP_TASK_NAME = 'immutableSnapshotSetup'
    static final String IMMUTABLE_SNAPSHOT_TASK_NAME_OPTIONAL_COLON = ":$IMMUTABLE_SNAPSHOT_TASK_NAME"
    static final String CANDIDATE_TASK_NAME = 'candidate'
    static final String CANDIDATE_TASK_NAME_OPTIONAL_COLON = ":$CANDIDATE_TASK_NAME"
    static final String CANDIDATE_SETUP_TASK_NAME = 'candidateSetup'
    static final String FINAL_TASK_NAME = 'final'
    static final String FINAL_TASK_NAME_WITH_OPTIONAL_COLON = ":$FINAL_TASK_NAME"
    static final String FINAL_SETUP_TASK_NAME = 'finalSetup'
    static final String RELEASE_CHECK_TASK_NAME = 'releaseCheck'
    static final String NEBULA_RELEASE_EXTENSION_NAME = 'nebulaRelease'
    static final String POST_RELEASE_TASK_NAME = 'postRelease'
    static final String USE_LAST_TAG_PROPERTY = 'release.useLastTag'

    static boolean isUsingLatestTag(Project project) {
       return project.hasProperty(USE_LAST_TAG_PROPERTY) && project.property(USE_LAST_TAG_PROPERTY).toString().toBoolean()
    }

    static boolean isReleaseTaskThatRequiresTagging(List<String> cliTasks) {
        def hasCandidate = cliTasks.contains(CANDIDATE_TASK_NAME) || cliTasks.contains(CANDIDATE_TASK_NAME_OPTIONAL_COLON)
        def hasFinal = cliTasks.contains(FINAL_TASK_NAME) || cliTasks.contains(FINAL_TASK_NAME_WITH_OPTIONAL_COLON)
        return hasCandidate || hasFinal
    }
}
