# Micrometer Application Metrics

[![Build Status](https://circleci.com/gh/micrometer-metrics/micrometer-docs-generator.svg?style=shield)](https://circleci.com/gh/micrometer-metrics/micrometer-docs-generator)
[![Apache 2.0](https://img.shields.io/github/license/micrometer-metrics/micrometer-docs-generator.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/io.micrometer/micrometer-docs-generator.svg)](https://search.maven.org/artifact/io.micrometer/micrometer-docs-generator)
[![Javadocs](https://www.javadoc.io/badge/io.micrometer/micrometer-docs-generator.svg)](https://www.javadoc.io/doc/io.micrometer/micrometer-core)
[![Revved up by Gradle Enterprise](https://img.shields.io/badge/Revved%20up%20by-Gradle%20Enterprise-06A0CE?logo=Gradle&labelColor=02303A)](https://ge.micrometer.io/)

Generating adoc tables for metrics and spans.

## Join the discussion

Join the [Micrometer Slack](https://slack.micrometer.io) to share your questions, concerns, and feature requests.

## Snapshot builds

Snapshots are published to `repo.spring.io` for every successful build on the `main` branch and maintenance branches.

To use:

```groovy
repositories {
    maven { url 'https://repo.spring.io/libs-snapshot' }
}

dependencies {
    implementation 'io.micrometer:micrometer-docs-generator:latest.integration'
}
```

## Milestone releases

Milestone releases are published to https://repo.spring.io/milestone. Include that as a maven repository in your build
configuration to use milestone releases. Note that milestone releases are for testing purposes and are not intended for
production use.

## Documentation

Example for a Gradle setup that scans your sources from the root project and creates the metrics and spans output under the root project's `build` folder.

```groovy

repositories {
	maven { url 'https://repo.spring.io/snapshot' } // for snapshots
	maven { url 'https://repo.spring.io/milestone' } // for milestones
	mavenCentral() // for GA
}

ext {
	micrometerDocsVersion="1.0.0-SNAPSHOT"
}

configurations {
	adoc
}

dependencies {
	adoc "io.micrometer:micrometer-docs-generator-spans:$micrometerDocsVersion"
	adoc "io.micrometer:micrometer-docs-generator-metrics:$micrometerDocsVersion"
}

task generateObservabilityDocs(dependsOn: ["generateObservabilityMetricsDocs", "generateObservabilitySpansDocs"]) {
}

task generateObservabilityMetricsDocs(type: JavaExec) {
	mainClass = "io.micrometer.docs.metrics.DocsFromSources"
	classpath configurations.adoc
	args project.rootDir.getAbsolutePath(), ".*", project.rootProject.buildDir.getAbsolutePath()
}

task generateObservabilitySpansDocs(type: JavaExec) {
	mainClass = "io.micrometer.docs.spans.DocsFromSources"
	classpath configurations.adoc
	args project.rootDir.getAbsolutePath(), ".*", project.rootProject.buildDir.getAbsolutePath()
}
```
Example for a Maven setup that scans your sources from the root project and creates the metrics and spans output under the root project's `target` folder.

```xml

<properties>
	<!-- Observability -->
	<micrometer-docs-generator.version>1.0.0-SNAPSHOT</micrometer-docs-generator.version>
	<micrometer-docs-generator.inputPath>${maven.multiModuleProjectDirectory}/</micrometer-docs-generator.inputPath>
	<micrometer-docs-generator.inclusionPattern>.*</micrometer-docs-generator.inclusionPattern>
	<micrometer-docs-generator.outputPath>${maven.multiModuleProjectDirectory}/target/</micrometer-docs-generator.outputPath>
</properties>

<build>
    <plugins>
        <plugin>
            <groupId>org.codehaus.mojo</groupId>
            <artifactId>exec-maven-plugin</artifactId>
            <executions>
                <execution>
                    <id>generate-metrics-metadata</id>
                    <phase>pre-site</phase>
                    <goals>
                        <goal>java</goal>
                    </goals>
                    <configuration>
                        <mainClass>io.micrometer.docs.metrics.DocsFromSources</mainClass>
                    </configuration>
                </execution>
                <execution>
                    <id>generate-tracing-metadata</id>
                    <phase>pre-site</phase>
                    <goals>
                        <goal>java</goal>
                    </goals>
                    <configuration>
                        <mainClass>io.micrometer.docs.spans.DocsFromSources</mainClass>
                    </configuration>
                </execution>
            </executions>
            <dependencies>
                <dependency>
                    <groupId>io.micrometer
                    </groupId>
                    <artifactId>micrometer-docs-generator-spans</artifactId>
                    <version>${micrometer-docs-generator.version}
                    </version>
                    <type>jar</type>
                </dependency>
                <dependency>
                    <groupId>io.micrometer
                    </groupId>
                    <artifactId>micrometer-docs-generator-metrics</artifactId>
                    <version>${micrometer-docs-generator.version}
                    </version>
                    <type>jar</type>
                </dependency>
            </dependencies>
            <configuration>
                <includePluginDependencies>true</includePluginDependencies>
                <arguments>
                    <argument>${micrometer-docs-generator.inputPath}</argument>
                    <argument>${micrometer-docs-generator.inclusionPattern}</argument>
                    <argument>${micrometer-docs-generator.outputPath}</argument>
                </arguments>
            </configuration>
        </plugin>
    </plugins>
</build>

<repositories>
    <repository>
        <id>spring-snapshots</id>
        <name>Spring Snapshots</name>
        <url>https://repo.spring.io/snapshot</url> <!-- For Snapshots -->
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
        <releases>
            <enabled>false</enabled>
        </releases>
    </repository>
    <repository>
        <id>spring-milestones</id>
        <name>Spring Milestones</name>
        <url>https://repo.spring.io/milestone</url>  <!-- For Milestones -->
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
    </repository>
</repositories>
```

## Contributing

See our [Contributing Guide](CONTRIBUTING.md) for information about contributing to Micrometer Docs Generator.

-------------------------------------
_Licensed under [Apache Software License 2.0](https://www.apache.org/licenses/LICENSE-2.0)_

_Sponsored by [VMware](https://tanzu.vmware.com)_
