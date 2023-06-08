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
package io.micrometer.docs.metrics;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

class DocsFromSourcesTests {

    @Test
    void should_build_a_table_out_of_enum_tag_key() throws IOException {
        Path output = Paths.get(".", "build", "_metrics.adoc");

        // FIXME consider isolating classes relevant to this test into their own package
        // and use that as source root
        // for now only consider the java classes at the root of package
        // io.micrometer.docs.metrics
        File sourceRoot = new File(".", "src/test");
        new MetricsDocGenerator(sourceRoot, Pattern.compile(".*/docs/metrics/[a-zA-Z]+\\.java"),
                "templates/metrics.adoc.hbs", output)
            .generate();

        // @formatter:off
        BDDAssertions.then(new String(Files.readAllBytes(output)))
                .contains("==== Async Annotation")
                .contains("____" + System.lineSeparator() + "Observation that wraps a")
                .contains("**Metric name** `%s` - since")
                .contains("Fully qualified name of")
                .contains("|`class` _(required)_|Class name where a method got annotated with @Async.")
                .contains("|`class2`|Class name where a method got annotated.")
                .contains("==== Annotation New Or Continue")
                .contains("**Metric name** `my distribution`. **Type** `distribution summary` and **base unit** `bytes`")
                .contains("baaaar")
                .contains("**Metric name** `my other distribution`. **Type** `distribution summary`.")
                .contains("**Metric name** `name.from.convention` (defined by convention class `io.micrometer.docs.metrics.AsyncObservation$MyConvention`).")
                .contains("**Metric name** `name.from.convention.active` (defined by convention class `io.micrometer.docs.metrics.AsyncObservation$MyConvention`).")
                .contains("**Metric name** `foo` (defined by convention class `io.micrometer.docs.metrics.PublicObservationConvention`)")
                .contains("**Metric name** `foo.active` (defined by convention class `io.micrometer.docs.metrics.PublicObservationConvention`)")
                .contains("**Metric name** Unable to resolve the name - please check the convention class `io.micrometer.docs.metrics.AsyncObservation$MyDynamicConvention`")
                .contains("Since, events were set on this documented entry, they will be converted to the following counters.")
                .contains("[[observability-metrics-events-having-observation-foo-start]]")
                .contains("===== Events Having Observation - foo start")
                .contains("> Start event.")
                .contains("**Metric name** `foo.start`. **Type** `counter`.")
                .contains("[[observability-metrics-events-having-observation-foo-stop]]")
                .contains("===== Events Having Observation - foo stop")
                .contains("> Stop event.")
                .contains("**Metric name** `foo.stop`. **Type** `counter`.")
                .doesNotContain("docs.metrics.usecases"); //smoke test that usecases have been excluded
        // @formatter:on
    }

}
