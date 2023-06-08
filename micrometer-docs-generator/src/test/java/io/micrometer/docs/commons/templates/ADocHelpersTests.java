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

package io.micrometer.docs.commons.templates;

import com.github.jknack.handlebars.Options;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ADocHelpers}.
 *
 * @author Tadaya Tsuyukubo
 */
class ADocHelpersTests {

    @Test
    void anchor() throws Exception {
        ADocHelpers.AnchorHelper helper = new ADocHelpers.AnchorHelper();
        Options options = mock(Options.class);
        assertThat(helper.apply("foo", options)).isEqualTo("foo");
        assertThat(helper.apply("foo", options)).isEqualTo("foo-1");
        assertThat(helper.apply("foo", options)).isEqualTo("foo-2");
        assertThat(helper.apply("bar", options)).isEqualTo("bar");
        assertThat(helper.apply("bar", options)).isEqualTo("bar-1");
        assertThat(helper.apply("baz", options)).isEqualTo("baz");
    }

}
