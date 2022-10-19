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
package io.micrometer.docs.spans.test5;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import io.micrometer.docs.spans.SpansDocGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WrongTagPrefixTests {

    @Test
    void should_fail_wrong_prefix() throws IOException {
        File root = new File("./src/test/java/io/micrometer/docs/spans/test5");
        Path output = Paths.get(".", "build/test5", "_spans.adoc");
        Files.createDirectories(output.getParent());

        assertThatThrownBy(() ->
                new SpansDocGenerator(root, Pattern.compile(".*"), "templates/spans.adoc.hbs", output).generate())
                .hasMessageContaining("The following documented objects do not have properly prefixed tag keys according to their prefix() method. Please align the tag keys.")
                .hasMessageContaining("Name <PARENT> in class <io.micrometer.docs.spans.test5.ParentSample> has the following prefix <foo.bar> and following invalid tag keys bar")
                .hasMessageContaining("Name <PARENT> in class <io.micrometer.docs.spans.test5.ParentSample> has the following prefix <foo.bar> and following invalid tag keys foo");

        assertThat(output).doesNotExist();
    }

}
