package nebula.plugin.release

import org.ajoberstar.grgit.Status
import spock.lang.Specification

class ErrorMessageFormatterSpec extends Specification {
    ErrorMessageFormatter formatter = new ErrorMessageFormatter()

    def 'should not have a message for clean status'() {
        given:
        def status = new Status()

        expect:
        status.isClean()
        formatter.format(status) == ""
    }

    def 'should list staged files'() {
        given:
        def status = new Status(['staged':['added': ['add.txt'], 'modified': ['path/to/mod.txt', 'mod.b.txt'], 'removed': ['folder/rm.txt']]])
        String expected = [
                ErrorMessageFormatter.ROOT_CAUSE,
                "Found staged changes:",
                "  [+] add.txt",
                "  [M] path/to/mod.txt",
                "  [M] mod.b.txt",
                "  [-] folder/rm.txt",
        ].join(sprintf(ErrorMessageFormatter.NEW_LINE))

        when:
        String message = formatter.format(status)

        then:
        !status.isClean()
        message == expected
    }

    def 'should list unstaged files'() {
        given:
        def status = new Status(['unstaged':['added': ['z.txt'], 'modified': ['a/b/c.txt'], 'removed': ['sub/folder/file.txt', 'version.txt']]])
        String expected = [
                ErrorMessageFormatter.ROOT_CAUSE,
                "Found unstaged changes:",
                "  [+] z.txt",
                "  [M] a/b/c.txt",
                "  [-] sub/folder/file.txt",
                "  [-] version.txt",
        ].join(sprintf(ErrorMessageFormatter.NEW_LINE))

        when:
        String message = formatter.format(status)

        then:
        !status.isClean()
        message == expected
    }

    def 'should list conflict files'() {
        given:
        def status = new Status(['conflicts':['a.txt', 'b.java']])
        String expected = [
                ErrorMessageFormatter.ROOT_CAUSE,
                "Found conflicts:",
                "  a.txt",
                "  b.java",
        ].join(sprintf(ErrorMessageFormatter.NEW_LINE))

        when:
        String message = formatter.format(status)

        then:
        !status.isClean()
        message == expected
    }
}
