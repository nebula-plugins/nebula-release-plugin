package nebula.plugin.release

import org.gradle.api.Project

class VersionSanitizerUtil {

    static final String SANITIZE_PROPERTY_NAME = 'release.sanitizeVersion'

    private static final String SANITIZE_REGEX = "[^A-Za-z0-9_.-]"
    private static final String SANITIZE_REPLACEMENT_CHAR = '.'

    static String sanitize(String version) {
        return version.replaceAll(SANITIZE_REGEX, SANITIZE_REPLACEMENT_CHAR)
    }

    static boolean hasSanitizeFlag(Project project) {
        if(!project.hasProperty(SANITIZE_PROPERTY_NAME)) {
            return false
        }

        return project.property(SANITIZE_PROPERTY_NAME)?.toBoolean()
    }
}
