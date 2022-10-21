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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import io.micrometer.docs.spans.SpansDocGenerator;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

class ConventionsTests {

    @Test
    void should_build_a_table_out_of_enum_tag_key() throws IOException {
        File root = new File("./src/test/java/io/micrometer/docs/spans/conventions");
        Path output = Paths.get(".", "build/conventions", "_spans.adoc");
        Files.createDirectories(output.getParent());

        new SpansDocGenerator(root, Pattern.compile(".*"), "templates/spans.adoc.hbs", output).generate();

        // @formatter:off
        BDDAssertions.then(new String(Files.readAllBytes(output)))
                .contains("**Span name** `nested convention` (defined by convention class `io.micrometer.docs.spans.conventions.AnnotationSpan$NestedConvention`)")
                .contains("**Span name** `foo` (defined by convention class `io.micrometer.docs.spans.conventions.PublicObservationConvention`)")
                .contains("**Span name** `foo-iface` (defined by convention class `io.micrometer.docs.spans.conventions.UseInterfaceObservationConvention`)")
                .contains("**Span name** Unable to resolve the name - please check the convention class `io.micrometer.docs.spans.conventions.DynamicObservationConvention` for more details.");
        // @formatter:on
    }

}
