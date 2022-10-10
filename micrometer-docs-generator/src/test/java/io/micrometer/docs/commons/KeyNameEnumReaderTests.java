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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import io.micrometer.common.docs.KeyName;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.EnumConstantSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.JavaEnumSource;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link KeyNameEnumConstantReader}.
 *
 * @author Tadaya Tsuyukubo
 */
class KeyNameEnumReaderTests {

    @ParameterizedTest
    @MethodSource
    void convert(EnumConstantSource enumConstantSource, String expectedName, String expectedDesc) {
        KeyNameEnumConstantReader reader = new KeyNameEnumConstantReader();
        KeyNameEntry result = reader.apply(enumConstantSource);
        assertThat(result.getName()).isEqualTo(expectedName);
        assertThat(result.getDescription()).isEqualTo(expectedDesc);
    }

    static Stream<Arguments> convert() throws Exception {
        String filename = KeyNameEnumReaderTests.class.getCanonicalName().replace(".", "/") + ".java";
        Path path = Paths.get("src/test/java", filename);
        JavaClassSource classSource = Roaster.parse(JavaClassSource.class, path.toFile());
        JavaEnumSource enumSource = (JavaEnumSource) classSource.getNestedType(MyKeyNameEnum.class.getSimpleName());
        EnumConstantSource foo = enumSource.getEnumConstant("FOO");
        EnumConstantSource bar = enumSource.getEnumConstant("BAR");
        EnumConstantSource baz = enumSource.getEnumConstant("BAZ");
        return Stream.of(
                // enum source, name, description
                Arguments.of(Named.of("FOO", foo), "foo", "Foo title"),
                Arguments.of(Named.of("BAR", bar), "bar", "Bar title"),
                Arguments.of(Named.of("BAZ", baz), "baz", "Baz title")
        );
    }


    enum MyKeyNameEnum implements KeyName {
        /**
         * Foo title
         */
        FOO {
            @Override
            public String asString() {
                return "foo";
            }
        },

        /**
         * Bar title
         */
        BAR {
            @Override
            public String asString() {
                return "bar";
            }

            @Override
            public boolean isRequired() {
                return false;
            }
        },
        /**
         * Baz title
         */
        BAZ {
            @Override
            public String asString() {
                return "baz";
            }

            @Override
            public boolean isRequired() {
                return true;
            }
        }
    }
}
