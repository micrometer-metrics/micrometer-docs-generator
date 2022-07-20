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

package io.micrometer.docs.commons;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.docs.commons.utils.StringUtils;

public class ObservationConventionEntry implements Comparable<ObservationConventionEntry> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ObservationConventionEntry.class);

    private final String className;

    private final Type type;

    private final String contextClassName;

    public ObservationConventionEntry(String className, Type type, String contextClassName) {
        this.className = className;
        this.type = type;
        this.contextClassName = StringUtils.hasText(contextClassName) ? contextClassName : "Unable to resolve";
    }

    public String getClassName() {
        return className;
    }

    public String getContextClassName() {
        return contextClassName;
    }

    @Override
    public int compareTo(ObservationConventionEntry o) {
        int compare = this.contextClassName.compareTo(o.contextClassName);
        if (compare != 0) {
            return compare;
        }
        compare = this.type.compareTo(o.type);
        if (compare != 0) {
            return compare;
        }
        if (this.className != null) {
            return this.className.compareTo(o.className);
        }
        return compare;
    }

    public enum Type {
        GLOBAL, LOCAL
    }

    public static void saveEntriesAsAdocTableInAFile(Collection<ObservationConventionEntry> entries, File outputFile) throws IOException {
        if (entries.isEmpty()) {
            logger.debug("No ObservationConventions found - will not output anything");
            return;
        }

        StringBuilder builder = new StringBuilder();
        List<ObservationConventionEntry> global = entries.stream().filter(e -> e.type == Type.GLOBAL).collect(Collectors.toList());
        List<ObservationConventionEntry> local = entries.stream().filter(e -> e.type == Type.LOCAL).collect(Collectors.toList());

        Path output = outputFile.toPath();
        logger.debug("======================================");
        logger.debug("Summary of sources analysis for conventions");
        logger.debug("Found [" + entries.size() + "] conventions");
        logger.debug(
                "Found [" + global.size() + "] GlobalObservationConvention implementations");
        logger.debug(
                "Found [" + local.size() + "] ObservationConvention implementations");

        builder.append("[[observability-conventions]]\n")
                .append("=== Observability - Conventions\n\n")
                .append("Below you can find a list of all `GlobalObservabilityConventions` and `ObservabilityConventions` declared by this project.")
                .append("\n\n");

        if (!global.isEmpty()) {
            builder.append(".GlobalObservationConvention implementations\n")
                    .append("|===\n")
                    .append("|GlobalObservationConvention Class Name | Applicable ObservationContext Class Name\n")
                    .append(global.stream().map(e -> "|`" + e.getClassName() + "`|`" + e.contextClassName + "`").collect(Collectors.joining("\n")))
                    .append("\n|===");

            builder.append("\n\n");
        }

        // TODO: Duplication
        if (!local.isEmpty()) {

            builder.append(".ObservationConvention implementations\n")
                    .append("|===\n")
                    .append("|ObservationConvention Class Name | Applicable ObservationContext Class Name\n")
                    .append(local.stream().map(e -> "|`" + e.getClassName() + "`|`" + e.contextClassName + "`").collect(Collectors.joining("\n")))
                    .append("\n|===");
        }

        Files.write(output, builder.toString().getBytes());
    }
}
