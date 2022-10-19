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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.docs.MeterDocumentation;
import io.micrometer.docs.commons.AbstractSearchingFileVisitor;
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
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Expression;
import org.jboss.forge.roaster.model.source.EnumConstantSource;
import org.jboss.forge.roaster.model.source.EnumConstantSource.Body;
import org.jboss.forge.roaster.model.source.JavaEnumSource;
import org.jboss.forge.roaster.model.source.JavaSource;
import org.jboss.forge.roaster.model.source.MethodSource;

class MetricSearchingFileVisitor extends AbstractSearchingFileVisitor {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(MetricSearchingFileVisitor.class);

    private final Collection<MetricEntry> entries;

    MetricSearchingFileVisitor(Pattern pattern, Collection<MetricEntry> entries, JavaSourceSearchHelper searchHelper) {
        super(pattern, searchHelper);
        this.entries = entries;
    }

    @Override
    public Collection<Class<?>> supportedInterfaces() {
        return Arrays.asList(MeterDocumentation.class, ObservationDocumentation.class);
    }

    @Override
    public void onEnumConstant(JavaEnumSource enclosingEnumSource, EnumConstantSource enumConstant) {
        MetricEntry entry = parseMetric(enumConstant, enclosingEnumSource);
        entries.add(entry);
        logger.debug("Found [" + entry.lowCardinalityKeyNames.size() + "]");
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        validatePrefixOnTags();
        return FileVisitResult.CONTINUE;
    }

    private void validatePrefixOnTags() {
        List<String> messages = new ArrayList<>();
        for (MetricEntry metricEntry : this.entries) {
            String prefix = metricEntry.getPrefix();
            if (!StringUtils.hasText(prefix)) {
                continue;
            }
            String enumName = metricEntry.getEnumName();
            String enclosingClassName = metricEntry.getEnclosingClass();

            List<KeyNameEntry> allTags = new ArrayList<>();
            allTags.addAll(metricEntry.getLowCardinalityKeyNames());
            allTags.addAll(metricEntry.getHighCardinalityKeyNames());

            messages.addAll(validatePrefixOnTags(prefix, allTags, enumName, enclosingClassName));
        }
        if (!messages.isEmpty()) {
            StringBuilder sb = new StringBuilder("The following documented objects do not have properly prefixed tag keys according to their prefix() method. Please align the tag keys.");
            sb.append(System.lineSeparator()).append(System.lineSeparator());
            sb.append(messages.stream().collect(Collectors.joining(System.lineSeparator())));
            sb.append(System.lineSeparator()).append(System.lineSeparator());
            throw new IllegalStateException(sb.toString());
        }
    }


    private MetricEntry parseMetric(EnumConstantSource enumConstant, JavaEnumSource myEnum) {
        boolean isObservationDoc = myEnum.hasInterface(ObservationDocumentation.class);

        String description = AsciidocUtils.javadocToAsciidoc(enumConstant.getJavaDoc());
        String prefix = "";
        String baseUnit = "";
        Meter.Type type = Meter.Type.TIMER;
        List<KeyNameEntry> lowCardinalityTags = new ArrayList<>();
        List<KeyNameEntry> highCardinalityTags = new ArrayList<>();
        EnumConstantSource overridesDefaultMetricFrom = null;
        List<EventEntry> events = new ArrayList<>();

        MethodSource<?> methodSource;
        Body enumConstantBody = enumConstant.getBody();

        // resolve name from "getName" and "getDefaultConvention"
        NameInfo nameInfo = resolveName(isObservationDoc, enumConstant, myEnum);

        // MeterDocumentation
        methodSource = enumConstantBody.getMethod("getKeyNames");
        if (methodSource != null) {
            lowCardinalityTags.addAll(retrieveEnumValues(myEnum, methodSource, KeyNameEnumConstantReader.INSTANCE));
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

        String name = nameInfo.getName();
        String nameOrigin = nameInfo.getNameOrigin();
        events.forEach(event -> event.setName(name + "." + event.getName()));

        // if entry has overridesDefaultSpanFrom - read tags from that thing
        // if entry has overridesDefaultSpanFrom AND getKeyNames() - we pick only the latter
        // if entry has overridesDefaultSpanFrom AND getAdditionalKeyNames() - we pick both
        if (overridesDefaultMetricFrom != null && lowCardinalityTags.isEmpty()) {
            MethodSource<?> keyMethodSource = this.searchHelper.searchMethodSource(overridesDefaultMetricFrom.getBody(), "getLowCardinalityKeyNames");
            if (keyMethodSource != null) {
                JavaEnumSource enclosingEnumSource = overridesDefaultMetricFrom.getOrigin();
                List<KeyNameEntry> lows = retrieveEnumValues(enclosingEnumSource, keyMethodSource, KeyNameEnumConstantReader.INSTANCE);
                lowCardinalityTags.addAll(lows);
            }
        }

        Collections.sort(lowCardinalityTags);
        Collections.sort(highCardinalityTags);
        Collections.sort(events);

        List<MetricInfo> metricInfos = new ArrayList<>();
        // create a metric info based on the above parsing result
        metricInfos.add(new MetricInfo(name, nameOrigin, type, baseUnit));

        // DefaultMeterObservationHandler creates LongTaskTimer. Add the information.
        if (myEnum.hasInterface(ObservationDocumentation.class)) {
            String ltkMetricName = name + ".active";
            metricInfos.add(new MetricInfo(ltkMetricName, nameOrigin, Type.LONG_TASK_TIMER, ""));
        }

        return new MetricEntry(myEnum.getCanonicalName(), enumConstant.getName(), description, prefix, lowCardinalityTags,
                highCardinalityTags, events, metricInfos);
    }

    private NameInfo resolveName(boolean isObservationDoc, EnumConstantSource enumConstant, JavaEnumSource enclosingEnum) {
        Body enumConstantBody = enumConstant.getBody();

        String name = "";
        MethodSource<?> methodSource;

        // getName - MeterDocumentation, ObservationDocumentation
        methodSource = enumConstantBody.getMethod("getName");
        if (methodSource != null) {
            name = ParsingUtils.readStringReturnValue(methodSource);
        }


        if (!isObservationDoc) {
            return new NameInfo(name, "");
        }
        // getDefaultConvention - ObservationDocumentation(@Nullable)
        // resolve name from ObservationDocumentation if applicable
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
