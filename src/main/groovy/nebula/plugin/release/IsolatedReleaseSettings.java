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
package nebula.plugin.release;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;

/**
 * Experimental common inputs for isolated release workflows.
 * Configure this extension in settings, before project configuration starts.
 */
public abstract class IsolatedReleaseSettings extends ReleaseExtension {
    @Inject
    public IsolatedReleaseSettings(ObjectFactory objects) {
        super(objects);
    }

    public abstract DirectoryProperty getGitRoot();

    public abstract Property<String> getScope();

    public abstract Property<Boolean> getPrefixTagsWithV();
}
