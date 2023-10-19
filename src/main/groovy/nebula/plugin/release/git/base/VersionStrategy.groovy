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

import nebula.plugin.release.git.GitOps

import org.gradle.api.Project

/**
 * Strategy to infer a version from the project's and Git repository's state.
 * @see nebula.plugin.release.git.semver.SemVerStrategy
 * @see nebula.plugin.release.git.opinion.Strategies
 */
interface VersionStrategy {
    /**
     * The name of the strategy.
     * @return the name of the strategy
     */
    String getName()


    /**
     * Determines if the strategy should be used to infer the project's version.
     * A return of {@code false} does not mean that the strategy cannot be used
     * as the default.
     * @param project the project the version should be inferred for
     * @param gitOps the Git operations to use
     * @return {@code true} if the strategy should be used to infer the version
     */
    boolean selector(Project project, GitOps gitOps)


    /**
     * Infers the project version from the repository.
     * @param project the project the version should be inferred for
     * @param gitOps the Git operations to use
     * @return the inferred version
     */
    ReleaseVersion infer(Project project, GitOps gitOps)
}
