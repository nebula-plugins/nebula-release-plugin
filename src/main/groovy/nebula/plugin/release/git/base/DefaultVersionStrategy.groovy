/*
 * Copyright 2012-2023 the original author or authors.
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

import nebula.plugin.release.git.GitOps

import org.gradle.api.Project

/**
 * Strategy to infer a version from the project's and Git repository's state. This
 * also supports being selected as a default strategy. This is a temporary interface
 * and should be replaced in some other way in gradle-git 2.0.0.
 * @see nebula.plugin.release.git.semver.SemVerStrategy
 * @see nebula.plugin.release.git.opinion.Strategies
 */
interface DefaultVersionStrategy extends VersionStrategy {

    /**
     * Determines if the strategy can be used as a default strategy for inferring
     * the project's version. A return of {@code false} does not mean that the
     * strategy cannot be used as the default.
     * @param project the project the version should be inferred for
     * @param gitOps the class to talk to git using native git calls
     * @return {@code true} if the strategy can be used to infer the version
     */
    boolean defaultSelector(Project project, GitOps gitOps)
}
