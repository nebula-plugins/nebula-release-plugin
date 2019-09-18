package nebula.plugin.release

import org.ajoberstar.grgit.Status

class ErrorMessageFormatter {
    static final String ROOT_CAUSE = 'Final and candidate builds require all changes to be committed into Git.'
    static final String NEW_LINE = sprintf("%n")

    String format(Status status) {
        if (status.isClean()) {
            return ""
        }
        StringBuilder sb = new StringBuilder(ROOT_CAUSE)
        if (status.staged.allChanges) {
            sb.append(header('staged changes'))
            sb.append(annotate(status.staged))
        }
        if (status.unstaged.allChanges) {
            sb.append(header('unstaged changes'))
            sb.append(annotate(status.unstaged))
        }
        if(status.conflicts) {
            sb.append(header('conflicts'))
            def conflicts = status.conflicts.collect { "  $it" }
            sb.append(conflicts.join(NEW_LINE))
        }
        return sb.toString()
    }

    private static String header(String name) {
        [NEW_LINE, "Found $name:", NEW_LINE].join('')
    }

    private static String annotate(Status.Changes changes) {
        def added = changes.added.collect { "  [+] $it" }
        def modified = changes.modified.collect { "  [M] $it"}
        def removed = changes.removed.collect { "  [-] $it"}
        return [added, modified, removed].flatten().join(NEW_LINE)
    }

}
