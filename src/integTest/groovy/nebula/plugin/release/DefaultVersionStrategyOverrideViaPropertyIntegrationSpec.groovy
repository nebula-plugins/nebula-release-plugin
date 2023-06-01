/*
 * Copyright 2020 Netflix, Inc.
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

import org.gradle.api.plugins.JavaPlugin

class DefaultVersionStrategyOverrideViaPropertyIntegrationSpec extends GitVersioningIntegrationSpec {
    @Override
    def setupBuild() {
        fork = false
        buildFile << """
            ext.dryRun = true
            group = 'test'
            ${applyPlugin(ReleasePlugin)}
            ${applyPlugin(JavaPlugin)}
        """.stripIndent()

        git.add(patterns: ['build.gradle', '.gitignore'] as Set)
        git.tag.add(name: 'v0.0.1')
    }

    def 'should infer version using a custom strategy'() {
        buildFile << """
def printCustomVersion = tasks.register('printCustomVersion') {
    doLast {
        println "MY VERSION: \${project.version}"
    }
}

tasks.named('final').configure { 
   dependsOn(printCustomVersion)
}
"""

        def buildSrc = new File(projectDir, 'buildSrc')
        buildSrc.mkdirs()
        new File(buildSrc, 'build.gradle').text = """
        apply plugin: 'groovy'
        
        repositories {
            mavenCentral()
            maven {
              url "https://plugins.gradle.org/m2/"
            }
        }
        
        dependencies {
             implementation "com.netflix.nebula:nebula-release-plugin:latest.release"
             compileOnly localGroovy()
             compileOnly gradleApi()
             implementation ('org.ajoberstar.grgit:grgit-core:4.1.1') {
                    exclude group: 'org.codehaus.groovy', module: 'groovy'
             }
        }
"""

        writeGroovySourceFile(
        """
package com.netflix.nebula.custom.versioning

import nebula.plugin.release.*
import nebula.plugin.release.git.*
import nebula.plugin.release.git.base.*
import org.ajoberstar.grgit.*
import org.gradle.api.Project

class MyCustomVersioningStrategy implements DefaultVersionStrategy {
    @Override
    boolean defaultSelector(Project project, Grgit grgit) {
        return true
    }

    @Override
    String getName() {
        return "defaultStrategyTest"
    }

    @Override
    boolean selector(Project project, Grgit grgit) {
        return true
    }

    @Override
    ReleaseVersion infer(Project project, Grgit grgit) {
        return new ReleaseVersion("0.0.2", "0.0.1", true)
    }
}""", 'src/main/groovy', buildSrc)

        settingsFile.text = """

plugins {
    id "com.gradle.enterprise" version "3.13.3"
}

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}
"""
        git.add(patterns: ['.'] as Set)
        git.commit(message: 'More Setup')

        when:
        def resultBuild = runTasksSuccessfully('final',
                '-Prelease.defaultVersioningStrategy=com.netflix.nebula.custom.versioning.MyCustomVersioningStrategy', '--scan', '--refresh-dependencies')

        then:
        resultBuild.standardOutput.contains('MY VERSION: 0.0.2')
        git.tag.list().size() == 2
        git.tag.list().find { it.name == 'v0.0.2'}
    }

    void writeGroovySourceFile(String source, String sourceFolderPath, File projectDir = getProjectDir()) {
        File javaFile = createFile(sourceFolderPath + '/' + fullyQualifiedName(source).replaceAll(/\./, '/') + '.groovy', projectDir)
        javaFile.text = source
    }

    private String fullyQualifiedName(String sourceStr) {
        def pkgMatcher = sourceStr =~ /\s*package\s+([\w\.]+)/
        def pkg = pkgMatcher.find() ? (pkgMatcher[0] as List<String>)[1] + '.' : ''

        def classMatcher = sourceStr =~ /\s*(class|interface)\s+(\w+)/
        return classMatcher.find() ? pkg + (classMatcher[0] as List<String>)[2] : null
    }
}
