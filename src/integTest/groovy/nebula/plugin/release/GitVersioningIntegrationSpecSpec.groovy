package nebula.plugin.release

/**
 * Tests for {@link GitVersioningIntegrationSpec}.
 */
class GitVersioningIntegrationSpecSpec /* tee-hee */ extends GitVersioningIntegrationSpec {
    @Override def setupBuild() {}

    def 'inferred version is parsed'() {
        when:
        def version = inferredVersion('Inferred project: inferred-version-is-parsed, version: 1.0.0')

        then:
        version == normal('1.0.0')
    }

    def 'inferred version is parsed from multiline input'() {
        when:
        def version = inferredVersion('Some\nDummy\nText\nInferred project: inferred-version-is-parsed-from-multiline-input, version: 1.0.0\nAfter\nText')

        then:
        version == normal('1.0.0')
    }

    def 'missing inferred version throws IAE'() {
        when:
        inferredVersion('')

        then:
        thrown(IllegalArgumentException)
    }
}