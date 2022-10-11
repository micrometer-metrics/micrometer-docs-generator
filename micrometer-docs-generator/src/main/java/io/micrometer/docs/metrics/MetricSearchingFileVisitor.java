/**
 * Copyright 2022 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.docs.metrics;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.docs.MeterDocumentation;
import io.micrometer.docs.commons.EventEntry;
import io.micrometer.docs.commons.EventEntryForMetricEnumConstantReader;
import io.micrometer.docs.commons.KeyNameEntry;
import io.micrometer.docs.commons.KeyNameEnumConstantReader;
import io.micrometer.docs.commons.ParsingUtils;
import io.micrometer.docs.commons.utils.AsciidocUtils;
import io.micrometer.observation.docs.ObservationDocumentation;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MethodDeclaration;
import org.jboss.forge.roaster.model.source.EnumConstantSource;
import org.jboss.forge.roaster.model.source.JavaEnumSource;
import org.jboss.forge.roaster.model.source.JavaSource;
import org.jboss.forge.roaster.model.source.MemberSource;

class MetricSearchingFileVisitor extends SimpleFileVisitor<Path> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(MetricSearchingFileVisitor.class);

    private final Pattern pattern;

    private final Collection<MetricEntry> entries;

    MetricSearchingFileVisitor(Pattern pattern, Collection<MetricEntry> entries) {
        this.pattern = pattern;
        this.entries = entries;
    }

    @Override
    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
        if (!pattern.matcher(path.toString()).matches()) {
            return FileVisitResult.CONTINUE;
        }
        else if (!path.toString().endsWith(".java")) {
            return FileVisitResult.CONTINUE;
        }

        logger.debug("Parsing [" + path + "]");
        JavaSource<?> javaSource = Roaster.parse(JavaSource.class, path.toFile());
        if (!javaSource.isEnum()) {
            return FileVisitResult.CONTINUE;
        }
        JavaEnumSource enumSource = (JavaEnumSource) javaSource;
        if (!enumSource.hasInterface(MeterDocumentation.class) && !enumSource.hasInterface(ObservationDocumentation.class)) {
            return FileVisitResult.CONTINUE;
        }
        logger.debug("Checking [" + javaSource.getName() + "]");
        if (enumSource.getEnumConstants().size() == 0) {
            return FileVisitResult.CONTINUE;
        }
        for (EnumConstantSource enumConstant : enumSource.getEnumConstants()) {
            MetricEntry entry = parseMetric(path, enumConstant, enumSource);
            if (entry != null) {
                logger.debug(
                        "Found [" + entry.lowCardinalityKeyNames.size() + "] low cardinality tags and [" + entry.highCardinalityKeyNames.size() + "] high cardinality tags");
                if (entry.overridesDefaultMetricFrom != null && entry.lowCardinalityKeyNames.isEmpty()) {
                    addTagsFromOverride(path, entry);
                }
                entries.add(entry);
                logger.debug("Found [" + entry.lowCardinalityKeyNames.size() + "]");
            }
        }
        return FileVisitResult.CONTINUE;
    }

    // if entry has overridesDefaultSpanFrom - read tags from that thing
    // if entry has overridesDefaultSpanFrom AND getKeyNames() - we pick only the latter
    // if entry has overridesDefaultSpanFrom AND getAdditionalKeyNames() - we pick both
    private void addTagsFromOverride(Path file, MetricEntry entry) throws IOException {
        Map.Entry<String, String> overrideDefaults = entry.overridesDefaultMetricFrom;
        logger.debug("Reading additional meta data from [" + overrideDefaults + "]");
        String className = overrideDefaults.getKey();
        File parent = file.getParent().toFile();
        while (!parent.getAbsolutePath().endsWith(File.separator + "java")) {
            parent = parent.getParentFile();
        }
        Path filePath = parent.toPath().resolve(className.replace(".", File.separator) + ".java");
        JavaSource<?> javaSource = Roaster.parse(JavaSource.class, filePath.toFile());
        if (!javaSource.isEnum()) {
            return;
        }
        JavaEnumSource enumSource = (JavaEnumSource) javaSource;
        if (!enumSource.hasInterface(ObservationDocumentation.class)) {
            return;
        }
        logger.debug("Checking [" + enumSource.getName() + "]");
        if (enumSource.getEnumConstants().size() == 0) {
            return;
        }
        for (EnumConstantSource enumConstant : enumSource.getEnumConstants()) {
            if (!enumConstant.getName().equals(overrideDefaults.getValue())) {
                continue;
            }
            List<KeyNameEntry> lows = ParsingUtils.getTags(enumConstant, enumSource, "getLowCardinalityKeyNames");
            entry.lowCardinalityKeyNames.addAll(lows);
        }
    }

    @Nullable
    private MetricEntry parseMetric(Path file, EnumConstantSource enumConstant, JavaEnumSource myEnum) {
        List<MemberSource<EnumConstantSource.Body, ?>> members = enumConstant.getBody().getMembers();
        if (members.isEmpty()) {
            return null;
        }
        String name = "";
        String description = AsciidocUtils.javadocToAsciidoc(enumConstant.getJavaDoc());
        String prefix = "";
        String baseUnit = "";
        Meter.Type type = Meter.Type.TIMER;
        List<KeyNameEntry> lowCardinalityTags = new ArrayList<>();
        List<KeyNameEntry> highCardinalityTags = new ArrayList<>();
        Map.Entry<String, String> overridesDefaultMetricFrom = null;
        String conventionClass = null;
        String nameFromConventionClass = null;
        List<EventEntry> events = new ArrayList<>();
        for (MemberSource<EnumConstantSource.Body, ?> member : members) {
            Object internal = member.getInternal();
            if (!(internal instanceof MethodDeclaration)) {
                continue;
            }
            MethodDeclaration methodDeclaration = (MethodDeclaration) internal;
            String methodName = methodDeclaration.getName().getIdentifier();
            // MeterDocumentation, ObservationDocumentation
            if ("getName".equals(methodName)) {
                name = ParsingUtils.readStringReturnValue(methodDeclaration);
            }
            // MeterDocumentation
            else if ("getKeyNames".equals(methodName)) {
                lowCardinalityTags.addAll(ParsingUtils.retrieveModels(myEnum, methodDeclaration, KeyNameEnumConstantReader.INSTANCE));
            }
            // ObservationDocumentation(@Nullable)
            else if ("getDefaultConvention".equals(methodName)) {
                conventionClass = ParsingUtils.readClass(methodDeclaration);
                nameFromConventionClass = ParsingUtils.tryToReadStringReturnValue(file, conventionClass);
            }
            // ObservationDocumentation
            else if ("getLowCardinalityKeyNames".equals(methodName) || "asString".equals(methodName)) {
                lowCardinalityTags.addAll(ParsingUtils.retrieveModels(myEnum, methodDeclaration, KeyNameEnumConstantReader.INSTANCE));
            }
            // ObservationDocumentation
            else if ("getHighCardinalityKeyNames".equals(methodName)) {
                highCardinalityTags.addAll(ParsingUtils.retrieveModels(myEnum, methodDeclaration, KeyNameEnumConstantReader.INSTANCE));
            }
            // MeterDocumentation, ObservationDocumentation
            else if ("getPrefix".equals(methodName)) {
                prefix = ParsingUtils.readStringReturnValue(methodDeclaration);
            }
            // MeterDocumentation
            else if ("getBaseUnit".equals(methodName)) {
                baseUnit = ParsingUtils.readStringReturnValue(methodDeclaration);
            }
            // MeterDocumentation
            else if ("getType".equals(methodName)) {
                type = ParsingUtils.enumFromReturnMethodDeclaration(methodDeclaration, Meter.Type.class);
            }
            // MeterDocumentation
            else if ("overridesDefaultMetricFrom".equals(methodName)) {
                overridesDefaultMetricFrom = ParsingUtils.readClassToEnum(methodDeclaration);
            }
            // ObservationDocumentation
            else if ("getEvents".equals(methodName)) {
                Collection<EventEntry> entries = ParsingUtils.retrieveModels(myEnum, methodDeclaration, EventEntryForMetricEnumConstantReader.INSTANCE);
                events.addAll(entries);
            }
        }
        final String newName = name;
        events.forEach(event -> event.setName(newName + "." + event.getName()));
        Collections.sort(lowCardinalityTags);
        Collections.sort(highCardinalityTags);
        Collections.sort(events);

        return new MetricEntry(name, conventionClass, nameFromConventionClass, myEnum.getCanonicalName(), enumConstant.getName(), description, prefix, baseUnit, type, lowCardinalityTags,
                highCardinalityTags, overridesDefaultMetricFrom, events);
    }

}
