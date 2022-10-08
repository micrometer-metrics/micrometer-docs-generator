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
package io.micrometer.docs.commons;

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.docs.commons.utils.StringUtils;

public class ObservationConventionEntry implements Comparable<ObservationConventionEntry> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ObservationConventionEntry.class);

    private final String className;

    private final Type type;

    private final String contextClassName;

    public ObservationConventionEntry(String className, Type type, String contextClassName) {
        this.className = className;
        this.type = type;
        this.contextClassName = StringUtils.hasText(contextClassName) ? contextClassName : "Unable to resolve";
    }

    public String getClassName() {
        return className;
    }

    public String getContextClassName() {
        return contextClassName;
    }

    public Type getType() {
        return this.type;
    }

    @Override
    public int compareTo(ObservationConventionEntry o) {
        int compare = this.contextClassName.compareTo(o.contextClassName);
        if (compare != 0) {
            return compare;
        }
        compare = this.type.compareTo(o.type);
        if (compare != 0) {
            return compare;
        }
        if (this.className != null) {
            return this.className.compareTo(o.className);
        }
        return compare;
    }

    public enum Type {
        GLOBAL, LOCAL
    }

}
