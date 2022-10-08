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
package io.micrometer.docs.spans.conventions;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

enum AnnotationSpan implements ObservationDocumentation {

    /**
     * Observation that wraps annotations.
     */
    PUBLIC_CONVENTION {
        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return PublicObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return Tags.values();
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return Tags2.values();
        }

    },

    /**
     * Observation that wraps annotations.
     */
    NESTED_CONVENTION {
        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return NestedConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return Tags.values();
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return Tags2.values();
        }

    },

    /**
     * Observation that wraps annotations.
     */
    DYNAMIC_CONVENTION {
        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return DynamicObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return Tags.values();
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return Tags2.values();
        }

    },

    /**
     * Observation with interface that implements getName method.
     */
    CONCRETE_CONVENTION {
        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return UseInterfaceObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return Tags.values();
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return Tags2.values();
        }

    };

    static class NestedConvention implements ObservationConvention<Observation.Context> {

        @Override
        public String getName() {
            return "nested convention";
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return false;
        }
    }

    /**
     * Low cardinality tags.
     */
    enum Tags implements KeyName {

        /**
         * Class name where a method got annotated with a annotation.
         */
        CLASS {
            @Override
            public String asString() {
                return "class";
            }
        },

        /**
         * Method name that got annotated with annotation.
         */
        METHOD {
            @Override
            public String asString() {
                return "method";
            }
        }

    }

    /**
     * High cardinality tags.
     */
    enum Tags2 implements KeyName {

        /**
         * Class name where a method got annotated with a annotation.
         */
        CLASS2 {
            @Override
            public String asString() {
                return "class2";
            }
        },

        /**
         * Method name that got annotated with annotation.
         */
        METHOD2 {
            @Override
            public String asString() {
                return "method2";
            }
        }

    }

}
