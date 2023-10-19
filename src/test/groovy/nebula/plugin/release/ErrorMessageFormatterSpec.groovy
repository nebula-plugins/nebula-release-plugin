package nebula.plugin.release

import org.ajoberstar.grgit.Status
import spock.lang.Specification

class ErrorMessageFormatterSpec extends Specification {
    ErrorMessageFormatter formatter = new ErrorMessageFormatter()

    def 'should not have a message for clean status'() {
        given:
        def status = ""

        expect:
        formatter.format(status) == ""
    }

    def 'should list pending changes'() {
        given:
        def status = """
 M src/main/groovy/nebula/plugin/release/ErrorMessageFormatter.groovy
 M src/main/groovy/nebula/plugin/release/ReleasePlugin.groovy
AM src/main/groovy/nebula/plugin/release/git/GitProviders.groovy
 M src/test/groovy/nebula/plugin/release/ErrorMessageFormatterSpec.groovy
"""
        String expected = [
                ErrorMessageFormatter.ROOT_CAUSE,
                " M src/main/groovy/nebula/plugin/release/ErrorMessageFormatter.groovy\n" +
                        " M src/main/groovy/nebula/plugin/release/ReleasePlugin.groovy\n" +
                        "AM src/main/groovy/nebula/plugin/release/git/GitProviders.groovy\n" +
                        " M src/test/groovy/nebula/plugin/release/ErrorMessageFormatterSpec.groovy\n",
        ].join(sprintf(ErrorMessageFormatter.NEW_LINE))

        when:
        String message = formatter.format(status)

        then:
        message.stripIndent().replaceAll("\n",'') == expected.stripIndent().replaceAll("\n",'')
    }

}
