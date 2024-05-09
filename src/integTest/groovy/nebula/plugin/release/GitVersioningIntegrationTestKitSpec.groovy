/*
 * Copyright 2014-2015 Netflix, Inc.
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

import com.github.zafarkhaja.semver.Version
import nebula.test.IntegrationSpec
import nebula.test.IntegrationTestKitSpec
import org.ajoberstar.grgit.Grgit

import java.nio.file.Files

abstract class GitVersioningIntegrationTestKitSpec extends IntegrationTestKitSpec {
    protected Grgit git
    protected Grgit originGit

    def setup() {
        def origin = new File(projectDir.parent, "${projectDir.name}.git")
        if (origin.exists()) {
            origin.deleteDir()
        }
        origin.mkdirs()

        ['build.gradle', 'settings.gradle'].each {
            def file = new File(projectDir, it)
            if(!file.exists()) {
                file.createNewFile()
            }
            Files.move(file.toPath(), new File(origin, it).toPath())
        }

        originGit = Grgit.init(dir: origin)
        originGit.add(patterns: ['build.gradle', 'settings.gradle', '.gitignore'] as Set)
        originGit.commit(message: 'Initial checkout')

        if (originGit.branch.current().name == 'main') {
            originGit.checkout(branch: 'master', createBranch: true)
            originGit.branch.remove(names: ['main'])
        }

        git = Grgit.clone(dir: projectDir, uri: origin.absolutePath) as Grgit

        new File(projectDir, '.gitignore') << '''.gradle-test-kit
.gradle
build/
gradle.properties'''.stripIndent()

        // Enable configuration cache :)
        new File(projectDir, 'gradle.properties') << '''org.gradle.configuration-cache=true'''.stripIndent()

        setupBuild()


        git.add(patterns: ['build.gradle', 'settings.gradle', '.gitignore'])
        git.commit(message: 'Setup')
        git.push()
    }

    abstract def setupBuild()

    def cleanup() {
        if (git) git.close()
        if (originGit) originGit.close()
    }

    def Version normal(String version) {
        Version.valueOf(version)
    }

    def Version dev(String version) {
        normal("${version}${git.head().abbreviatedId}")
    }

    def Version inferredVersionForTask(String... args) {
        def result = runTasks(args)
        inferredVersion(result.output)
    }

    def Version inferredVersion(String standardOutput) {
        inferredVersion(standardOutput, moduleName)
    }

    def Version inferredVersion(String standardOutput, String projectName) {
        def matcher = standardOutput =~ /Inferred project: (.*), version: (.*)/
        if (matcher.size() > 0) {
            def project = matcher[0][1] as String
            def version = matcher[0][2] as String
            assert project == projectName
            normal(version)
        } else {
            throw new IllegalArgumentException("Could not find inferred version using $matcher")
        }
    }
}
