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
package io.micrometer.docs.spans.test2;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import io.micrometer.docs.spans.SpansDocGenerator;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

class TagsFromKeyNamesDocsFromSourcesTests {

    @Test
    void should_take_tags_from_tag_keys() throws IOException {
        File root = new File("./src/test/java/io/micrometer/docs/spans/test2");
        Path output = Paths.get(".", "build/test2", "_spans.adoc");
        Files.createDirectories(output.getParent());

        new SpansDocGenerator(root, Pattern.compile(".*"), "templates/spans.adoc.hbs", output).generate();

        BDDAssertions.then(new String(Files.readAllBytes(output)))
                .doesNotContain("==== Parent Span")  // this should be overridden
                .contains("**Span name** `%s` - since").contains("Fully qualified name of")
                .contains("==== Should Return Tag Keys Only Span").contains("|`foooooo`|Test foo");
    }

}