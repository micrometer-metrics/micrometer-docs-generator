/*
 * Copyright 2013-2021 the original author or authors.
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


import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.docs.DocumentedObservation;

enum AsyncObservation implements DocumentedObservation {

    /**
     * Observation that wraps a @Async annotation.
     */
    ASYNC_ANNOTATION {
        @Override
        public String getName() {
            return "%s";
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return AsyncSpanTags.values();
        }

    },

    /**
     * FOO.
     */
    TEST {
        @Override
        public String getName() {
            return "fixed";
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return KeyName.merge(TestSpanTags.values(), AsyncSpanTags.values());
        }

    };

    enum AsyncSpanTags implements KeyName {

        /**
         * Class name where a method got annotated with @Async.
         */
        CLASS {
            @Override
            public String getKeyName() {
                return "class";
            }
        },

        /**
         * Method name that got annotated with @Async.
         */
        METHOD {
            @Override
            public String getKeyName() {
                return "method";
            }
        }

    }

    enum TestSpanTags implements KeyName {

        /**
         * Test foo
         */
        FOO {
            @Override
            public String getKeyName() {
                return "foooooo";
            }
        }

    }

}
