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

package io.micrometer.docs.metrics;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.docs.DocumentedMeter;
import io.micrometer.core.util.internal.logging.InternalLogger;
import io.micrometer.core.util.internal.logging.InternalLoggerFactory;
import io.micrometer.docs.commons.KeyValueEntry;
import io.micrometer.docs.commons.ParsingUtils;
import io.micrometer.docs.commons.utils.ClassUtils;
import io.micrometer.observation.Observation;
import io.micrometer.observation.docs.DocumentedObservation;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MethodDeclaration;
import org.jboss.forge.roaster.model.JavaType;
import org.jboss.forge.roaster.model.JavaUnit;
import org.jboss.forge.roaster.model.impl.JavaEnumImpl;
import org.jboss.forge.roaster.model.source.EnumConstantSource;
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
            if (!(myClass instanceof JavaEnumImpl)) {
                return FileVisitResult.CONTINUE;
            }
            JavaEnumImpl myEnum = (JavaEnumImpl) myClass;
            if (Stream.of(DocumentedMeter.class.getCanonicalName(), DocumentedObservation.class.getCanonicalName()).noneMatch(ds -> myEnum.getInterfaces().contains(ds))) {
                return FileVisitResult.CONTINUE;
            }
            logger.info("Checking [" + myEnum.getName() + "]");
            if (myEnum.getEnumConstants().size() == 0) {
                return FileVisitResult.CONTINUE;
            }
            for (EnumConstantSource enumConstant : myEnum.getEnumConstants()) {
                MetricEntry entry = parseMetric(file, enumConstant, myEnum);
                if (entry != null) {
                    sampleEntries.add(entry);
                    logger.info(
                            "Found [" + entry.lowCardinalityKeyNames.size() + "] low cardinality tags and [" + entry.highCardinalityKeyNames.size() + "] high cardinality tags");
                }
                if (entry != null) {
                    if (entry.overridesDefaultMetricFrom != null && entry.lowCardinalityKeyNames.isEmpty()) {
                        addTagsFromOverride(file, entry);
                    }
                    sampleEntries.add(entry);
                    logger.info(
                            "Found [" + entry.lowCardinalityKeyNames.size() + "]");
                }
            }
            return FileVisitResult.CONTINUE;
        } catch (Exception e) {
            logger.error("Failed to parse file [" + file + "] due to an error", e);
        }
        return FileVisitResult.CONTINUE;
    }

    // if entry has overridesDefaultSpanFrom - read tags from that thing
    // if entry has overridesDefaultSpanFrom AND getKeyNames() - we pick only the latter
    // if entry has overridesDefaultSpanFrom AND getAdditionalKeyNames() - we pick both
    private void addTagsFromOverride(Path file, MetricEntry entry) throws IOException {
        Map.Entry<String, String> overrideDefaults = entry.overridesDefaultMetricFrom;
        logger.info("Reading additional meta data from [" + overrideDefaults + "]");
        String className = overrideDefaults.getKey();
        File parent = file.getParent().toFile();
        while (!parent.getAbsolutePath().endsWith(File.separator + "java")) {
            parent = parent.getParentFile();
        }
        String filePath = new File(parent, className.replace(".", File.separator) + ".java").getAbsolutePath();
        try (InputStream streamForOverride = Files.newInputStream(new File(filePath).toPath())) {
            JavaUnit parsedForOverride = Roaster.parseUnit(streamForOverride);
            JavaType overrideClass = parsedForOverride.getGoverningType();
            if (!(overrideClass instanceof JavaEnumImpl)) {
                return;
            }
            JavaEnumImpl myEnum = (JavaEnumImpl) overrideClass;
            if (!myEnum.getInterfaces().contains(DocumentedObservation.class.getCanonicalName())) {
                return;
            }
            logger.info("Checking [" + myEnum.getName() + "]");
            if (myEnum.getEnumConstants().size() == 0) {
                return;
            }
            for (EnumConstantSource enumConstant : myEnum.getEnumConstants()) {
                if (!enumConstant.getName().equals(overrideDefaults.getValue())) {
                    continue;
                }
                Collection<KeyValueEntry> low = ParsingUtils.getTags(enumConstant, myEnum, "getLowCardinalityKeyNames");
                if (low != null) {
                    entry.lowCardinalityKeyNames.addAll(low);
                }
            }
        }
    }

    private MetricEntry parseMetric(Path file, EnumConstantSource enumConstant, JavaEnumImpl myEnum) {
        List<MemberSource<EnumConstantSource.Body, ?>> members = enumConstant.getBody().getMembers();
        if (members.isEmpty()) {
            return null;
        }
        String name = "";
        String description = enumConstant.getJavaDoc().getText();
        String prefix = "";
        String baseUnit = "";
        Meter.Type type = Meter.Type.TIMER;
        Collection<KeyValueEntry> lowCardinalityTags = new TreeSet<>();
        Collection<KeyValueEntry> highCardinalityTags = new TreeSet<>();
        Map.Entry<String, String> overridesDefaultMetricFrom = null;
        Class<? extends Observation.ObservationConvention<?>> conventionClass = null;
        String nameFromConventionClass = null;
        for (MemberSource<EnumConstantSource.Body, ?> member : members) {
            Object internal = member.getInternal();
            if (!(internal instanceof MethodDeclaration)) {
                return null;
            }
            MethodDeclaration methodDeclaration = (MethodDeclaration) internal;
            String methodName = methodDeclaration.getName().getIdentifier();
            if ("getName".equals(methodName)) {
                name = ParsingUtils.readStringReturnValue(methodDeclaration);
            }
            else if ("getDefaultConvention".equals(methodName)) {
                String fqn = ParsingUtils.readClass(methodDeclaration);
                conventionClass = ClassUtils.clazz(fqn);
                nameFromConventionClass = ParsingUtils.tryToReadStringReturnValue(file, conventionClass);
            }
            else if ("getLowCardinalityKeyNames".equals(methodName) || "getKeyNames".equals(methodName)) {
                lowCardinalityTags.addAll(ParsingUtils.keyValueEntries(myEnum, methodDeclaration, KeyName.class));
            }
            else if ("getHighCardinalityKeyNames".equals(methodName)) {
                highCardinalityTags.addAll(ParsingUtils.keyValueEntries(myEnum, methodDeclaration, KeyName.class));
            }
            else if ("getPrefix".equals(methodName)) {
                prefix = ParsingUtils.readStringReturnValue(methodDeclaration);
            }
            else if ("getBaseUnit".equals(methodName)) {
                baseUnit = ParsingUtils.readStringReturnValue(methodDeclaration);
            }
            else if ("getType".equals(methodName)) {
                type = ParsingUtils.enumFromReturnMethodDeclaration(methodDeclaration, Meter.Type.class);
            }
            else if ("getDescription".equals(methodName)) {
                description = ParsingUtils.readStringReturnValue(methodDeclaration);
            }
            else if ("overridesDefaultMetricFrom".equals(methodName)) {
                overridesDefaultMetricFrom = ParsingUtils.readClassToEnum(methodDeclaration);
            }
        }
        return new MetricEntry(name, conventionClass, nameFromConventionClass, myEnum.getCanonicalName(), enumConstant.getName(), description, prefix, baseUnit, type, lowCardinalityTags,
                highCardinalityTags, overridesDefaultMetricFrom);
    }

}
