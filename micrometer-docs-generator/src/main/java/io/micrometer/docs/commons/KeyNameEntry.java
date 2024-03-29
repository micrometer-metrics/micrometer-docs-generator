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

package io.micrometer.docs.commons;

import io.micrometer.common.docs.KeyName;

/**
 * Model object for {@link KeyName}.
 *
 * @author Tadaya Tsuyukubo
 */
public class KeyNameEntry implements Comparable<KeyNameEntry> {

    private String name;

    private String description;

    private boolean required;

    public String getDisplayDescription() {
        // TODO: use handlebar helper to compose the description
        String suffix = "";
        if (this.name.contains("%s")) {
            suffix = " (since the name contains `%s` the final value will be resolved at runtime)";
        }
        return this.description + suffix;
    }

    @Override
    public int compareTo(KeyNameEntry entry) {
        return this.name.compareTo(entry.name);
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isRequired() {
        return this.required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

}
