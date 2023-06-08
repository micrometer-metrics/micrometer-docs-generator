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
package io.micrometer.docs.commons.templates;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;

/**
 * Helper source class for handlebars.
 *
 * @author Tadaya Tsuyukubo
 */
public class ADocHelpers {

    public static boolean isDynamic(String input) {
        return input.contains("%s");
    }

    /**
     * A helper class to generate unique anchor values. When this helper receives the same
     * value multiple times, it appends a suffix to the anchor value to make it unique.
     * The suffix takes the form of "-1", "-2", and so on, incrementing with each
     * occurrence.
     */
    public static class AnchorHelper implements Helper<String> {

        private final Map<String, Integer> map = new HashMap<>();

        @Override
        public Object apply(String context, Options options) throws IOException {
            int suffixNumber = this.map.compute(context, (key, number) -> (number == null) ? 0 : number + 1);
            // returns foo, foo-1, foo-2, ...
            return suffixNumber == 0 ? context : context + "-" + suffixNumber;
        }

    }

}
