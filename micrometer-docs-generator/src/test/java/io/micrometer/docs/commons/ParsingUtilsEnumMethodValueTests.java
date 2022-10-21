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

package io.micrometer.docs.commons;

import io.micrometer.docs.RoasterTestUtils;
import org.jboss.forge.roaster.model.source.EnumConstantSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.JavaEnumSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ParsingUtils#enumMethodValue(EnumConstantSource, String)}.
 *
 * @author Tadaya Tsuyukubo
 */
class ParsingUtilsEnumMethodValueTests {

    @Test
    void enumMethodValue() {
        JavaClassSource classSource = RoasterTestUtils.readJavaClass(ParsingUtilsEnumMethodValueTests.class);
        JavaEnumSource enumSource = (JavaEnumSource) classSource.getNestedType(MyEnum.class.getSimpleName());
        EnumConstantSource foo = enumSource.getEnumConstant("FOO");
        EnumConstantSource bar = enumSource.getEnumConstant("BAR");

        String result;

        result = ParsingUtils.enumMethodValue(foo, "toString");
        assertThat(result).isNull();

        result = ParsingUtils.enumMethodValue(foo, "nonExistingMethod");
        assertThat(result).isNull();

        result = ParsingUtils.enumMethodValue(bar, "toString");
        assertThat(result).isEqualTo("toString-override");
    }

    enum MyEnum {

        FOO {
            // no method defined
        },
        BAR {
            @Override
            public String toString() {
                return "toString-override";
            }
        }

    }

}
