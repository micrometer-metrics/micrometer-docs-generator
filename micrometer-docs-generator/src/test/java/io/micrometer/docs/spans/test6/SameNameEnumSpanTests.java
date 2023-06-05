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
package io.micrometer.docs.spans.test6;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import io.micrometer.docs.spans.SpansDocGenerator;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

class SameNameEnumSpanTests {

    @Test
    void should_detect_same_name_spans_documentations() throws IOException {
        File root = new File("./src/test/java/io/micrometer/docs/spans/test6");
        Path output = Paths.get(".", "build/test6", "_spans.adoc");
        Files.createDirectories(output.getParent());

        new SpansDocGenerator(root, Pattern.compile(".*"), "templates/spans.adoc.hbs", output).generate();

        // @formatter:off
        BDDAssertions.then(new String(Files.readAllBytes(output)))
                .contains("foo in top")
                .contains("foo in one")
                .contains("foo in two")
                .contains("foo in same-enum-value")
                .contains("foo-span in top")
                .contains("foo-span in one")
                .contains("foo-span in two")
                .contains("foo-span in same-enum-value")
        ;
        // @formatter:on
    }

}
