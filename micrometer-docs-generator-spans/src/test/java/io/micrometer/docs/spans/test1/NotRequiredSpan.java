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

package io.micrometer.docs.spans.test1;

import io.micrometer.common.docs.KeyName;
import io.micrometer.tracing.docs.SpanDocumentation;

public enum NotRequiredSpan implements SpanDocumentation {

    /**
     * A span with non-required key name.
     */
    NOT_REQUIRED {
        @Override
        public String getName() {
            return "NotRequired";
        }

        @Override
        public KeyName[] getKeyNames() {
            return KeyNames.values();
        }
    };

    enum KeyNames implements KeyName {

        /**
         * Optional key.
         */
        OPTIONAL_KEY {
            @Override
            public String asString() {
                return "optional";
            }

            @Override
            public boolean isRequired() {
                return false;
            }
        },
        /**
         * Mandatory key.
         */
        MANDATORY_KEY {
            @Override
            public String asString() {
                return "mandatory";
            }

            @Override
            public boolean isRequired() {
                return true;  // explicitly specify true
            }
        }

    }

}
