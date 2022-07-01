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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.Meter;
import io.micrometer.docs.commons.KeyValueEntry;
import io.micrometer.docs.commons.utils.Assert;
import io.micrometer.docs.commons.utils.StringUtils;
import io.micrometer.observation.Observation;

class MetricEntry implements Comparable<MetricEntry> {

    final String name;

    final Class<? extends Observation.ObservationConvention<?>> conventionClass;

    final String nameFromConventionClass;

    final String enclosingClass;

    final String enumName;

    final String description;

    final String prefix;

    final String baseUnit;

    final Meter.Type type;

    final Collection<KeyValueEntry> lowCardinalityKeyNames;

    final Collection<KeyValueEntry> highCardinalityKeyNames;

    final Map.Entry<String, String> overridesDefaultMetricFrom;

    MetricEntry(String name, Class<? extends Observation.ObservationConvention<?>> conventionClass, String nameFromConventionClass, String enclosingClass, String enumName, String description, String prefix, String baseUnit, Meter.Type meterType, Collection<KeyValueEntry> lowCardinalityKeyNames, Collection<KeyValueEntry> highCardinalityKeyNames, Map.Entry<String, String> overridesDefaultMetricFrom) {
        Assert.hasText(name, "Observation / Meter name must not be empty. Check <" + enclosingClass + "#" + enumName + ">");
        Assert.hasText(description, "Observation / Meter description must not be empty. Check <" + enclosingClass + "#" + enumName + ">");
        this.name = name;
        this.conventionClass = conventionClass;
        this.nameFromConventionClass = nameFromConventionClass;
        this.enclosingClass = enclosingClass;
        this.enumName = enumName;
        this.description = description;
        this.prefix = prefix;
        this.baseUnit = StringUtils.hasText(baseUnit) ? baseUnit : meterType == Meter.Type.TIMER ? "seconds" : "";
        this.type = meterType;
        this.lowCardinalityKeyNames = lowCardinalityKeyNames;
        this.highCardinalityKeyNames = highCardinalityKeyNames;
        this.overridesDefaultMetricFrom = overridesDefaultMetricFrom;
        if (this.name != null && this.conventionClass != null) {
            throw new IllegalStateException("You can't declare both [getName()] and [getDefaultConvention()] methods at the same time, you have to chose only one. Problem occurred in [" + this.enclosingClass + "] class");
        } else if (this.name == null && this.conventionClass == null) {
            throw new IllegalStateException("You have to set either [getName()] or [getDefaultConvention()] methods. In case of [" + this.enclosingClass + "] you haven't defined any");
        }
    }

    static void assertThatProperlyPrefixed(Collection<MetricEntry> entries) {
        List<Map.Entry<MetricEntry, List<String>>> collect = entries.stream().map(MetricEntry::notProperlyPrefixedTags).filter(Objects::nonNull).collect(Collectors.toList());
        if (collect.isEmpty()) {
            return;
        }
        throw new IllegalStateException("The following documented objects do not have properly prefixed tag keys according to their prefix() method. Please align the tag keys.\n\n" + collect.stream()
                .map(e -> "\tName <" + e.getKey().enumName + "> in class <" + e.getKey().enclosingClass + "> has the following prefix <" + e.getKey().prefix + "> and following invalid tag keys " + e.getValue())
                .collect(Collectors.joining("\n")) + "\n\n");
    }

    Map.Entry<MetricEntry, List<String>> notProperlyPrefixedTags() {
        if (!StringUtils.hasText(this.prefix)) {
            return null;
        }
        List<KeyValueEntry> allTags = new ArrayList<>(this.lowCardinalityKeyNames);
        allTags.addAll(this.highCardinalityKeyNames);
        List<String> collect = allTags.stream().map(KeyValueEntry::getName).filter(eName -> !eName.startsWith(this.prefix)).collect(Collectors.toList());
        if (collect.isEmpty()) {
            return null;
        }
        return new AbstractMap.SimpleEntry<>(this, collect);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MetricEntry that = (MetricEntry) o;
        return Objects.equals(name, that.name) && Objects.equals(enclosingClass, that.enclosingClass) && Objects.equals(enumName, that.enumName) && Objects.equals(description, that.description) && Objects.equals(prefix, that.prefix) && Objects.equals(baseUnit, that.baseUnit) && type == that.type && Objects.equals(lowCardinalityKeyNames, that.lowCardinalityKeyNames) && Objects.equals(highCardinalityKeyNames, that.highCardinalityKeyNames) && Objects.equals(overridesDefaultMetricFrom, that.overridesDefaultMetricFrom);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, enclosingClass, enumName, description, prefix, baseUnit, type, lowCardinalityKeyNames, highCardinalityKeyNames, overridesDefaultMetricFrom);
    }

    @Override
    public int compareTo(MetricEntry o) {
        return enumName.compareTo(o.enumName);
    }

    @Override
    public String toString() {
        String displayName = Arrays.stream(enumName.replace("_", " ").split(" "))
                .map(s -> StringUtils.capitalize(s.toLowerCase(Locale.ROOT))).collect(Collectors.joining(" "));
        StringBuilder text = new StringBuilder()
                .append("[[observability-metrics-").append(displayName.toLowerCase(Locale.ROOT).replace(" ", "-")).append("]]\n")
                .append("==== ")
                .append(displayName)
                .append("\n\n> ").append(description).append("\n\n")
                .append("**Metric name** `").append(this.name).append("`");
        if (this.name.contains("%s")) {
            text.append(" - since it contains `%s`, the name is dynamic and will be resolved at runtime.");
        }
        else {
            text.append(".");
        }
        text.append(" **Type** `").append(type.toString().toLowerCase(Locale.ROOT).replace("_", " "));
        if (StringUtils.hasText(baseUnit)) {
            text.append("` and **base unit** `").append(baseUnit.toLowerCase(Locale.ROOT));
        }
        text.append("`.").append("\n\n").append("Fully qualified name of the enclosing class `").append(this.enclosingClass).append("`.");
        if (StringUtils.hasText(prefix)) {
            text.append("\n\nIMPORTANT: All tags must be prefixed with `").append(this.prefix).append("` prefix!");
        }
        if (!lowCardinalityKeyNames.isEmpty()) {
            text.append("\n\n.Low cardinality Keys\n|===\n|Name | Description\n")
                    .append(this.lowCardinalityKeyNames.stream().map(KeyValueEntry::toString).collect(Collectors.joining("\n")))
                    .append("\n|===");
        }
        if (!highCardinalityKeyNames.isEmpty()) {
            text.append("\n\n.High cardinality Keys\n|===\n|Name | Description\n")
                    .append(this.highCardinalityKeyNames.stream().map(KeyValueEntry::toString).collect(Collectors.joining("\n")))
                    .append("\n|===");
        }
        return text.toString();
    }

}
