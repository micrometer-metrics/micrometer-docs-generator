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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.docs.commons.AbstractSearchingFileVisitor;
import io.micrometer.docs.commons.EventEntry;
import io.micrometer.docs.commons.EventEntryForSpanEnumConstantReader;
import io.micrometer.docs.commons.EventValueEntryEnumConstantReader;
import io.micrometer.docs.commons.JavaSourceSearchHelper;
import io.micrometer.docs.commons.KeyNameEntry;
import io.micrometer.docs.commons.KeyNameEnumConstantReader;
import io.micrometer.docs.commons.ParsingUtils;
import io.micrometer.docs.commons.utils.AsciidocUtils;
import io.micrometer.docs.commons.utils.Assert;
import io.micrometer.docs.commons.utils.StringUtils;
import io.micrometer.observation.docs.ObservationDocumentation;
import io.micrometer.tracing.docs.SpanDocumentation;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Expression;
import org.jboss.forge.roaster.model.source.EnumConstantSource;
import org.jboss.forge.roaster.model.source.EnumConstantSource.Body;
import org.jboss.forge.roaster.model.source.JavaEnumSource;
import org.jboss.forge.roaster.model.source.JavaSource;
import org.jboss.forge.roaster.model.source.MethodSource;

class SpanSearchingFileVisitor extends AbstractSearchingFileVisitor {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SpanSearchingFileVisitor.class);

    private final Collection<SpanEntry> spanEntries;

    /**
     * The enclosing enum classes for overriding will be excluded from documentation
     */
    private final Set<String> overrideEnumClassNames = new HashSet<>();

    SpanSearchingFileVisitor(Pattern pattern, Collection<SpanEntry> spanEntries, JavaSourceSearchHelper searchHelper) {
        super(pattern, searchHelper);
        this.spanEntries = spanEntries;
    }

    @Override
    public Collection<Class<?>> supportedInterfaces() {
        return Arrays.asList(SpanDocumentation.class, ObservationDocumentation.class);
    }

    @Override
    public void onEnumConstant(JavaEnumSource enclosingEnumSource, EnumConstantSource enumConstant) {
        SpanEntry entry = parseSpan(enumConstant, enclosingEnumSource);
        spanEntries.add(entry);
        logger.debug(
                "Found [" + entry.tagKeys.size() + "] tags and [" + entry.events.size() + "] events");
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        Set<SpanEntry> toRemove = this.spanEntries.stream()
                .filter(spanEntry -> this.overrideEnumClassNames.contains(spanEntry.enclosingClass))
                .collect(Collectors.toSet());
        if (!toRemove.isEmpty()) {
            logger.debug("Will remove the span entries <" + toRemove.stream().map(s -> s.name).collect(Collectors.joining(",")) + "> because they are overridden");
        }
        spanEntries.removeAll(toRemove);

        validatePrefixOnTags();
        return FileVisitResult.CONTINUE;
    }

    void validatePrefixOnTags() {
        List<String> messages = new ArrayList<>();
        for (SpanEntry spanEntry : this.spanEntries) {
            String prefix = spanEntry.getPrefix();
            if (!StringUtils.hasText(prefix)) {
                continue;
            }
            String enumName = spanEntry.getEnumName();
            String enclosingClass = spanEntry.getEnclosingClass();
            List<String> wrongTags = spanEntry.getTagKeys().stream().map(KeyNameEntry::getName).filter(tagName -> !tagName.startsWith(prefix)).collect(Collectors.toList());
            for (String wrongTag : wrongTags) {
                String message = String.format("\tName <%s> in class <%s> has the following prefix <%s> and following invalid tag keys %s", enumName, enclosingClass, prefix, wrongTag);
                messages.add(message);
            }
        }
        if (!messages.isEmpty()) {
            StringBuilder sb = new StringBuilder("The following documented objects do not have properly prefixed tag keys according to their prefix() method. Please align the tag keys.");
            sb.append(System.lineSeparator()).append(System.lineSeparator());
            sb.append(messages.stream().collect(Collectors.joining(System.lineSeparator())));
            sb.append(System.lineSeparator()).append(System.lineSeparator());
            throw new IllegalStateException(sb.toString());
        }
    }

    private SpanEntry parseSpan(EnumConstantSource enumConstant, JavaEnumSource myEnum) {
        boolean isObservationDoc = myEnum.hasInterface(ObservationDocumentation.class);

        String description = AsciidocUtils.javadocToAsciidoc(enumConstant.getJavaDoc());
        String prefix = "";
        List<KeyNameEntry> tags = new ArrayList<>();
        List<KeyNameEntry> additionalKeyNames = new ArrayList<>();
        List<EventEntry> events = new ArrayList<>();
        EnumConstantSource overridesDefaultSpanFrom = null;


        MethodSource<?> methodSource;
        Body enumConstantBody = enumConstant.getBody();

        // resolve name from "getContextName", "getName", and "getDefaultConvention"
        NameInfo nameInfo = resolveName(isObservationDoc, enumConstant, myEnum);

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
            tags.addAll(additionalKeyNames);
        }

        Collections.sort(tags);
        Collections.sort(additionalKeyNames);
        Collections.sort(events);

        String name = nameInfo.getName();
        String nameOrigin = nameInfo.getNameOrigin();

        return new SpanEntry(name, nameOrigin, myEnum.getCanonicalName(), enumConstant.getName(), description, prefix, tags, events);
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

    private NameInfo resolveName(boolean isObservationDoc, EnumConstantSource enumConstant, JavaEnumSource enclosingEnum) {
        Body enumConstantBody = enumConstant.getBody();

        String name = "";
        MethodSource<?> methodSource;

        // getContextualName - ObservationDocumentation
        methodSource = enumConstantBody.getMethod("getContextualName");
        if (methodSource != null) {
            name = ParsingUtils.readStringReturnValue(methodSource);
        }

        // getName - SpanDocumentation, ObservationDocumentation
        if (!StringUtils.hasText(name)) {
            methodSource = enumConstantBody.getMethod("getName");
            if (methodSource != null) {
                name = ParsingUtils.readStringReturnValue(methodSource);
            }
        }

        if (!isObservationDoc) {
            return new NameInfo(name, "");
        }

        // getDefaultConvention - ObservationDocumentation
        String conventionClassName = null;
        methodSource = enumConstantBody.getMethod("getDefaultConvention");
        if (methodSource != null) {
            conventionClassName = ParsingUtils.readStringReturnValue(methodSource);
        }

        validateNameOrConvention(name, conventionClassName, enclosingEnum);

        if (!StringUtils.hasText(conventionClassName)) {
            return new NameInfo(name, "");
        }

        JavaSource<?> conventionClassSource = this.searchHelper.searchReferencingClass(enclosingEnum, conventionClassName);
        if (conventionClassSource == null) {
            throw new RuntimeException("Cannot find the source java file for " + conventionClassName);
        }
        MethodSource<?> getNameMethodSource = this.searchHelper.searchMethodSource(conventionClassSource, "getName");
        if (getNameMethodSource == null) {
            throw new RuntimeException("Cannot find getName() method in the hierarchy of " + conventionClassName);
        }
        name = ParsingUtils.readStringReturnValue(getNameMethodSource);
        return new NameInfo(name, conventionClassSource.getQualifiedName());
    }

}
