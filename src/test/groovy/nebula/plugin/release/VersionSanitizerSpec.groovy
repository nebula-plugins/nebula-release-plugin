package nebula.plugin.release

import spock.lang.Specification
import spock.lang.Unroll

class VersionSanitizerSpec extends Specification {

    @Unroll("sanitize #version results in #expected")
    def 'sanitize version'() {
        when:
        String sanitizedVersion = VersionSanitizerUtil.sanitize(version)

        then:
        sanitizedVersion == expected

        where:
        version                           | expected
        "0.0.1"                           | "0.0.1"
        "0.0.1-rc.1"                      | "0.0.1-rc.1"
        "0.0.1-alpha"                     | "0.0.1-alpha"
        "0.0.1-SNAPSHOT"                  | "0.0.1-SNAPSHOT"
        "0.0.1-snapshot-2019070708092902" | "0.0.1-snapshot-2019070708092902"
        "0.0.1-dev.1+4dcsd"               | "0.0.1-dev.1.4dcsd"
        "0.0.1-dev.1+branchName.4dcsd"    | "0.0.1-dev.1.branchName.4dcsd"
        "0.0.1-dev.1+my.feature.4dcsd"    | "0.0.1-dev.1.my.feature.4dcsd"
        "0.1.0-dev.0.uncommitted"         | "0.1.0-dev.0.uncommitted"
        "0.1.0-dev.0.uncommitted+4dcsd"   | "0.1.0-dev.0.uncommitted.4dcsd"
        "0.1.0-dev.0.uncommitted&4dcsd"   | "0.1.0-dev.0.uncommitted.4dcsd"
    }
}
