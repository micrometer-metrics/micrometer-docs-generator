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
package io.micrometer.docs.metrics.test1;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import io.micrometer.docs.metrics.MetricsDocGenerator;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

class SameNameEnumMeterTests {

    @Test
    void should_detect_same_name_meter_documentations() throws IOException {
        File root = new File("./src/test/java/io/micrometer/docs/metrics/test1");
        Path output = Paths.get(".", "build/metrics/test1", "_metrics.adoc");
        Files.createDirectories(output.getParent());

        new MetricsDocGenerator(root, Pattern.compile(".*"), "templates/metrics.adoc.hbs", output).generate();

        // check prefix
        // @formatter:off
        BDDAssertions.then(new String(Files.readAllBytes(output)))
                .contains("foo-top")
                .contains("foo-meter-top")
                .contains("foo1")
                .contains("foo-meter-1")
                .contains("foo2")
                .contains("foo-meter-2")
                .contains("foo-enum")
                .contains("foo-meter-enum")
                .contains("[[observability-metrics-foo]]")  // anchors
                .contains("[[observability-metrics-foo-1]]")
                .contains("[[observability-metrics-foo-2]]")
                .contains("[[observability-metrics-foo-3]]")
                .contains("[[observability-metrics-foo-4]]")
                .contains("[[observability-metrics-foo-5]]")
                .contains("[[observability-metrics-foo-6]]")
                .contains("[[observability-metrics-foo-7]]")
        ;
        // @formatter:on
    }

}
