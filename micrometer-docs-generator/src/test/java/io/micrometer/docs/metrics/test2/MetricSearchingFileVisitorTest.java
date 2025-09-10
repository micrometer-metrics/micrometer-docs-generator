/**
 * Copyright 2025 the original author or authors.
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
package io.micrometer.docs.metrics.test2;

import io.micrometer.docs.metrics.MetricsDocGenerator;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

class MetricSearchingFileVisitorTest {

    @Test
    void should_render_metrics_from_sub_class_interfaces() throws IOException {
        Path output = Paths.get(".", "build", "_metrics.adoc");

        File sourceRoot = new File(".", "src/test");
        new MetricsDocGenerator(sourceRoot, Pattern.compile(".*/docs/metrics/test2/[a-zA-Z]+\\.java"),
                "templates/metrics.adoc.hbs", output)
            .generate();

        BDDAssertions.then(new String(Files.readAllBytes(output)))
            .contains("**Metric name** `extended.foo`. **Type** `counter`")
            .contains("**Metric name** `extend.extended.foo`. **Type** `counter`");
    }

}
