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
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.micrometer.common.docs.KeyName;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.docs.commons.KeyValueEntry;
import io.micrometer.docs.commons.ParsingUtils;
import io.micrometer.observation.docs.DocumentedObservation;
import io.micrometer.tracing.docs.DocumentedSpan;
import io.micrometer.tracing.docs.EventValue;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MethodDeclaration;
import org.jboss.forge.roaster.model.JavaType;
import org.jboss.forge.roaster.model.JavaUnit;
import org.jboss.forge.roaster.model.impl.JavaEnumImpl;
import org.jboss.forge.roaster.model.source.EnumConstantSource;
import org.jboss.forge.roaster.model.source.MemberSource;

class SpanSearchingFileVisitor extends SimpleFileVisitor<Path> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SpanSearchingFileVisitor.class);

    private final Pattern pattern;

    private final Collection<SpanEntry> spanEntries;

    SpanSearchingFileVisitor(Pattern pattern, Collection<SpanEntry> spanEntries) {
        this.pattern = pattern;
        this.spanEntries = spanEntries;
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
            if (Stream.of(DocumentedSpan.class.getCanonicalName(), DocumentedObservation.class.getCanonicalName()).noneMatch(ds -> myEnum.getInterfaces().contains(ds))) {
                return FileVisitResult.CONTINUE;
            }
            logger.info("Checking [" + myEnum.getName() + "]");
            if (myEnum.getEnumConstants().size() == 0) {
                return FileVisitResult.CONTINUE;
            }
            for (EnumConstantSource enumConstant : myEnum.getEnumConstants()) {
                SpanEntry entry = parseSpan(enumConstant, myEnum);
                if (entry != null) {
                    if (entry.overridesDefaultSpanFrom != null && entry.tagKeys.isEmpty()) {
                        addTagsFromOverride(file, entry);
                    }
                    if (!entry.additionalKeyNames.isEmpty()) {
                        entry.tagKeys.addAll(entry.additionalKeyNames);
                    }
                    spanEntries.add(entry);
                    logger.info(
                            "Found [" + entry.tagKeys.size() + "] tags and [" + entry.events.size() + "] events");
                }
            }
            return FileVisitResult.CONTINUE;
        } catch (Exception e) {
            logger.error("Failed to parse file [" + file + "] due to an error", e);
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        List<String> overridingNames = spanEntries.stream().filter(s -> s.overridesDefaultSpanFrom != null)
                .map(spanEntry -> spanEntry.overridesDefaultSpanFrom.getKey())
                .collect(Collectors.toList());
        List<SpanEntry> spansToRemove = spanEntries.stream()
                .filter(spanEntry -> overridingNames.stream().anyMatch(name -> spanEntry.enclosingClass.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))))
                .collect(Collectors.toList());
        if (!spansToRemove.isEmpty()) {
            logger.info("Will remove the span entries <" + spansToRemove.stream().map(s -> s.name).collect(Collectors.joining(",")) + "> because they are overridden");
        }
        spanEntries.removeAll(spansToRemove);
        return FileVisitResult.CONTINUE;
    }

    // if entry has overridesDefaultSpanFrom - read tags from that thing
    // if entry has overridesDefaultSpanFrom AND getKeyNames() - we pick only the latter
    // if entry has overridesDefaultSpanFrom AND getAdditionalKeyNames() - we pick both
    private void addTagsFromOverride(Path file, SpanEntry entry) throws IOException {
        Map.Entry<String, String> overridesDefaultSpanFrom = entry.overridesDefaultSpanFrom;
        logger.info("Reading additional meta data from [" + overridesDefaultSpanFrom + "]");
        String className = overridesDefaultSpanFrom.getKey();
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
                if (!enumConstant.getName().equals(overridesDefaultSpanFrom.getValue())) {
                    continue;
                }
                Collection<KeyValueEntry> low = ParsingUtils.getTags(enumConstant, myEnum, "getLowCardinalityKeyNames");
                Collection<KeyValueEntry> high = ParsingUtils.getTags(enumConstant, myEnum, "getHighCardinalityKeyNames");
                if (low != null) {
                    entry.tagKeys.addAll(low);
                }
                if (high != null) {
                    entry.tagKeys.addAll(high);
                }
            }
        }
    }

    private SpanEntry parseSpan(EnumConstantSource enumConstant, JavaEnumImpl myEnum) {
        List<MemberSource<EnumConstantSource.Body, ?>> members = enumConstant.getBody().getMembers();
        if (members.isEmpty()) {
            return null;
        }
        String name = "";
        String contextualName = null;
        String description = enumConstant.getJavaDoc().getText();
        String prefix = "";
        Collection<KeyValueEntry> tags = new TreeSet<>();
        Collection<KeyValueEntry> additionalKeyNames = new TreeSet<>();
        Collection<KeyValueEntry> events = new TreeSet<>();
        Map.Entry<String, String> overridesDefaultSpanFrom = null;
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
            else if ("getContextualName".equals(methodName)) {
                contextualName = ParsingUtils.readStringReturnValue(methodDeclaration);
            }
            else if ("getKeyNames".equals(methodName)) {
                tags.addAll(ParsingUtils.keyValueEntries(myEnum, methodDeclaration, KeyName.class));
            }
            else if ("getLowCardinalityKeyNames".equals(methodName)) {
                tags.addAll(ParsingUtils.keyValueEntries(myEnum, methodDeclaration, KeyName.class));
            }
            else if ("getHighCardinalityKeyNames".equals(methodName)) {
                tags.addAll(ParsingUtils.keyValueEntries(myEnum, methodDeclaration, KeyName.class));
            }
            else if ("getAdditionalKeyNames".equals(methodName)) {
                additionalKeyNames.addAll(ParsingUtils.keyValueEntries(myEnum, methodDeclaration, KeyName.class));
            }
            else if ("getEvents".equals(methodName)) {
                events.addAll(ParsingUtils.keyValueEntries(myEnum, methodDeclaration, EventValue.class));
            }
            else if ("getPrefix".equals(methodName)) {
                prefix = ParsingUtils.readStringReturnValue(methodDeclaration);
            }
            else if ("overridesDefaultSpanFrom".equals(methodName)) {
                overridesDefaultSpanFrom = ParsingUtils.readClassToEnum(methodDeclaration);
            }
        }
        return new SpanEntry(contextualName != null ? contextualName : name, myEnum.getCanonicalName(), enumConstant.getName(), description, prefix, tags,
                additionalKeyNames, events, overridesDefaultSpanFrom);
    }

}
