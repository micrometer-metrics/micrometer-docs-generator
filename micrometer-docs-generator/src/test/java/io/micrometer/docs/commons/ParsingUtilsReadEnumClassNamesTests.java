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

import java.util.Set;

import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.docs.MeterDocumentation;
import io.micrometer.docs.RoasterTestUtils;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ParsingUtils#readEnumClassNames(MethodSource)}.
 *
 * @author Tadaya Tsuyukubo
 */
class ParsingUtilsReadEnumClassNamesTests {

    @Test
    void readSimpleEnumClassName() {
        JavaClassSource javaSource = RoasterTestUtils.readJavaClass(ParsingUtilsReadEnumClassNamesTests.class);
        MethodSource<?> methodSource = ((JavaClassSource) javaSource.getNestedType("MyClass")).getMethod("simple");
        Set<String> result = ParsingUtils.readEnumClassNames(methodSource);
        assertThat(result).containsExactlyInAnyOrder("FooKeyName");
    }

    @Test
    void readMergeEnumClassName() {
        JavaClassSource javaSource = RoasterTestUtils.readJavaClass(ParsingUtilsReadEnumClassNamesTests.class);
        MethodSource<?> methodSource = ((JavaClassSource) javaSource.getNestedType("MyClass")).getMethod("merge");
        Set<String> result = ParsingUtils.readEnumClassNames(methodSource);
        assertThat(result).containsExactlyInAnyOrder("FooKeyName", "BarKeyName", "BazKeyName");
    }

    @Test
    void readNestedEnumClassName() {
        JavaClassSource javaSource = RoasterTestUtils.readJavaClass(ParsingUtilsReadEnumClassNamesTests.class);
        MethodSource<?> methodSource = ((JavaClassSource) javaSource.getNestedType("MyClass")).getMethod("mergeNested");
        Set<String> result = ParsingUtils.readEnumClassNames(methodSource);
        assertThat(result).containsExactlyInAnyOrder("NestedFooKeyName", "NestedBarKeyName");
    }

    static class MyClass {

        Enum<?>[] simple() {
            return FooKeyName.values();
        }

        KeyName[] merge() {
            return KeyName.merge(FooKeyName.values(), BarKeyName.values(), BazKeyName.values());
        }

        KeyName[] mergeNested() {
            return KeyName.merge(FooMeterDocumentation.NestedFooKeyName.values(),
                    FooMeterDocumentation.NestedBarKeyName.values());
        }

    }

    enum FooKeyName implements KeyName {

        FOO {
            @Override
            public String asString() {
                return "foo";
            }
        }

    }

    enum BarKeyName implements KeyName {

        BAR {
            @Override
            public String asString() {
                return "bar";
            }
        }

    }

    enum BazKeyName implements KeyName {

        BAZ {
            @Override
            public String asString() {
                return "baz";
            }
        }

    }

    enum FooMeterDocumentation implements MeterDocumentation {

        FOO {
            @Override
            public String getName() {
                return "foo";
            }

            @Override
            public Meter.Type getType() {
                return Meter.Type.COUNTER;
            }
        };

        enum NestedFooKeyName implements KeyName {

            NESTED_FOO {
                @Override
                public String asString() {
                    return "nested.foo";
                }
            }

        }

        enum NestedBarKeyName implements KeyName {

            NESTED_BAR {
                @Override
                public String asString() {
                    return "nested.bar";
                }
            }

        }

    }

}
