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

import io.micrometer.observation.Observation;
import io.micrometer.observation.docs.DocumentedObservation;

enum EventObservation implements DocumentedObservation {

    /**
     * Events having observation.
     */
    EVENTS_HAVING_OBSERVATION {
        @Override
        public String getName() {
            return "foo";
        }

        @Override
        public String getContextualName() {
            return "foo span name";
        }

        @Override
        public Observation.Event[] getEvents() {
            return Events.values();
        }
    };

    /**
     * Observation events.
     */
    enum Events implements Observation.Event {

        /**
         * Start event.
         */
        START {
            @Override
            public String getName() {
                return "start";
            }

            @Override
            public String getContextualName() {
                return "start annotation";
            }
        },

        /**
         * Stop event.
         */
        STOP {
            @Override
            public String getName() {
                return "stop";
            }

            @Override
            public String getContextualName() {
                return "stop %s %s foo";
            }
        }

    }

}
