/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micrometer.docs;

import java.io.File;
import java.util.regex.Pattern;

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.docs.conventions.ObservationConventionsDocGenerator;
import io.micrometer.docs.metrics.MetricsDocGenerator;
import io.micrometer.docs.spans.SpansDocGenerator;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Entry point for document generation.
 *
 * @author Tadaya Tsuyukubo
 */
@Command(mixinStandardHelpOptions = true, description = "Generate documentation from source files")
public class DocsGeneratorCommand implements Runnable {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DocsGeneratorCommand.class);

    @ArgGroup(exclusive = false)
    private final Options options = new Options();

    @Parameters(index = "0", description = "The project root directory.")
    private File projectRoot;

    @Parameters(index = "1", description = "The regex pattern for inclusion.")
    private Pattern inclusionPattern;

    @Parameters(index = "2", description = "The output directory.")
    private File outputDir;

    public static void main(String... args) {
        new CommandLine(new DocsGeneratorCommand()).execute(args);
        // Do not call System.exit here since exec-maven-plugin stops the maven run
    }

    @Override
    public void run() {
        this.inclusionPattern = Pattern.compile(this.inclusionPattern.pattern().replace("/", File.separator));
        logger.info("Project root: {}", this.projectRoot);
        logger.info("Inclusion pattern: {}", this.inclusionPattern);
        logger.info("Output root: {}", this.outputDir);

        this.options.setAllIfNoneSpecified();
        if (this.options.metrics) {
            generateMetricsDoc();
        }
        if (this.options.spans) {
            generateSpansDoc();
        }
        if (this.options.conventions) {
            generateConventionsDoc();
        }
    }

    void generateMetricsDoc() {
        new MetricsDocGenerator(this.projectRoot, this.inclusionPattern, this.outputDir).generate();
    }

    void generateSpansDoc() {
        new SpansDocGenerator(this.projectRoot, this.inclusionPattern, this.outputDir).generate();
    }

    void generateConventionsDoc() {
        new ObservationConventionsDocGenerator(this.projectRoot, this.inclusionPattern, this.outputDir).generate();
    }

    static class Options {
        @Option(names = "--metrics", description = "Generate metrics documentation")
        private boolean metrics;

        @Option(names = "--spans", description = "Generate spans documentation")
        private boolean spans;

        @Option(names = "--conventions", description = "Generate conventions documentation")
        private boolean conventions;

        void setAllIfNoneSpecified() {
            if (!this.metrics && !this.spans && !this.conventions) {
                this.metrics = true;
                this.spans = true;
                this.conventions = true;
            }
        }

    }
}
