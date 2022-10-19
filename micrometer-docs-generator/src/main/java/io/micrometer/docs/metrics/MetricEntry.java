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

import java.util.List;
import java.util.Objects;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.docs.commons.EventEntry;
import io.micrometer.docs.commons.KeyNameEntry;
import io.micrometer.docs.commons.utils.Assert;
import io.micrometer.docs.commons.utils.StringUtils;

class MetricEntry implements Comparable<MetricEntry> {

    final String enclosingClass;

    final String enumName;

    final String description;

    final String prefix;

    final List<KeyNameEntry> lowCardinalityKeyNames;

    final List<KeyNameEntry> highCardinalityKeyNames;

    final List<EventEntry> events;

    final List<MetricInfo> metricInfos;

    MetricEntry(String enclosingClass, String enumName, String description, String prefix, List<KeyNameEntry> lowCardinalityKeyNames, List<KeyNameEntry> highCardinalityKeyNames, List<EventEntry> events, List<MetricInfo> metricInfos) {
        Assert.hasText(description, "Observation / Meter javadoc description must not be empty. Check <" + enclosingClass + "#" + enumName + ">");
        this.enclosingClass = enclosingClass;
        this.enumName = enumName;
        this.description = description;
        this.prefix = prefix;
        this.lowCardinalityKeyNames = lowCardinalityKeyNames;
        this.highCardinalityKeyNames = highCardinalityKeyNames;
        this.events = events;
        this.metricInfos = metricInfos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetricEntry that = (MetricEntry) o;
        return Objects.equals(enclosingClass, that.enclosingClass) && Objects.equals(enumName, that.enumName) && Objects.equals(description, that.description) && Objects.equals(prefix, that.prefix) && Objects.equals(lowCardinalityKeyNames, that.lowCardinalityKeyNames) && Objects.equals(highCardinalityKeyNames, that.highCardinalityKeyNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enclosingClass, enumName, description, prefix, lowCardinalityKeyNames, highCardinalityKeyNames);
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

        final String nameOrigin;

        final Meter.Type type;

        final String baseUnit;

        public MetricInfo(String name, String nameOrigin, Type type, String baseUnit) {
            this.name = name;
            this.nameOrigin = nameOrigin;
            this.type = type;
            this.baseUnit = baseUnit;
        }

        public String getName() {
            return this.name;
        }

        public Type getType() {
            return this.type;
        }

        public String getBaseUnit() {
            return this.baseUnit;
        }

        public String getMetricName() {
            // TODO: convert to handlebar helper
            StringBuilder sb = new StringBuilder();
            if (StringUtils.hasText(this.name)) {
                sb.append("`").append(this.name).append("`");
                if (StringUtils.hasText(this.nameOrigin)) {
                    sb.append(" (defined by convention class `").append(this.nameOrigin).append("`)");
                }
            }
            else {
                // TODO: existing logic. Consider failing in this case.
                sb.append("Unable to resolve the name - please check the convention class `");
                sb.append(this.nameOrigin).append("` for more details");
            }
            return sb.toString();
        }

    }
}
