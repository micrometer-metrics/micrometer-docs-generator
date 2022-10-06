/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micrometer.docs.spans;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.docs.commons.templates.HandlebarsUtils;

public class SpansDocGenerator {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SpansDocGenerator.class);

    private final File projectRoot;

    private final Pattern inclusionPattern;

    private final File outputDir;

    public SpansDocGenerator(File projectRoot, Pattern inclusionPattern, File outputDir) {
        this.projectRoot = projectRoot;
        this.inclusionPattern = inclusionPattern;
        this.outputDir = outputDir;
    }

    public void generate() {
        Path path = this.projectRoot.toPath();
        logger.debug("Path is [" + this.projectRoot.getAbsolutePath() + "]. Inclusion pattern is [" + this.inclusionPattern + "]");
        Collection<SpanEntry> spanEntries = new TreeSet<>();
        FileVisitor<Path> fv = new SpanSearchingFileVisitor(this.inclusionPattern, spanEntries);
        try {
            Files.walkFileTree(path, fv);
            SpanEntry.assertThatProperlyPrefixed(spanEntries);

            printSpansAdoc(spanEntries);
        }
        catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void printSpansAdoc(Collection<SpanEntry> spanEntries) throws IOException {
        String location = "templates/spans.adoc.hbs";
        Handlebars handlebars = HandlebarsUtils.createHandlebars();
        Template template = handlebars.compile(location);

        Map<String, Object> map = new HashMap<>();
        map.put("entries", spanEntries);
        String result = template.apply(map);

        Path output = new File(this.outputDir, "_spans.adoc").toPath();
        Files.write(output, result.getBytes());
    }

}
