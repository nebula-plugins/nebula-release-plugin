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
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Plugin providing the base structure of gradle-git's flavor of release
 * behavior. The plugin can be applied using the {@code org.ajoberstar.release-base} id.
 *
 * <p>
 * The plugin adds the {@link ReleasePluginExtension} and a {@code release} task.
 * </p>
 *
 * @see nebula.plugin.release.git.opinion.Strategies
 * @see <a href="https://github.com/ajoberstar/gradle-git/wiki/org.ajoberstar.release-base">Wiki Doc</a>
 */
@CompileDynamic
class BaseReleasePlugin implements Plugin<Project> {

    private static final String PREPARE_TASK_NAME = 'prepare'
    private static final String RELEASE_TASK_NAME = 'release'

    void apply(Project project) {
        ReleasePluginExtension releasePluginExtension = project.extensions.create('release', ReleasePluginExtension, project)
        addPrepareTask(project, releasePluginExtension)
        addReleaseTask(project, releasePluginExtension)
    }

    private void addPrepareTask(Project project, ReleasePluginExtension extension) {
        def prepareTask = project.tasks.register(PREPARE_TASK_NAME, PrepareTask)
        prepareTask.configure {
            it.description = 'Verifies that the project could be released.'
            project.version.toString()
            if(!(project.version instanceof String) && project.version.inferredVersion) {
                it.projectVersion.set(project.version.inferredVersion)
            }
            it.remote.set(extension.remote)
            it.gitWriteCommandsUtil.set(extension.gitWriteCommands)
            it.gitCommandUtil.set(extension.gitReadCommands)
        }

        project.tasks.configureEach { task ->
            if (task.name != PREPARE_TASK_NAME) {
                task.shouldRunAfter PREPARE_TASK_NAME
            }
        }

    }


    private void addReleaseTask(Project project, ReleasePluginExtension extension) {
        def releaseTask = project.tasks.register(RELEASE_TASK_NAME, ReleaseTask)
        releaseTask.configure {
            it.description = 'Releases this project.'
            it.dependsOn project.tasks.named(PREPARE_TASK_NAME)
            project.version.toString()
            if(!(project.version instanceof String) && project.version.inferredVersion) {
                it.projectVersion.set(project.version.inferredVersion)
            }
            it.tagStrategy.set(extension.tagStrategy)
            it.remote.set(extension.remote)
            it.gitWriteCommandsUtil.set(extension.gitWriteCommands)
        }
    }
}
