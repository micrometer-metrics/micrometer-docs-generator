/*
 * Copyright 2023 the original author or authors.
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
package io.micrometer.docs.metrics.test1.two;

import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.docs.MeterDocumentation;

enum SameNameMeterDoc implements MeterDocumentation {

    /**
     * Same name and enum value in two by meter doc.
     */
    FOO {
        @Override
        public String getPrefix() {
            return "foo-meter-2";
        }

        @Override
        public String getName() {
            return "foo-meter in two";
        }

        @Override
        public Type getType() {
            return Type.COUNTER;
        }

    };

}
