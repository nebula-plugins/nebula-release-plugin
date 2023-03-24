package nebula.plugin.release

class ErrorMessageFormatter {
    static final String ROOT_CAUSE = 'Final and candidate builds require all changes to be committed into Git.'
    static final String NEW_LINE = sprintf("%n")

    static String format(String status) {
        if(!status) {
            return ""
        }

        StringBuilder sb = new StringBuilder(ROOT_CAUSE)
        sb.append(NEW_LINE)
        sb.append(status)
        sb.append(NEW_LINE)
        return sb.toString()
    }

}
