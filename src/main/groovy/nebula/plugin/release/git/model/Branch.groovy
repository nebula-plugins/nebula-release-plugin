package nebula.plugin.release.git.model

import groovy.transform.Immutable
import nebula.plugin.release.git.base.ShortenRefUtil

@Immutable
class Branch {
    /**
     * The fully qualified name of this branch.
     */
    String fullName

    /**
     * The simple name of the branch.
     * @return the simple name
     */
    String getName() {
        return ShortenRefUtil.shortenRefName(fullName)
    }
}
