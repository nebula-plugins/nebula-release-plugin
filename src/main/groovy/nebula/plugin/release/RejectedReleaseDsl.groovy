/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nebula.plugin.release

import groovy.transform.PackageScope

/** Diagnoses legacy extension customization instead of silently ignoring it. */
@PackageScope
final class RejectedReleaseDsl {
    private final String extensionName

    RejectedReleaseDsl(String extensionName) {
        this.extensionName = extensionName
    }

    Object methodMissing(String name, Object arguments) {
        throw unsupported(name)
    }

    Object propertyMissing(String name) {
        throw unsupported(name)
    }

    void propertyMissing(String name, Object value) {
        throw unsupported(name)
    }

    private RuntimeException unsupported(String name) {
        IsolatedReleaseSupport.unsupported("legacy ${extensionName}.${name} customization is not implemented; " +
                'configure supported common inputs through isolatedRelease in settings.gradle')
    }
}
