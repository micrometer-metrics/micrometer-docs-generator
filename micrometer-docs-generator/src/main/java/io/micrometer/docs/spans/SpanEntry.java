/**
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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import io.micrometer.docs.commons.EventEntry;
import io.micrometer.docs.commons.KeyNameEntry;
import io.micrometer.docs.commons.utils.Assert;
import io.micrometer.docs.commons.utils.StringUtils;

class SpanEntry implements Comparable<SpanEntry> {

    final String name;

    final String conventionClass;

    final String nameFromConventionClass;

    final String enclosingClass;

    final String enumName;

    final String description;

    final String prefix;

    final List<KeyNameEntry> tagKeys;

    final List<KeyNameEntry> additionalKeyNames;

    final List<EventEntry> events;

    SpanEntry(String name, String conventionClass, String nameFromConventionClass, String enclosingClass, String enumName, String description, String prefix,
            List<KeyNameEntry> tagKeys, List<KeyNameEntry> additionalKeyNames, List<EventEntry> events) {
        Assert.hasText(description, "Span javadoc description must not be empty");
        this.conventionClass = conventionClass;
        this.nameFromConventionClass = nameFromConventionClass;
        this.name = name;
        this.enclosingClass = enclosingClass;
        this.enumName = enumName;
        this.description = description;
        this.prefix = prefix;
        this.tagKeys = tagKeys;
        this.additionalKeyNames = additionalKeyNames;
        this.events = events;
        if (StringUtils.hasText(this.name) && this.conventionClass != null) {
            throw new IllegalStateException("You can't declare both [getName()] and [getDefaultConvention()] methods at the same time, you have to chose only one. Problem occurred in [" + this.enclosingClass + "] class");
        } else if (this.name == null && this.conventionClass == null) {
            throw new IllegalStateException("You have to set either [getName()] or [getDefaultConvention()] methods. In case of [" + this.enclosingClass + "] you haven't defined any");
        }
    }

    static void assertThatProperlyPrefixed(Collection<SpanEntry> entries) {
        List<Map.Entry<SpanEntry, List<String>>> collect = entries.stream().map(SpanEntry::notProperlyPrefixedTags).filter(Objects::nonNull).collect(Collectors.toList());
        if (collect.isEmpty()) {
            return;
        }
        throw new IllegalStateException("The following documented objects do not have properly prefixed tag keys according to their prefix() method. Please align the tag keys.\n\n" + collect.stream().map(e -> "\tName <" + e.getKey().enumName + "> in class <" + e.getKey().enclosingClass + "> has the following prefix <" + e.getKey().prefix + "> and following invalid tag keys " + e.getValue()).collect(Collectors.joining("\n")) + "\n\n");
    }

    Map.Entry<SpanEntry, List<String>> notProperlyPrefixedTags() {
        if (!StringUtils.hasText(this.prefix)) {
            return null;
        }
        List<KeyNameEntry> allTags = new ArrayList<>(this.tagKeys);
        allTags.addAll(this.additionalKeyNames);
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
        SpanEntry spanEntry = (SpanEntry) o;
        return Objects.equals(name, spanEntry.name) && Objects.equals(conventionClass, spanEntry.conventionClass) && Objects.equals(nameFromConventionClass, spanEntry.nameFromConventionClass) && Objects.equals(enclosingClass, spanEntry.enclosingClass) && Objects.equals(enumName, spanEntry.enumName) && Objects.equals(description, spanEntry.description) && Objects.equals(prefix, spanEntry.prefix) && Objects.equals(tagKeys, spanEntry.tagKeys) && Objects.equals(additionalKeyNames, spanEntry.additionalKeyNames) && Objects.equals(events, spanEntry.events);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, conventionClass, nameFromConventionClass, enclosingClass, enumName, description, prefix, tagKeys, additionalKeyNames, events);
    }

    @Override
    public int compareTo(SpanEntry o) {
        return enumName.compareTo(o.enumName);
    }

    private String spanName() {
        String name = Arrays.stream(enumName.replace("_", " ").split(" ")).map(s -> StringUtils.capitalize(s.toLowerCase(Locale.ROOT))).collect(Collectors.joining(" "));
        if (!name.toLowerCase(Locale.ROOT).endsWith("span")) {
            return name + " Span";
        }
        return name;
    }

    private String name() {
        if (StringUtils.hasText(this.name)) {
            return "`" + this.name + "`";
        } else if (StringUtils.hasText(this.nameFromConventionClass)) {
            return "`" + this.nameFromConventionClass + "` (defined by convention class `" + this.conventionClass + "`)";
        }
        return "Unable to resolve the name - please check the convention class `" + this.conventionClass + "` for more details";
    }

    public String getSpanTitle() {
        // TODO: convert to handlebar helper
        return spanName();
    }
    public String getDisplayName() {
        return name();
    }

    public String getName() {
        return this.name;
    }

    public String getEnumName() {
        return this.enumName;
    }

    public String getDescription() {
        return this.description;
    }

    public String getEnclosingClass() {
        return this.enclosingClass;
    }

    public String getPrefix() {
        return this.prefix;
    }

    public List<KeyNameEntry> getTagKeys() {
        return this.tagKeys;
    }

    public List<EventEntry> getEvents() {
        return this.events;
    }
}
