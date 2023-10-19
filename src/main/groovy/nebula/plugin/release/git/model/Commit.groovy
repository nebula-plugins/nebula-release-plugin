package nebula.plugin.release.git.model

import groovy.transform.Immutable

@Immutable
class Commit {
    String id
    /**
     * The abbreviated hash of the commit.
     */
    String abbreviatedId
}
