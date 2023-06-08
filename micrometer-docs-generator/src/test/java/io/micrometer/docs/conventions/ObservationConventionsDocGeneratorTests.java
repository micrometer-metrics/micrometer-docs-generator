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

package io.micrometer.docs.conventions;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ObservationConventionsDocGenerator}.
 *
 * @author Tadaya Tsuyukubo
 */
class ObservationConventionsDocGeneratorTests {

    @Test
    void withConventionClasses() throws Exception {
        File root = new File("./src/test/java/io/micrometer/docs/conventions/data1");
        Path output = Paths.get(".", "build/data1", "_conventions.adoc");
        Files.createDirectories(output.getParent());

        new ObservationConventionsDocGenerator(root, Pattern.compile(".*"), "templates/conventions.adoc.hbs", output)
            .generate();

        // @formatter:off
        BDDAssertions.then(new String(Files.readAllBytes(output)))
                .contains("|`io.micrometer.docs.conventions.data1.MyGlobalConvention`|`MyContext`")
                .contains("|`io.micrometer.docs.conventions.data1.MyConvention`|`MyContext`");
        // @formatter:on

    }

    @Test
    void withConventionInterfaces() throws Exception {
        File root = new File("./src/test/java/io/micrometer/docs/conventions/data2");
        Path output = Paths.get(".", "build/data2", "_conventions.adoc");
        Files.createDirectories(output.getParent());

        new ObservationConventionsDocGenerator(root, Pattern.compile(".*"), "templates/conventions.adoc.hbs", output)
            .generate();

        // @formatter:off
        BDDAssertions.then(new String(Files.readAllBytes(output)))
                .contains("|`io.micrometer.docs.conventions.data2.MyInterfaceGlobalConvention`|`MyContext`")
                .contains("|`io.micrometer.docs.conventions.data2.MyInterfaceConvention`|`MyContext`");
        // @formatter:on

    }

}
