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
package io.micrometer.docs.metrics;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.docs.MeterDocumentation;
import io.micrometer.docs.commons.EntryEnumConstantReader;
import io.micrometer.docs.commons.EventEntry;
import io.micrometer.docs.commons.EventEntryForMetricEnumConstantReader;
import io.micrometer.docs.commons.JavaSourceSearchHelper;
import io.micrometer.docs.commons.KeyNameEntry;
import io.micrometer.docs.commons.KeyNameEnumConstantReader;
import io.micrometer.docs.commons.ParsingUtils;
import io.micrometer.docs.commons.utils.AsciidocUtils;
import io.micrometer.docs.commons.utils.Assert;
import io.micrometer.docs.commons.utils.StringUtils;
import io.micrometer.docs.metrics.MetricEntry.MetricInfo;
import io.micrometer.observation.docs.ObservationDocumentation;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Expression;
import org.jboss.forge.roaster.model.source.EnumConstantSource;
import org.jboss.forge.roaster.model.source.EnumConstantSource.Body;
import org.jboss.forge.roaster.model.source.JavaEnumSource;
import org.jboss.forge.roaster.model.source.JavaSource;
import org.jboss.forge.roaster.model.source.MethodSource;

class MetricSearchingFileVisitor extends SimpleFileVisitor<Path> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(MetricSearchingFileVisitor.class);

    private final Pattern pattern;

    private final Collection<MetricEntry> entries;

    private final JavaSourceSearchHelper searchHelper;

    MetricSearchingFileVisitor(Pattern pattern, Collection<MetricEntry> entries, JavaSourceSearchHelper searchHelper) {
        this.pattern = pattern;
        this.entries = entries;
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
        if (!enumSource.hasInterface(MeterDocumentation.class) && !enumSource.hasInterface(ObservationDocumentation.class)) {
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
            MetricEntry entry = parseMetric(enumConstant, enumSource);
            entries.add(entry);
            logger.debug("Found [" + entry.lowCardinalityKeyNames.size() + "]");
        }
        return FileVisitResult.CONTINUE;
    }

    private MetricEntry parseMetric(EnumConstantSource enumConstant, JavaEnumSource myEnum) {
        String name = "";
        String description = AsciidocUtils.javadocToAsciidoc(enumConstant.getJavaDoc());
        String prefix = "";
        String baseUnit = "";
        Meter.Type type = Meter.Type.TIMER;
        List<KeyNameEntry> lowCardinalityTags = new ArrayList<>();
        List<KeyNameEntry> highCardinalityTags = new ArrayList<>();
        EnumConstantSource overridesDefaultMetricFrom = null;
        String conventionClass = null; // convention class's qualified name
        String nameFromConventionClass = null;
        List<EventEntry> events = new ArrayList<>();

        MethodSource<?> methodSource;
        Body enumConstantBody = enumConstant.getBody();

        // MeterDocumentation, ObservationDocumentation
        methodSource = enumConstantBody.getMethod("getName");
        if (methodSource != null) {
            name = ParsingUtils.readStringReturnValue(methodSource);
        }

        // MeterDocumentation
        methodSource = enumConstantBody.getMethod("getKeyNames");
        if (methodSource != null) {
            lowCardinalityTags.addAll(retrieveEnumValues(myEnum, methodSource, KeyNameEnumConstantReader.INSTANCE));
        }

        // ObservationDocumentation(@Nullable)
        methodSource = enumConstantBody.getMethod("getDefaultConvention");
        if (methodSource != null) {
            // TODO: may share the logic with the span side
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
        methodSource = enumConstantBody.getMethod("getLowCardinalityKeyNames");
        if (methodSource != null) {
            lowCardinalityTags.addAll(retrieveEnumValues(myEnum, methodSource, KeyNameEnumConstantReader.INSTANCE));
        }

        // ObservationDocumentation
        methodSource = enumConstantBody.getMethod("getHighCardinalityKeyNames");
        if (methodSource != null) {
            highCardinalityTags.addAll(retrieveEnumValues(myEnum, methodSource, KeyNameEnumConstantReader.INSTANCE));
        }

        // MeterDocumentation, ObservationDocumentation
        methodSource = enumConstantBody.getMethod("getPrefix");
        if (methodSource != null) {
            prefix = ParsingUtils.readStringReturnValue(methodSource);
        }

        // MeterDocumentation
        methodSource = enumConstantBody.getMethod("getBaseUnit");
        if (methodSource != null) {
            baseUnit = ParsingUtils.readStringReturnValue(methodSource);
        }

        // MeterDocumentation
        methodSource = enumConstantBody.getMethod("getType");
        if (methodSource != null) {
            String value = ParsingUtils.readStringReturnValue(methodSource);
            Assert.hasText(value, "Failed to read getType() method on " + myEnum.getName());
            type = Meter.Type.valueOf(value);
        }

        // MeterDocumentation
        methodSource = enumConstantBody.getMethod("overridesDefaultMetricFrom");
        if (methodSource != null) {
            Expression expression = ParsingUtils.expressionFromReturnMethodDeclaration(methodSource);
            Assert.notNull(expression, "Failed to parse the expression from " + methodSource);
            overridesDefaultMetricFrom = this.searchHelper.searchReferencingEnumConstant(myEnum, expression);
        }

        // ObservationDocumentation
        methodSource = enumConstantBody.getMethod("getEvents");
        if (methodSource != null) {
            events.addAll((retrieveEnumValues(myEnum, methodSource, EventEntryForMetricEnumConstantReader.INSTANCE)));
        }


        // prepare view model objects

        final String newName = name;
        events.forEach(event -> event.setName(newName + "." + event.getName()));

        // if entry has overridesDefaultSpanFrom - read tags from that thing
        // if entry has overridesDefaultSpanFrom AND getKeyNames() - we pick only the latter
        // if entry has overridesDefaultSpanFrom AND getAdditionalKeyNames() - we pick both
        if (overridesDefaultMetricFrom != null && lowCardinalityTags.isEmpty()) {
            MethodSource<?> keyMethodSource = this.searchHelper.searchMethodSource(overridesDefaultMetricFrom.getBody(), "getLowCardinalityKeyNames");
            if (keyMethodSource != null) {
                JavaEnumSource enclosingEnumSource = overridesDefaultMetricFrom.getOrigin();
                List<KeyNameEntry> lows = retrieveEnumValues(enclosingEnumSource, methodSource, KeyNameEnumConstantReader.INSTANCE);
                lowCardinalityTags.addAll(lows);
            }
        }

        Collections.sort(lowCardinalityTags);
        Collections.sort(highCardinalityTags);
        Collections.sort(events);

        List<MetricInfo> metricInfos = new ArrayList<>();
        // create a metric info based on the above parsing result
        metricInfos.add(new MetricInfo(name, nameFromConventionClass, conventionClass, type, baseUnit));

        // DefaultMeterObservationHandler creates LongTaskTimer. Add the information.
        if (myEnum.hasInterface(ObservationDocumentation.class)) {
            String ltkMetricName = StringUtils.hasText(name) ? name + ".active" : name;
            String ltkMetricNameFromConvention = StringUtils.hasText(nameFromConventionClass) ? nameFromConventionClass + ".active" : nameFromConventionClass;
            metricInfos.add(new MetricInfo(ltkMetricName, ltkMetricNameFromConvention, conventionClass, Type.LONG_TASK_TIMER, ""));
        }

        return new MetricEntry(nameFromConventionClass, myEnum.getCanonicalName(), enumConstant.getName(), description, prefix, lowCardinalityTags,
                highCardinalityTags, events, metricInfos);
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
