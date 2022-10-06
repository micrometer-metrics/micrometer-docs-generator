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
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.docs.MeterDocumentation;

enum MyDistributionSummary implements MeterDocumentation {

    /**
     * A test distribution.
     */
    TEST_DISTRIBUTION {
        @Override
        public String getName() {
            return "my distribution";
        }

        @Override
        public String getBaseUnit() {
            return "bytes";
        }

        @Override
        public Meter.Type getType() {
            return Meter.Type.DISTRIBUTION_SUMMARY;
        }

        @Override
        public KeyName[] getKeyNames() {
            return TestSpanTags.values();
        }

    };

    enum TestSpanTags implements KeyName {

        /**
         * Test bar.
         */
        BAR {
            @Override
            public String asString() {
                return "baaaar";
            }
        }

    }

}
