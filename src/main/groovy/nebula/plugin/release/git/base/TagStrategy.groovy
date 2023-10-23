/*
 * Copyright 2012-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nebula.plugin.release.git.base

import groovy.transform.CompileDynamic
import nebula.plugin.release.git.command.GitWriteCommandsUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Strategy for creating a Git tag associated with a release.
 */
@CompileDynamic
class TagStrategy implements Serializable {

    /**
     * Closure taking a version String as an argument and returning a string to be used as a tag name.
     */
    Closure<String> toTagString


    TagStrategy() {
        setPrefixNameWithV(true)
    }

    private static final Logger logger = LoggerFactory.getLogger(TagStrategy)

    /**
     * Added for backwards compatibility.
     * @param prefix whether or not to prefix the tag with a 'v'
     */
    void setPrefixNameWithV(boolean prefix) {
        toTagString = { versionString -> prefix ? "v${versionString}" : versionString }
    }

    /**
     * Closure taking a {@link ReleaseVersion} as an argument and returning
     * a string to be used as the tag's message.
     */
    Closure generateMessage = { ReleaseVersion version -> "Release of ${version.version}" }

   /**
     * If the release version specifies a tag should be created, create a tag
     * using the provided {@code Grgit} instance and this instance's state to
     * determine the tag name and message.
     * @param gitOps the git operations to use
     * @param version the version to create the tag for
     * @return the name of the tag created, or {@code null} if it wasn't
     */
    String maybeCreateTag(GitWriteCommandsUtil gitWriteCommandsUtil, ReleaseVersion version) {
        if (version.createTag) {
            String name = toTagString(version.version)
            String message = generateMessage(version)

            logger.warn('Tagging repository as {}', name)
            gitWriteCommandsUtil.createTag(name, message)
            return name
        } else {
            return null
        }
    }
}
