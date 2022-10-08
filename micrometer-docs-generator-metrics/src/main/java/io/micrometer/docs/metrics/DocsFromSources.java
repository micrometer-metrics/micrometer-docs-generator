/**
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
package io.micrometer.docs.metrics;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.docs.commons.ObservationConventionEntry;
import io.micrometer.docs.commons.ObservationConventionEntry.Type;
import io.micrometer.docs.commons.templates.HandlebarsUtils;

// TODO: Assert on prefixes
public class DocsFromSources {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DocsFromSources.class);

    private final File projectRoot;

    private final Pattern inclusionPattern;

    private final File outputDir;

    public DocsFromSources(File projectRoot, Pattern inclusionPattern, File outputDir) {
        this.projectRoot = projectRoot;
        this.inclusionPattern = inclusionPattern;
        this.outputDir = outputDir;
    }

    public static void main(String... args) {
        String projectRoot = args[0];
        String inclusionPattern = args[1];
        inclusionPattern = inclusionPattern.replace("/", File.separator);
        String output = args[2];
        new DocsFromSources(new File(projectRoot), Pattern.compile(inclusionPattern), new File(output)).generate();
    }

    public void generate() {
        Path path = this.projectRoot.toPath();
        logger.debug("Path is [" + this.projectRoot.getAbsolutePath() + "]. Inclusion pattern is [" + this.inclusionPattern + "]");
        Collection<MetricEntry> entries = new TreeSet<>();
        TreeSet<ObservationConventionEntry> observationConventionEntries = new TreeSet<>();
        FileVisitor<Path> fv = new MetricSearchingFileVisitor(this.inclusionPattern, entries, observationConventionEntries);
        try {
            Files.walkFileTree(path, fv);
            MetricEntry.assertThatProperlyPrefixed(entries);

            this.outputDir.mkdirs();
            printMetricsAdoc(entries);
            printObservationConventionsAdoc(observationConventionEntries);
        }
        catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void printMetricsAdoc(Collection<MetricEntry> entries) throws IOException {
        String location = "templates/metrics.adoc.hbs";
        Handlebars handlebars = HandlebarsUtils.createHandlebars();
        Template template = handlebars.compile(location);

        Map<String, Object> map = new HashMap<>();
        map.put("entries", entries);
        String result = template.apply(map);

        Path output = new File(this.outputDir, "_metrics.adoc").toPath();
        Files.write(output, result.getBytes());
    }
    private void printObservationConventionsAdoc(TreeSet<ObservationConventionEntry> entries) throws IOException {
        List<ObservationConventionEntry> globals = entries.stream().filter(e -> e.getType() == Type.GLOBAL).collect(Collectors.toList());
        List<ObservationConventionEntry> locals = entries.stream().filter(e -> e.getType() == Type.LOCAL).collect(Collectors.toList());

        String location = "templates/conventions.adoc.hbs";
        Handlebars handlebars = HandlebarsUtils.createHandlebars();
        Template template = handlebars.compile(location);

        Map<String, Object> map = new HashMap<>();
        map.put("globals", globals);
        map.put("locals", locals);
        String result = template.apply(map);

        Path output = new File(this.outputDir, "_conventions.adoc").toPath();
        Files.write(output, result.getBytes());
    }

}
