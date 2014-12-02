package nebula.plugins.release

class ReleaseExtension {
    Set<String> releaseBranchPatterns = [/master/, /(release(-|\/))?\d+\.x/] as Set
    Set<String> excludeBranchPatterns = [] as Set

    void addReleaseBranchPattern(String pattern) {
        releaseBranchPatterns.add(pattern)
    }

    void addExcludeBranchPattern(String pattern) {
        excludeBranchPatterns.add(pattern)
    }
}
