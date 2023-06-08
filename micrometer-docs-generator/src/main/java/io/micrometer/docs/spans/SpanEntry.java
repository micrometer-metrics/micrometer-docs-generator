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

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import io.micrometer.docs.commons.EventEntry;
import io.micrometer.docs.commons.KeyNameEntry;
import io.micrometer.docs.commons.utils.Assert;
import io.micrometer.docs.commons.utils.StringUtils;

class SpanEntry implements Comparable<SpanEntry> {

    final String name;

    final String nameOrigin;

    final String enclosingClass;

    final String enumName;

    final String description;

    final String prefix;

    final List<KeyNameEntry> tagKeys;

    final List<EventEntry> events;

    SpanEntry(String name, String nameOrigin, String enclosingClass, String enumName, String description, String prefix,
            List<KeyNameEntry> tagKeys, List<EventEntry> events) {
        Assert.hasText(description, "Span javadoc description must not be empty");
        this.nameOrigin = nameOrigin;
        this.name = name;
        this.enclosingClass = enclosingClass;
        this.enumName = enumName;
        this.description = description;
        this.prefix = prefix;
        this.tagKeys = tagKeys;
        this.events = events;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        SpanEntry spanEntry = (SpanEntry) o;
        return Objects.equals(name, spanEntry.name) && Objects.equals(nameOrigin, spanEntry.nameOrigin)
                && Objects.equals(enclosingClass, spanEntry.enclosingClass)
                && Objects.equals(enumName, spanEntry.enumName) && Objects.equals(description, spanEntry.description)
                && Objects.equals(prefix, spanEntry.prefix) && Objects.equals(tagKeys, spanEntry.tagKeys)
                && Objects.equals(events, spanEntry.events);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, nameOrigin, enclosingClass, enumName, description, prefix, tagKeys, events);
    }

    @Override
    public int compareTo(SpanEntry o) {
        int compare = this.enumName.compareTo(o.enumName);
        if (compare != 0) {
            return compare;
        }
        return this.enclosingClass.compareTo(o.enclosingClass);
    }

    public String getSpanTitle() {
        // TODO: convert to handlebar helper
        String name = Arrays.stream(this.enumName.replace("_", " ").split(" "))
            .map(s -> StringUtils.capitalize(s.toLowerCase(Locale.ROOT)))
            .collect(Collectors.joining(" "));
        if (!name.toLowerCase(Locale.ROOT).endsWith("span")) {
            return name + " Span";
        }
        return name;
    }

    public String getDisplayName() {
        // TODO: convert to handlebar helper
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(this.name)) {
            sb.append("`");
            sb.append(this.name);
            sb.append("`");
            if (StringUtils.hasText(this.nameOrigin)) {
                sb.append(" (defined by convention class `");
                sb.append(this.nameOrigin);
                sb.append("`)");
            }
        }
        else {
            // TODO: existing logic. Consider failing in this case.
            sb.append("Unable to resolve the name - please check the convention class `");
            sb.append(this.nameOrigin);
            sb.append("` for more details");
        }
        return sb.toString();
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
