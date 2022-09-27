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

package io.micrometer.docs.commons;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import org.jboss.forge.roaster.model.source.EnumConstantSource;

/**
 * Hold Key and Value type of information.
 * For example, it is used to hold the data of {@link KeyName}, {@code EventValue}, and {@link Observation.Event}.
 * Additionally, each entry can have map based extra attributes.
 */
public class KeyValueEntry implements Comparable<KeyValueEntry> {

    private final String name;

    private final String description;

    private final Map<String, String> extra = new HashMap<>();

    public KeyValueEntry(String name, String description, Map<String, String> extra) {
        this.name = name;
        this.description = description;
        this.extra.putAll(extra);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        KeyValueEntry that = (KeyValueEntry) o;
        return Objects.equals(name, that.name) && Objects.equals(description, that.description) && Objects.equals(extra, that.extra);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, extra);
    }

    @Override
    public int compareTo(KeyValueEntry o) {
        return name.compareTo(o.name);
    }

    @Override
    public String toString() {
        return "|`" + name + "`|" + description();
    }

    private String description() {
        String suffix = "";
        if (this.name.contains("%s")) {
            suffix = " (since the name contains `%s` the final value will be resolved at runtime)";
        }
        return description + suffix;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, String> getExtra() {
        return this.extra;
    }

    /**
     * Extract extra attribute information from the enum.
     */
    @FunctionalInterface
    public interface ExtraAttributesExtractor extends Function<EnumConstantSource, Map<String, String>> {
        ExtraAttributesExtractor EMPTY = (myEnum) -> Collections.emptyMap();
    }

}
