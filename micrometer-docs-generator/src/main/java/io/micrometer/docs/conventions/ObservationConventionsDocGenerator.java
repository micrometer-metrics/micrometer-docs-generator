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
package io.micrometer.docs.conventions;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
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
import io.micrometer.docs.commons.JavaSourceSearchHelper;
import io.micrometer.docs.commons.templates.HandlebarsUtils;
import io.micrometer.docs.conventions.ObservationConventionEntry.Type;

public class ObservationConventionsDocGenerator {

    private static final InternalLogger logger = InternalLoggerFactory
            .getInstance(ObservationConventionsDocGenerator.class);

    private final File projectRoot;

    private final Pattern inclusionPattern;

    private final String templateLocation;

    private final Path output;

    public ObservationConventionsDocGenerator(File projectRoot, Pattern inclusionPattern, String templateLocation,
            Path output) {
        this.projectRoot = projectRoot;
        this.inclusionPattern = inclusionPattern;
        this.templateLocation = templateLocation;
        this.output = output;
    }

    public void generate() {
        Path path = this.projectRoot.toPath();
        logger.debug("Path is [" + this.projectRoot.getAbsolutePath() + "]. Inclusion pattern is ["
                + this.inclusionPattern + "]");
        TreeSet<ObservationConventionEntry> observationConventionEntries = new TreeSet<>();
        JavaSourceSearchHelper searchHelper = JavaSourceSearchHelper.create(this.projectRoot.toPath(),
                this.inclusionPattern);
        FileVisitor<Path> fv = new ObservationConventionSearchingFileVisitor(this.inclusionPattern,
                observationConventionEntries, searchHelper);
        try {
            Files.walkFileTree(path, fv);
            printObservationConventionsAdoc(observationConventionEntries);
        }
        catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void printObservationConventionsAdoc(TreeSet<ObservationConventionEntry> entries) throws IOException {
        List<ObservationConventionEntry> globals = entries.stream().filter(e -> e.getType() == Type.GLOBAL)
                .collect(Collectors.toList());
        List<ObservationConventionEntry> locals = entries.stream().filter(e -> e.getType() == Type.LOCAL)
                .collect(Collectors.toList());

        Handlebars handlebars = HandlebarsUtils.createHandlebars();
        Template template = handlebars.compile(this.templateLocation);

        Map<String, Object> map = new HashMap<>();
        map.put("globals", globals);
        map.put("locals", locals);
        String result = template.apply(map);

        Files.write(this.output, result.getBytes());
    }

}
