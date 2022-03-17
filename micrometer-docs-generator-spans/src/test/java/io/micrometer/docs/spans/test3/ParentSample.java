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

package io.micrometer.docs.spans.test3;

import io.micrometer.observation.docs.DocumentedObservation;
import io.micrometer.observation.docs.TagKey;

enum ParentSample implements DocumentedObservation {

    /**
     * Observation that wraps annotations.
     */
    PARENT {
        @Override
        public String getName() {
            return "%s";
        }

        @Override
        public TagKey[] getLowCardinalityTagKeys() {
            return Tags.values();
        }

        @Override
        public TagKey[] getHighCardinalityTagKeys() {
            return Tags2.values();
        }

    };

    /**
     * Low cardinality tags.
     */
    enum Tags implements TagKey {

        /**
         * Class name where a method got annotated with a annotation.
         */
        CLASS {
            @Override
            public String getKey() {
                return "class";
            }
        },

        /**
         * Method name that got annotated with annotation.
         */
        METHOD {
            @Override
            public String getKey() {
                return "method";
            }
        }

    }

    /**
     * High cardinality tags.
     */
    enum Tags2 implements TagKey {

        /**
         * Class name where a method got annotated with a annotation.
         */
        CLASS2 {
            @Override
            public String getKey() {
                return "class2";
            }
        },

        /**
         * Method name that got annotated with annotation.
         */
        METHOD2 {
            @Override
            public String getKey() {
                return "method2";
            }
        }

    }

}
