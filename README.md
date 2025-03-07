# Micrometer Docs Generator

[![Build Status](https://circleci.com/gh/micrometer-metrics/micrometer-docs-generator.svg?style=shield)](https://circleci.com/gh/micrometer-metrics/micrometer-docs-generator)
[![Apache 2.0](https://img.shields.io/github/license/micrometer-metrics/micrometer-docs-generator.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/io.micrometer/micrometer-docs-generator.svg)](https://search.maven.org/artifact/io.micrometer/micrometer-docs-generator)
[![Javadocs](https://www.javadoc.io/badge/io.micrometer/micrometer-docs-generator.svg)](https://www.javadoc.io/doc/io.micrometer/micrometer-core)
[![Revved up by Develocity](https://img.shields.io/badge/Revved%20up%20by-Develocity-06A0CE?logo=Gradle&labelColor=02303A)](https://ge.micrometer.io/)

Generating adoc tables for metrics and spans.

IMPORTANT: This project is in an incubating stage.

## Join the discussion

Join the [Micrometer Slack](https://slack.micrometer.io) to share your questions, concerns, and feature requests.

## Snapshot builds

Snapshots are published to `repo.spring.io` for every successful build on the `main` branch and maintenance branches.

To use:

```groovy
repositories {
    maven { url 'https://repo.spring.io/snapshot' }
}

dependencies {
    implementation 'io.micrometer:micrometer-docs-generator:latest.integration'
}
```

## Milestone releases

Starting with the 1.1.0-M1 release, milestone releases and release candidates will be published to Maven Central. Note that milestone releases are for testing purposes and are not intended for production use. Earlier milestone releases were published to https://repo.spring.io/milestone.

## Documentation

Example for a Gradle setup that scans your sources from the root project and creates the metrics and spans output under the root project's `build` folder.

```groovy

repositories {
	maven { url 'https://repo.spring.io/snapshot' } // for snapshots
	mavenCentral() // for GA and milestones
}

ext {
	micrometerDocsVersion="1.0.0"
}

configurations {
	adoc
}

dependencies {
	adoc "io.micrometer:micrometer-docs-generator:$micrometerDocsVersion"
}

task generateObservabilityDocs() {
	mainClass = "io.micrometer.docs.DocsGeneratorCommand"
	classpath configurations.adoc
	// input folder, inclusion pattern, output folder
	args project.rootDir.getAbsolutePath(), ".*", project.rootProject.buildDir.getAbsolutePath()
}

```
Example for a Maven setup that scans your sources from the root project and creates the metrics and spans output under the root project's `target` folder.

```xml
	<properties>
		<micrometer-docs-generator.version>1.0.0</micrometer-docs-generator.version>
		<micrometer-docs-generator.inputPath>${maven.multiModuleProjectDirectory}/folder-with-sources-to-scan/</micrometer-docs-generator.inputPath>
		<micrometer-docs-generator.inclusionPattern>.*</micrometer-docs-generator.inclusionPattern>
		<micrometer-docs-generator.outputPath>${maven.multiModuleProjectDirectory}/target/output-folder-with-adocs/'</micrometer-docs-generator.outputPath>
	</properties>
	<build>
		<plugins>
			<plugin>
				<groupId>org.codehaus.mojo</groupId>
				<artifactId>exec-maven-plugin</artifactId>
				<executions>
					<execution>
						<id>generate-docs</id>
						<phase>prepare-package</phase>
						<goals>
							<goal>java</goal>
						</goals>
						<configuration>
							<mainClass>io.micrometer.docs.DocsGeneratorCommand</mainClass>
							<includePluginDependencies>true</includePluginDependencies>
							<arguments>
								<argument>${micrometer-docs-generator.inputPath}</argument>
								<argument>${micrometer-docs-generator.inclusionPattern}</argument>
								<argument>${micrometer-docs-generator.outputPath}</argument>
							</arguments>
						</configuration>
					</execution>
				</executions>
				<dependencies>
					<dependency>
						<groupId>io.micrometer</groupId>
						<artifactId>micrometer-docs-generator</artifactId>
						<version>${micrometer-docs-generator.version}</version>
						<type>jar</type>
					</dependency>
				</dependencies>
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
</repositories>
```

## Contributing

See our [Contributing Guide](CONTRIBUTING.md) for information about contributing to Micrometer Docs Generator.

## Code formatting

The [spring-javaformat plugin](https://github.com/spring-io/spring-javaformat) is configured to check and apply consistent formatting in the codebase through the build.
The `checkFormat` task checks the formatting as part of the `check` task.
Apply formatting with the `format` task.
You should rely on the formatting the `format` task applies instead of your IDE's configured formatting.

-------------------------------------
_Licensed under [Apache Software License 2.0](https://www.apache.org/licenses/LICENSE-2.0)_

_Sponsored by [VMware](https://tanzu.vmware.com)_
