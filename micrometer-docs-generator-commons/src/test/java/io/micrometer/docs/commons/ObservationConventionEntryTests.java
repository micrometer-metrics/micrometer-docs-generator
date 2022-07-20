/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micrometer.docs.commons;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ObservationConventionEntryTests {
    File output = new File(".", "build/conventions");

    @BeforeEach
    void setup() {
        output.mkdirs();
    }

    //TODO: Write other test cases
    @Test
    void should_save_conventions_as_adoc_table() throws IOException {
        ObservationConventionEntry localEntry = new ObservationConventionEntry("foo.bar.LocalBaz", ObservationConventionEntry.Type.LOCAL, "Observation.Context");
        ObservationConventionEntry globalEntry = new ObservationConventionEntry("foo.bar.GlobalBaz", ObservationConventionEntry.Type.GLOBAL, "Foo");
        File file = new File(this.output, "_success.adoc");

        ObservationConventionEntry.saveEntriesAsAdocTableInAFile(Arrays.asList(localEntry, globalEntry), file);

        BDDAssertions.then(new String(Files.readAllBytes(file.toPath())))
                .contains("|`foo.bar.GlobalBaz`|`Foo`")
                .contains("|`foo.bar.LocalBaz`|`Observation.Context`");
    }
}
