package nebula.plugin.release.git.base

class ShortenRefUtil {
    /** Prefix for branch refs */
    public static final String R_HEADS = "refs/heads/"

    /** Prefix for remotes refs */
    public static final String R_REMOTES = "refs/remotes/"

    /** Prefix for tag refs */
    public static final String R_TAGS = "refs/tags/"

    static String shortenRefName(String refName) {
        if (refName.startsWith(R_HEADS))
            return refName.substring(R_HEADS.length())
        if (refName.startsWith(R_TAGS))
            return refName.substring(R_TAGS.length())
        if (refName.startsWith(R_REMOTES))
            return refName.substring(R_REMOTES.length())
        return refName
    }
}
