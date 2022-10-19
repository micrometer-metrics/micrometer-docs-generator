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
package io.micrometer.docs.spans.test5;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.docs.ObservationDocumentation;

enum ParentSample implements ObservationDocumentation {

    /**
     * Parent observation
     */
    PARENT {
        @Override
        public String getPrefix() {
            return "foo.bar";
        }

        @Override
        public String getName() {
            return "parent";
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return MyKeyNames.values();
        }

    };

    enum MyKeyNames implements KeyName {
        CORRECT {
            @Override
            public String asString() {
                return "foo.bar.correct";
            }
        },
        WRONG_FOO {
            @Override
            public String asString() {
                return "foo";
            }
        },
        WRONG_BAR {
            @Override
            public String asString() {
                return "bar";
            }
        }
    }

}
