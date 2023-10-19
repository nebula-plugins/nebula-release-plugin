package nebula.plugin.release.git.model

import com.github.zafarkhaja.semver.Version
import groovy.transform.Sortable
import nebula.plugin.release.git.base.ShortenRefUtil

@Sortable(includes = ['version'])
class TagRef  {

    String name
    Version version
    String commit

    TagRef(String name, String commit) {
        this.name = name
        this.commit = commit
        this.version = parseTag(name)
    }

    TagRef(String name) {
        String tag = ShortenRefUtil.shortenRefName(name)
        this.name = tag
        this.version = parseTag(tag)
    }

    static fromRef(String ref) {
        List<String> parts = ref.split(' ').toList()
        String tag = ShortenRefUtil.shortenRefName(parts[1])
        return new TagRef(tag, parts[0])
    }


    private static Version parseTag(String name) {
        try {
            Version.valueOf(name[0] == 'v' ? name[1..-1] : name)
        } catch (Exception e) {
            null
        }
    }
}
