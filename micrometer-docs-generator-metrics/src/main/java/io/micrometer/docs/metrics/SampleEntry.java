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

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

class SampleEntry implements Comparable<SampleEntry> {

    final String name;

    final String enclosingClass;

    final String enumName;

    final String description;

    final String prefix;

    final Collection<KeyValueEntry> lowCardinalityTagKeys;

    final Collection<KeyValueEntry> highCardinalityTagKeys;

    SampleEntry(String name, String enclosingClass, String enumName, String description, String prefix, Collection<KeyValueEntry> lowCardinalityTagKeys, Collection<KeyValueEntry> highCardinalityTagKeys) {
        Assert.hasText(name, "Span name must not be empty");
        Assert.hasText(description, "Span description must not be empty");
        this.name = name;
        this.enclosingClass = enclosingClass;
        this.enumName = enumName;
        this.description = description;
        this.prefix = prefix;
        this.lowCardinalityTagKeys = lowCardinalityTagKeys;
        this.highCardinalityTagKeys = highCardinalityTagKeys;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SampleEntry that = (SampleEntry) o;
        return Objects.equals(name, that.name) && Objects.equals(enclosingClass, that.enclosingClass) && Objects.equals(enumName, that.enumName) && Objects.equals(description, that.description) && Objects.equals(prefix, that.prefix) && Objects.equals(lowCardinalityTagKeys, that.lowCardinalityTagKeys) && Objects.equals(highCardinalityTagKeys, that.highCardinalityTagKeys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, enclosingClass, enumName, description, lowCardinalityTagKeys, highCardinalityTagKeys);
    }

    @Override
    public int compareTo(SampleEntry o) {
        return enumName.compareTo(o.enumName);
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder()
                .append("=== ").append(Arrays.stream(enumName.replace("_", " ").split(" "))
                        .map(s -> StringUtils.capitalize(s.toLowerCase(Locale.ROOT))).collect(Collectors.joining(" ")))
                .append("\n\n> ").append(description).append("\n\n")
                .append("**Sample name** `").append(name).append("`");
        if (name.contains("%s")) {
            text.append(" - since it contains `%s`, the name is dynamic and will be resolved at runtime.");
        }
        else {
            text.append(".");
        }
        text.append("\n\n").append("Fully qualified name of the enclosing class `").append(this.enclosingClass).append("`");
        if (StringUtils.hasText(prefix)) {
            text.append("\n\nIMPORTANT: All tags must be prefixed with `").append(this.prefix).append("` prefix!");
        }
        if (!lowCardinalityTagKeys.isEmpty()) {
            text.append("\n\n.Low cardinality Keys\n|===\n|Name | Description\n")
                    .append(this.lowCardinalityTagKeys.stream().map(KeyValueEntry::toString).collect(Collectors.joining("\n")))
                    .append("\n|===");
        }
        if (!highCardinalityTagKeys.isEmpty()) {
            text.append("\n\n.High cardinality Keys\n|===\n|Name | Description\n")
                    .append(this.highCardinalityTagKeys.stream().map(KeyValueEntry::toString).collect(Collectors.joining("\n")))
                    .append("\n|===");
        }
        return text.toString();
    }

}
