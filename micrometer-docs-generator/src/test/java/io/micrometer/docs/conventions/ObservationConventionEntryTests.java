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
package io.micrometer.docs.conventions;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.jknack.handlebars.Handlebars;
import io.micrometer.docs.commons.templates.HandlebarsUtils;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        List<ObservationConventionEntry> globals = Collections.singletonList(globalEntry);
        List<ObservationConventionEntry> locals = Collections.singletonList(localEntry);

        Handlebars handlebars = HandlebarsUtils.createHandlebars();

        Map<String, Object> map = new HashMap<>();
        map.put("globals", globals);
        map.put("locals", locals);

        String template = "templates/conventions.adoc.hbs";

        String result = handlebars.compile(template).apply(map);

        BDDAssertions.then(result)
                .contains("|`foo.bar.GlobalBaz`|`Foo`")
                .contains("|`foo.bar.LocalBaz`|`Observation.Context`");
    }
}
