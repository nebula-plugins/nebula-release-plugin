/*
 * Copyright 2014-2023 Netflix, Inc.
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

plugins {
    id("com.netflix.nebula.plugin-plugin")
    `kotlin-dsl`
}

description = "Release opinions on top of gradle-git"

group = "com.netflix.nebula"

contacts {
    addPerson("nebula-plugins-oss@netflix.com") {
        moniker = "Nebula Plugins Maintainers"
        github = "nebula-plugins"
    }
}

tasks.named<GroovyCompile>("compileGroovy") {
    groovyOptions.configurationScript =
        project.layout.projectDirectory.file("src/groovyCompile/groovycConfig.groovy").asFile
}

dependencies {
    implementation("com.github.zafarkhaja:java-semver:0.9.0")
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.14.2"))

    compileOnly(platform("com.fasterxml.jackson:jackson-bom:2.11.0"))
    testImplementation("org.eclipse.jgit:org.eclipse.jgit:5.7.0.202003110725-r")
    testImplementation("org.ajoberstar.grgit:grgit-core:4.1.1") {
        exclude(group = "org.codehaus.groovy", module = "groovy")
    }
    testImplementation("org.spockframework:spock-junit4:2.4-groovy-4.0")
}

gradlePlugin {
    plugins {
        create("nebulaRelease") {
            id = "com.netflix.nebula.release"
            displayName = "Nebula Release plugin"
            description = project.description
            implementationClass = "nebula.plugin.release.ReleasePlugin"
            tags.addAll("nebula", "release", "versioning", "semver")
        }
        create("nebulaReleaseLegacy") {
            id = "nebula.release"
            displayName = "Nebula Release plugin"
            description = project.description
            implementationClass = "nebula.plugin.release.ReleasePlugin"
            tags.addAll("nebula", "release", "versioning", "semver")
        }
    }
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter()
            targets.all {
                testTask.configure {
                    maxParallelForks = 4
                }
            }
        }
    }
}

tasks.wrapper {
    distributionType = Wrapper.DistributionType.ALL
    gradleVersion = "9.5.0"
    distributionSha256Sum = "a3c4ba4aca8f0075688b9c5b18939fd28e8cb4357c227da5c1d9f38343791439"
}
