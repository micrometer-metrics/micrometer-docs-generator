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
 * Write out {@link KeyName} entries.
 *
 * @author Tadaya Tsuyukubo
 */
public class KeyNameWriter {

    public static final KeyNameWriter INSTANCE = new KeyNameWriter();

    public String write(KeyValueEntry entry) {
        // since "isRequired" in "KeyName" has default "true", when user does not
        // override the method in enum, set default to "true".
        String value = entry.getExtra().get("isRequired");
        boolean isRequired = value == null || Boolean.parseBoolean(value);

        String name = "`" + entry.getName() + "`" + (isRequired ? " _(* Required)_" : "");

        String description = entry.getDescription();
        if (entry.getName().contains("%s")) {
            description = " (since the name contains `%s` the final value will be resolved at runtime)";
        }
        return "|" + name + "|" + description;
    }

}
