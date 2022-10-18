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
package io.micrometer.docs.spans;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.docs.commons.EntryEnumConstantReader;
import io.micrometer.docs.commons.EventEntry;
import io.micrometer.docs.commons.EventEntryForSpanEnumConstantReader;
import io.micrometer.docs.commons.EventValueEntryEnumConstantReader;
import io.micrometer.docs.commons.JavaSourceSearchHelper;
import io.micrometer.docs.commons.KeyNameEntry;
import io.micrometer.docs.commons.KeyNameEnumConstantReader;
import io.micrometer.docs.commons.ParsingUtils;
import io.micrometer.docs.commons.utils.AsciidocUtils;
import io.micrometer.docs.commons.utils.Assert;
import io.micrometer.observation.docs.ObservationDocumentation;
import io.micrometer.tracing.docs.SpanDocumentation;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Expression;
import org.jboss.forge.roaster.model.source.EnumConstantSource;
import org.jboss.forge.roaster.model.source.EnumConstantSource.Body;
import org.jboss.forge.roaster.model.source.JavaEnumSource;
import org.jboss.forge.roaster.model.source.JavaSource;
import org.jboss.forge.roaster.model.source.MethodSource;

class SpanSearchingFileVisitor extends SimpleFileVisitor<Path> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SpanSearchingFileVisitor.class);

    private final Pattern pattern;

    private final Collection<SpanEntry> spanEntries;

    private final JavaSourceSearchHelper searchHelper;

    /**
     * The enclosing enum classes for overriding will be excluded from documentation
     */
    private final Set<String> overrideEnumClassNames = new HashSet<>();

    SpanSearchingFileVisitor(Pattern pattern, Collection<SpanEntry> spanEntries, JavaSourceSearchHelper searchHelper) {
        this.pattern = pattern;
        this.spanEntries = spanEntries;
        this.searchHelper = searchHelper;
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
        if (enumSource.getMethods().size() > 0) {
            String message = String.format("The enum constants can define methods but the container enum class(%s) cannot define methods.", enumSource.getName());
            throw new RuntimeException(message);
        }
        if (enumSource.getEnumConstants().size() == 0) {
            return FileVisitResult.CONTINUE;
        }
        for (EnumConstantSource enumConstant : enumSource.getEnumConstants()) {
            if (enumConstant.getBody().getMethods().isEmpty()) {
                continue;
            }
            SpanEntry entry = parseSpan(enumConstant, enumSource);
            if (!entry.additionalKeyNames.isEmpty()) {
                entry.tagKeys.addAll(entry.additionalKeyNames);
            }
            spanEntries.add(entry);
            logger.debug(
                    "Found [" + entry.tagKeys.size() + "] tags and [" + entry.events.size() + "] events");
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        Set<SpanEntry> toRemove = spanEntries.stream()
                .filter(spanEntry -> this.overrideEnumClassNames.contains(spanEntry.enclosingClass))
                .collect(Collectors.toSet());
        if (!toRemove.isEmpty()) {
            logger.debug("Will remove the span entries <" + toRemove.stream().map(s -> s.name).collect(Collectors.joining(",")) + "> because they are overridden");
        }
        spanEntries.removeAll(toRemove);
        return FileVisitResult.CONTINUE;
    }

    private SpanEntry parseSpan(EnumConstantSource enumConstant, JavaEnumSource myEnum) {
        String name = "";
        String contextualName = null;
        String description = AsciidocUtils.javadocToAsciidoc(enumConstant.getJavaDoc());
        String prefix = "";
        List<KeyNameEntry> tags = new ArrayList<>();
        List<KeyNameEntry> additionalKeyNames = new ArrayList<>();
        List<EventEntry> events = new ArrayList<>();
        EnumConstantSource overridesDefaultSpanFrom = null;
        String conventionClass = null;
        String nameFromConventionClass = null;


        MethodSource<?> methodSource;
        Body enumConstantBody = enumConstant.getBody();

        // SpanDocumentation, ObservationDocumentation
        methodSource = enumConstantBody.getMethod("getName");
        if (methodSource != null) {
            name = ParsingUtils.readStringReturnValue(methodSource);
        }

        // ObservationDocumentation
        methodSource = enumConstantBody.getMethod("getDefaultConvention");
        if (methodSource != null) {
            // TODO: may share the logic with the metrics side
            // this returns canonical name. e.g. "io.micrometer.Foo.Bar" where qualified name is "io.micrometer.Foo$Bar"
            String conventionClassName = ParsingUtils.readStringReturnValue(methodSource);
            JavaSource<?> conventionClassSource = this.searchHelper.searchReferencingClass(myEnum, conventionClassName);
            if (conventionClassSource == null) {
                throw new RuntimeException("Cannot find the source java file for " + conventionClassName);
            }
            MethodSource<?> getNameMethodSource = this.searchHelper.searchMethodSource(conventionClassSource, "getName");
            if (getNameMethodSource == null) {
                throw new RuntimeException("Cannot find getName() method in the hierarchy of " + conventionClassName);
            }
            nameFromConventionClass = ParsingUtils.readStringReturnValue(getNameMethodSource);
            conventionClass = conventionClassSource.getQualifiedName();
        }

        // ObservationDocumentation
        methodSource = enumConstantBody.getMethod("getContextualName");
        if (methodSource != null) {
            contextualName = ParsingUtils.readStringReturnValue(methodSource);
        }

        // SpanDocumentation
        methodSource = enumConstantBody.getMethod("getKeyNames");
        if (methodSource != null) {
            tags.addAll((retrieveEnumValues(myEnum, methodSource, KeyNameEnumConstantReader.INSTANCE)));

        }

        // ObservationDocumentation
        methodSource = enumConstantBody.getMethod("getLowCardinalityKeyNames");
        if (methodSource != null) {
            tags.addAll((retrieveEnumValues(myEnum, methodSource, KeyNameEnumConstantReader.INSTANCE)));
        }

        // ObservationDocumentation
        methodSource = enumConstantBody.getMethod("getHighCardinalityKeyNames");
        if (methodSource != null) {
            tags.addAll((retrieveEnumValues(myEnum, methodSource, KeyNameEnumConstantReader.INSTANCE)));
        }

        // SpanDocumentation
        methodSource = enumConstantBody.getMethod("getAdditionalKeyNames");
        if (methodSource != null) {
            additionalKeyNames.addAll((retrieveEnumValues(myEnum, methodSource, KeyNameEnumConstantReader.INSTANCE)));
        }

        // SpanDocumentation(EventValue), ObservationDocumentation(Observation.Event)
        methodSource = enumConstantBody.getMethod("getEvents");
        if (methodSource != null) {
            if ("EventValue".equals(methodSource.getReturnType().getSimpleName())) {
                events.addAll((retrieveEnumValues(myEnum, methodSource, EventValueEntryEnumConstantReader.INSTANCE)));
            }
            else {
                events.addAll((retrieveEnumValues(myEnum, methodSource, EventEntryForSpanEnumConstantReader.INSTANCE)));
            }
        }

        // SpanDocumentation, ObservationDocumentation
        methodSource = enumConstantBody.getMethod("getPrefix");
        if (methodSource != null) {
            prefix = ParsingUtils.readStringReturnValue(methodSource);
        }

        // SpanDocumentation
        methodSource = enumConstantBody.getMethod("overridesDefaultSpanFrom");
        if (methodSource != null) {
            Expression expression = ParsingUtils.expressionFromReturnMethodDeclaration(methodSource);
            Assert.notNull(expression, "Failed to parse the expression from " + methodSource);
            overridesDefaultSpanFrom = this.searchHelper.searchReferencingEnumConstant(myEnum, expression);
            if (overridesDefaultSpanFrom != null) {
                overrideEnumClassNames.add(overridesDefaultSpanFrom.getOrigin().getQualifiedName());
            }
        }

        // prepare view model objects

        // if entry has overridesDefaultSpanFrom - read tags from that thing
        // if entry has overridesDefaultSpanFrom AND getKeyNames() - we pick only the latter
        // if entry has overridesDefaultSpanFrom AND getAdditionalKeyNames() - we pick both
        if (overridesDefaultSpanFrom != null && tags.isEmpty()) {
            List<KeyNameEntry> lows = getKeyNameEntriesFromEnumConstant(overridesDefaultSpanFrom, "getLowCardinalityKeyNames");
            List<KeyNameEntry> highs = getKeyNameEntriesFromEnumConstant(overridesDefaultSpanFrom, "getHighCardinalityKeyNames");
            tags.addAll(lows);
            tags.addAll(highs);
        }

        Collections.sort(tags);
        Collections.sort(additionalKeyNames);
        Collections.sort(events);

        return new SpanEntry(contextualName != null ? contextualName : name, conventionClass, nameFromConventionClass, myEnum.getCanonicalName(), enumConstant.getName(), description, prefix, tags,
                additionalKeyNames, events);
    }


    private List<KeyNameEntry> getKeyNameEntriesFromEnumConstant(EnumConstantSource enumConstantSource, String methodName) {
        List<KeyNameEntry> tags = new ArrayList<>();
        MethodSource<?> methodSource = enumConstantSource.getBody().getMethod(methodName);
        if (methodSource != null) {
            JavaEnumSource enclosingEnumSource = enumConstantSource.getOrigin();
            List<KeyNameEntry> keys = retrieveEnumValues(enclosingEnumSource, methodSource, KeyNameEnumConstantReader.INSTANCE);
            tags.addAll(keys);
        }
        return tags;
    }

    private <T> List<T> retrieveEnumValues(JavaSource<?> enclosingJavaSource, MethodSource<?> methodSource, EntryEnumConstantReader<?> converter) {
        List<T> result = new ArrayList<>();
        Set<String> enumClassNames = ParsingUtils.readEnumClassNames(methodSource);
        for (String enumClassName : enumClassNames) {
            JavaSource<?> enclosingEnumClass = this.searchHelper.searchReferencingClass(enclosingJavaSource, enumClassName);
            if (enclosingEnumClass == null || !enclosingEnumClass.isEnum()) {
                throw new IllegalStateException("Cannot find enum class with name [" + enumClassName + "]");
            }
            result.addAll(ParsingUtils.retrieveModelsFromEnum((JavaEnumSource) enclosingEnumClass, converter));
        }
        return result;
    }

}
