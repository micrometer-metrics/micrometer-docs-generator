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
package io.micrometer.docs.spans;

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
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.docs.commons.EventEntry;
import io.micrometer.docs.commons.EventEntryForSpanEnumConstantReader;
import io.micrometer.docs.commons.EventValueEntryEnumConstantReader;
import io.micrometer.docs.commons.KeyNameEntry;
import io.micrometer.docs.commons.KeyNameEnumConstantReader;
import io.micrometer.docs.commons.ParsingUtils;
import io.micrometer.docs.commons.utils.AsciidocUtils;
import io.micrometer.observation.docs.ObservationDocumentation;
import io.micrometer.tracing.docs.SpanDocumentation;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MethodDeclaration;
import org.jboss.forge.roaster.model.source.EnumConstantSource;
import org.jboss.forge.roaster.model.source.JavaEnumSource;
import org.jboss.forge.roaster.model.source.JavaSource;
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
        if (!enumSource.hasInterface(SpanDocumentation.class) && !enumSource.hasInterface(ObservationDocumentation.class)) {
            return FileVisitResult.CONTINUE;
        }
        logger.debug("Checking [" + javaSource.getName() + "]");
        if (enumSource.getEnumConstants().size() == 0) {
            return FileVisitResult.CONTINUE;
        }
        for (EnumConstantSource enumConstant : enumSource.getEnumConstants()) {
            SpanEntry entry = parseSpan(path, enumConstant, enumSource);
            if (entry != null) {
                if (entry.overridesDefaultSpanFrom != null && entry.tagKeys.isEmpty()) {
                    addTagsFromOverride(path, entry);
                }
                if (!entry.additionalKeyNames.isEmpty()) {
                    entry.tagKeys.addAll(entry.additionalKeyNames);
                }
                spanEntries.add(entry);
                logger.debug(
                        "Found [" + entry.tagKeys.size() + "] tags and [" + entry.events.size() + "] events");
            }
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
            logger.debug("Will remove the span entries <" + spansToRemove.stream().map(s -> s.name).collect(Collectors.joining(",")) + "> because they are overridden");
        }
        spanEntries.removeAll(spansToRemove);
        return FileVisitResult.CONTINUE;
    }

    // if entry has overridesDefaultSpanFrom - read tags from that thing
    // if entry has overridesDefaultSpanFrom AND getKeyNames() - we pick only the latter
    // if entry has overridesDefaultSpanFrom AND getAdditionalKeyNames() - we pick both
    private void addTagsFromOverride(Path file, SpanEntry entry) throws IOException {
        Map.Entry<String, String> overridesDefaultSpanFrom = entry.overridesDefaultSpanFrom;
        logger.debug("Reading additional meta data from [" + overridesDefaultSpanFrom + "]");
        String className = overridesDefaultSpanFrom.getKey();
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
            if (!enumConstant.getName().equals(overridesDefaultSpanFrom.getValue())) {
                continue;
            }
            Collection<KeyNameEntry> low = ParsingUtils.getTags(enumConstant, enumSource, "getLowCardinalityKeyNames");
            Collection<KeyNameEntry> high = ParsingUtils.getTags(enumConstant, enumSource, "getHighCardinalityKeyNames");
            entry.tagKeys.addAll(low);
            entry.tagKeys.addAll(high);
        }
    }

    private SpanEntry parseSpan(Path file, EnumConstantSource enumConstant, JavaEnumSource myEnum) {
        List<MemberSource<EnumConstantSource.Body, ?>> members = enumConstant.getBody().getMembers();
        if (members.isEmpty()) {
            return null;
        }
        String name = "";
        String contextualName = null;
        String description = AsciidocUtils.javadocToAsciidoc(enumConstant.getJavaDoc());
        String prefix = "";
        List<KeyNameEntry> tags = new ArrayList<>();
        List<KeyNameEntry> additionalKeyNames = new ArrayList<>();
        List<EventEntry> events = new ArrayList<>();
        Map.Entry<String, String> overridesDefaultSpanFrom = null;
        String conventionClass = null;
        String nameFromConventionClass = null;
        for (MemberSource<EnumConstantSource.Body, ?> member : members) {
            Object internal = member.getInternal();
            if (!(internal instanceof MethodDeclaration)) {
                return null;
            }
            MethodDeclaration methodDeclaration = (MethodDeclaration) internal;
            String methodName = methodDeclaration.getName().getIdentifier();
            // SpanDocumentation, ObservationDocumentation
            if ("getName".equals(methodName)) {
                name = ParsingUtils.readStringReturnValue(methodDeclaration);
            }
            // ObservationDocumentation
            else if ("getDefaultConvention".equals(methodName)) {
                conventionClass = ParsingUtils.readClass(methodDeclaration);
                nameFromConventionClass = ParsingUtils.tryToReadStringReturnValue(file, conventionClass);
            }
            // ObservationDocumentation
            else if ("getContextualName".equals(methodName)) {
                contextualName = ParsingUtils.readStringReturnValue(methodDeclaration);
            }
            // SpanDocumentation
            else if ("getKeyNames".equals(methodName)) {
                tags.addAll(ParsingUtils.retrieveModels(myEnum, methodDeclaration, KeyNameEnumConstantReader.INSTANCE));
            }
            // ObservationDocumentation
            else if ("getLowCardinalityKeyNames".equals(methodName)) {
                tags.addAll(ParsingUtils.retrieveModels(myEnum, methodDeclaration, KeyNameEnumConstantReader.INSTANCE));
            }
            // ObservationDocumentation
            else if ("getHighCardinalityKeyNames".equals(methodName)) {
                tags.addAll(ParsingUtils.retrieveModels(myEnum, methodDeclaration, KeyNameEnumConstantReader.INSTANCE));
            }
            // SpanDocumentation
            else if ("getAdditionalKeyNames".equals(methodName)) {
                additionalKeyNames.addAll(ParsingUtils.retrieveModels(myEnum, methodDeclaration, KeyNameEnumConstantReader.INSTANCE));
            }
            // SpanDocumentation(EventValue), ObservationDocumentation(Observation.Event)
            else if ("getEvents".equals(methodName)) {
                if (methodDeclaration.getReturnType2().toString().contains("EventValue")) {
                    events.addAll(ParsingUtils.retrieveModels(myEnum, methodDeclaration, EventValueEntryEnumConstantReader.INSTANCE));
                }
                else {
                    events.addAll(ParsingUtils.retrieveModels(myEnum, methodDeclaration, EventEntryForSpanEnumConstantReader.INSTANCE));
                }
            }
            // SpanDocumentation, ObservationDocumentation
            else if ("getPrefix".equals(methodName)) {
                prefix = ParsingUtils.readStringReturnValue(methodDeclaration);
            }
            // SpanDocumentation
            else if ("overridesDefaultSpanFrom".equals(methodName)) {
                overridesDefaultSpanFrom = ParsingUtils.readClassToEnum(methodDeclaration);
            }
        }

        Collections.sort(tags);
        Collections.sort(additionalKeyNames);
        Collections.sort(events);

        return new SpanEntry(contextualName != null ? contextualName : name, conventionClass, nameFromConventionClass, myEnum.getCanonicalName(), enumConstant.getName(), description, prefix, tags,
                additionalKeyNames, events, overridesDefaultSpanFrom);
    }

}
