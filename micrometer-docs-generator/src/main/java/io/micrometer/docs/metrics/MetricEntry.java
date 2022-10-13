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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.docs.commons.EventEntry;
import io.micrometer.docs.commons.KeyNameEntry;
import io.micrometer.docs.commons.utils.Assert;
import io.micrometer.docs.commons.utils.StringUtils;

class MetricEntry implements Comparable<MetricEntry> {

    final String nameFromConventionClass;

    final String enclosingClass;

    final String enumName;

    final String description;

    final String prefix;

    final List<KeyNameEntry> lowCardinalityKeyNames;

    final List<KeyNameEntry> highCardinalityKeyNames;

    @Nullable
    final Map.Entry<String, String> overridesDefaultMetricFrom;

    final List<EventEntry> events;

    final List<MetricInfo> metricInfos;

    MetricEntry(String nameFromConventionClass, String enclosingClass, String enumName, String description, String prefix, List<KeyNameEntry> lowCardinalityKeyNames, List<KeyNameEntry> highCardinalityKeyNames, @Nullable Map.Entry<String, String> overridesDefaultMetricFrom, List<EventEntry> events, List<MetricInfo> metricInfos) {
        Assert.hasText(description, "Observation / Meter javadoc description must not be empty. Check <" + enclosingClass + "#" + enumName + ">");
        this.nameFromConventionClass = nameFromConventionClass;
        this.enclosingClass = enclosingClass;
        this.enumName = enumName;
        this.description = description;
        this.prefix = prefix;
        this.lowCardinalityKeyNames = lowCardinalityKeyNames;
        this.highCardinalityKeyNames = highCardinalityKeyNames;
        this.overridesDefaultMetricFrom = overridesDefaultMetricFrom;
        this.events = events;
        this.metricInfos = metricInfos;

        // TODO: move out this check from this class
        for (MetricInfo metricInfo : metricInfos) {
            if (StringUtils.hasText(metricInfo.name) && metricInfo.conventionClass != null) {
                throw new IllegalStateException("You can't declare both [getName()] and [getDefaultConvention()] methods at the same time, you have to chose only one. Problem occurred in [" + this.enclosingClass + "] class");
            }
            else if (metricInfo.name == null && metricInfo.conventionClass == null) {
                throw new IllegalStateException("You have to set either [getName()] or [getDefaultConvention()] methods. In case of [" + this.enclosingClass + "] you haven't defined any");
            }
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
        List<KeyNameEntry> allTags = new ArrayList<>(this.lowCardinalityKeyNames);
        allTags.addAll(this.highCardinalityKeyNames);
        List<String> collect = allTags.stream().map(KeyNameEntry::getName).filter(eName -> !eName.startsWith(this.prefix)).collect(Collectors.toList());
        if (collect.isEmpty()) {
            return null;
        }
        return new AbstractMap.SimpleEntry<>(this, collect);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetricEntry that = (MetricEntry) o;
        return Objects.equals(nameFromConventionClass, that.nameFromConventionClass) && Objects.equals(enclosingClass, that.enclosingClass) && Objects.equals(enumName, that.enumName) && Objects.equals(description, that.description) && Objects.equals(prefix, that.prefix) && Objects.equals(lowCardinalityKeyNames, that.lowCardinalityKeyNames) && Objects.equals(highCardinalityKeyNames, that.highCardinalityKeyNames) && Objects.equals(overridesDefaultMetricFrom, that.overridesDefaultMetricFrom);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nameFromConventionClass, enclosingClass, enumName, description, prefix, lowCardinalityKeyNames, highCardinalityKeyNames, overridesDefaultMetricFrom);
    }

    @Override
    public int compareTo(MetricEntry o) {
        return enumName.compareTo(o.enumName);
    }

    public String getDescription() {
        return this.description;
    }

    public String getEnumName() {
        return this.enumName;
    }

    public String getEnclosingClass() {
        return this.enclosingClass;
    }

    public String getPrefix() {
        return this.prefix;
    }


    public List<KeyNameEntry> getLowCardinalityKeyNames() {
        return this.lowCardinalityKeyNames;
    }

    public List<KeyNameEntry> getHighCardinalityKeyNames() {
        return this.highCardinalityKeyNames;
    }

    public List<EventEntry> getEvents() {
        return this.events;
    }

    public List<MetricInfo> getMetricInfos() {
        return this.metricInfos;
    }

    // Handlebars require the method name to be "isX" or "getX". see JavaBeanValueResolver
    // TODO: this should be handled by template by checking nested types satisfies the condition.
    // If there is no native way to do it, may need to have a helper function.
    public boolean isTimerMetric() {
        return this.metricInfos.stream().anyMatch(info -> Type.TIMER.equals(info.getType()) || Type.LONG_TASK_TIMER.equals(info.getType()));
    }

    public static class MetricInfo {
        final String name;

        final String nameFromConventionClass;

        final String conventionClass;

        final Meter.Type type;

        final String baseUnit;

        public MetricInfo(String name, String nameFromConventionClass, String conventionClass, Type type, String baseUnit) {
            this.name = name;
            this.nameFromConventionClass = nameFromConventionClass;
            this.conventionClass = conventionClass;
            this.type = type;
            this.baseUnit = baseUnit;
        }

        public String getName() {
            return this.name;
        }

        public String getNameFromConventionClass() {
            return this.nameFromConventionClass;
        }

        public String getConventionClass() {
            return this.conventionClass;
        }

        public Type getType() {
            return this.type;
        }

        public String getBaseUnit() {
            return this.baseUnit;
        }

        public String getMetricName() {
            // TODO: convert to handlebar helper
            if (StringUtils.hasText(this.name)) {
                return "`" + this.name + "`";
            }
            else if (StringUtils.hasText(this.nameFromConventionClass)) {
                return "`" + this.nameFromConventionClass + "` (defined by convention class `" + this.conventionClass + "`)";
            }
            return "Unable to resolve the name - please check the convention class `" + this.conventionClass + "` for more details";
        }

    }
}
