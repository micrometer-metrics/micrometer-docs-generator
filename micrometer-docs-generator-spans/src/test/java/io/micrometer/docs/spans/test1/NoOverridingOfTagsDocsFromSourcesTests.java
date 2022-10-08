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
package io.micrometer.docs.spans.test1;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Pattern;

import io.micrometer.docs.spans.DocsFromSources;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

class NoOverridingOfTagsDocsFromSourcesTests {

    @Test
    void should_build_a_table_out_of_enum_tag_key() throws IOException {
        File root = new File("./src/test/java/io/micrometer/docs/spans/test1");
        File output = new File(".", "build/test1");
        output.mkdirs();

        new DocsFromSources(root, Pattern.compile(".*"), output).generate();

        BDDAssertions.then(new String(Files.readAllBytes(new File(output, "_spans.adoc").toPath())))
                .contains("==== Async Annotation Span").contains("> Span that wraps a")
                .contains("**Span name** `%s` - since").contains("Fully qualified name of")
                .contains("|`class`|Class name where a method got annotated with @Async.")
                .contains("==== Annotation New Or Continue Span")
                .contains("|`%s.before`|Annotated before executing a method annotated with @ContinueSpan or @NewSpan.")
                .contains("==== Test Span").contains("**Span name** `fixed`.").contains("|`foooooo`|Test foo")
                .contains("==== Parent Span")
                .contains("|`parent.class`|Class name where a method got annotated with a annotation.")
                .contains("Events Having Observation Span")
                .contains("|`start annotation`|Start event.")
                .contains("|`stop %s %s foo`|Stop event. (since the name contains `%s` the final value will be resolved at runtime)");
    }

}
