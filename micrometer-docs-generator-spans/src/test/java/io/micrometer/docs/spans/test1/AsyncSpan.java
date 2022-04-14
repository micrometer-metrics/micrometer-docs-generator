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

package io.micrometer.docs.spans.test1;


import io.micrometer.common.docs.KeyName;
import io.micrometer.tracing.docs.DocumentedSpan;

enum AsyncSpan implements DocumentedSpan {

    /**
     * Span that wraps a @Async annotation. Either continues an existing one or creates a
     * new one if there was no present one.
     */
    ASYNC_ANNOTATION_SPAN {
        @Override
        public String getName() {
            return "%s";
        }

        @Override
        public KeyName[] getKeyNames() {
            return AsyncSpanTags.values();
        }

    },

    /**
     * Test span.
     */
    TEST_SPAN {
        @Override
        public String getName() {
            return "fixed";
        }

        @Override
        public KeyName[] getKeyNames() {
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
