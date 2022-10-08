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
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.docs.MeterDocumentation;
import io.micrometer.docs.commons.EventEntry;
import io.micrometer.docs.commons.EventEntryForMetricEnumReader;
import io.micrometer.docs.commons.KeyNameEntry;
import io.micrometer.docs.commons.KeyNameEnumReader;
import io.micrometer.docs.commons.ParsingUtils;
import io.micrometer.docs.commons.utils.AsciidocUtils;
import io.micrometer.observation.docs.ObservationDocumentation;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MethodDeclaration;
import org.jboss.forge.roaster.model.JavaType;
import org.jboss.forge.roaster.model.JavaUnit;
import org.jboss.forge.roaster.model.source.EnumConstantSource;
import org.jboss.forge.roaster.model.source.JavaEnumSource;
import org.jboss.forge.roaster.model.source.MemberSource;

class MetricSearchingFileVisitor extends SimpleFileVisitor<Path> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(MetricSearchingFileVisitor.class);

    private final Pattern pattern;

    private final Collection<MetricEntry> sampleEntries;

    MetricSearchingFileVisitor(Pattern pattern, Collection<MetricEntry> sampleEntries) {
        this.pattern = pattern;
        this.sampleEntries = sampleEntries;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (!pattern.matcher(file.toString()).matches()) {
            return FileVisitResult.CONTINUE;
        }
        else if (!file.toString().endsWith(".java")) {
            return FileVisitResult.CONTINUE;
        }
        try (InputStream stream = Files.newInputStream(file)) {
            JavaUnit unit = Roaster.parseUnit(stream);
            JavaType myClass = unit.getGoverningType();
            if (!(myClass instanceof JavaEnumSource)) {
                return FileVisitResult.CONTINUE;
            }
            JavaEnumSource myEnum = (JavaEnumSource) myClass;
            if (Stream.of(MeterDocumentation.class.getCanonicalName(), ObservationDocumentation.class.getCanonicalName()).noneMatch(ds -> myEnum.getInterfaces().contains(ds))) {
                return FileVisitResult.CONTINUE;
            }
            logger.debug("Checking [" + myEnum.getName() + "]");
            if (myEnum.getEnumConstants().size() == 0) {
                return FileVisitResult.CONTINUE;
            }
            for (EnumConstantSource enumConstant : myEnum.getEnumConstants()) {
                MetricEntry entry = parseMetric(file, enumConstant, myEnum);
                if (entry != null) {
                    sampleEntries.add(entry);
                    logger.debug(
                            "Found [" + entry.lowCardinalityKeyNames.size() + "] low cardinality tags and [" + entry.highCardinalityKeyNames.size() + "] high cardinality tags");
                }
                if (entry != null) {
                    if (entry.overridesDefaultMetricFrom != null && entry.lowCardinalityKeyNames.isEmpty()) {
                        addTagsFromOverride(file, entry);
                    }
                    sampleEntries.add(entry);
                    logger.debug(
                            "Found [" + entry.lowCardinalityKeyNames.size() + "]");
                }
            }
            return FileVisitResult.CONTINUE;
        }
        catch (Exception e) {
            throw new IOException("Failed to parse file [" + file + "] due to an error", e);
        }
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
        String filePath = new File(parent, className.replace(".", File.separator) + ".java").getAbsolutePath();
        try (InputStream streamForOverride = Files.newInputStream(new File(filePath).toPath())) {
            JavaUnit parsedForOverride = Roaster.parseUnit(streamForOverride);
            JavaType overrideClass = parsedForOverride.getGoverningType();
            if (!(overrideClass instanceof JavaEnumSource)) {
                return;
            }
            JavaEnumSource myEnum = (JavaEnumSource) overrideClass;
            if (!myEnum.getInterfaces().contains(ObservationDocumentation.class.getCanonicalName())) {
                return;
            }
            logger.debug("Checking [" + myEnum.getName() + "]");
            if (myEnum.getEnumConstants().size() == 0) {
                return;
            }
            for (EnumConstantSource enumConstant : myEnum.getEnumConstants()) {
                if (!enumConstant.getName().equals(overrideDefaults.getValue())) {
                    continue;
                }
                Collection<KeyNameEntry> low = ParsingUtils.getTags(enumConstant, myEnum, "getLowCardinalityKeyNames");
                if (low != null) {
                    entry.lowCardinalityKeyNames.addAll(low);
                }
            }
        }
    }

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
                return null;
            }
            MethodDeclaration methodDeclaration = (MethodDeclaration) internal;
            String methodName = methodDeclaration.getName().getIdentifier();
            // MeterDocumentation, ObservationDocumentation
            if ("getName".equals(methodName)) {
                name = ParsingUtils.readStringReturnValue(methodDeclaration);
            }
            // MeterDocumentation
            else if ("getKeyNames".equals(methodName)) {
                lowCardinalityTags.addAll(ParsingUtils.retrieveModels(myEnum, methodDeclaration, KeyNameEnumReader.INSTANCE));
            }
            // ObservationDocumentation(@Nullable)
            else if ("getDefaultConvention".equals(methodName)) {
                conventionClass = ParsingUtils.readClass(methodDeclaration);
                nameFromConventionClass = ParsingUtils.tryToReadStringReturnValue(file, conventionClass);
            }
            // ObservationDocumentation
            else if ("getLowCardinalityKeyNames".equals(methodName) || "asString".equals(methodName)) {
                lowCardinalityTags.addAll(ParsingUtils.retrieveModels(myEnum, methodDeclaration, KeyNameEnumReader.INSTANCE));
            }
            // ObservationDocumentation
            else if ("getHighCardinalityKeyNames".equals(methodName)) {
                highCardinalityTags.addAll(ParsingUtils.retrieveModels(myEnum, methodDeclaration, KeyNameEnumReader.INSTANCE));
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
                Collection<EventEntry> entries = ParsingUtils.retrieveModels(myEnum, methodDeclaration, EventEntryForMetricEnumReader.INSTANCE);
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
