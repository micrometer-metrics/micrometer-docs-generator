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
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.micrometer.docs.RoasterTestUtils;
import io.micrometer.docs.commons.search.convention.test1.SimpleObservationConvention;
import io.micrometer.docs.commons.search.convention.test2.NestedHolder;
import io.micrometer.docs.commons.search.convention.test3.NoContextObservationConvention;
import io.micrometer.docs.commons.search.convention.test4.NoImportObservationConvention;
import io.micrometer.docs.commons.search.convention.test5.InterfaceInheritingObservationConvention;
import io.micrometer.docs.commons.search.convention.test6.ClassInheritingObservationConvention;
import io.micrometer.docs.commons.search.search_test.Container;
import io.micrometer.docs.commons.search.test1.ReferenceSample;
import io.micrometer.docs.commons.search.test2.MethodSearchSample;
import io.micrometer.docs.commons.search.test3.MethodSearchEnumSample;
import io.micrometer.docs.commons.search.test4.MyService;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Expression;
import org.jboss.forge.roaster.model.source.EnumConstantSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.JavaEnumSource;
import org.jboss.forge.roaster.model.source.JavaSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JavaSourceSearchHelper}.
 *
 * @author Tadaya Tsuyukubo
 */
class JavaSourceSearchHelperTests {

    @ParameterizedTest
    @ValueSource(classes = { Container.class, Container.Nest1.class, Container.Nest1.Nest2.class })
    void search(Class<?> clazz) {
        Path path = Paths.get("src/test/java/io/micrometer/docs/commons/search/search_test");
        JavaSourceSearchHelper helper = JavaSourceSearchHelper.create(path, Pattern.compile(".*"));

        String qualifiedName = clazz.getName();
        JavaSource<?> result = helper.search(qualifiedName);
        assertThat(result).isNotNull().extracting(JavaSource::getQualifiedName).isEqualTo(qualifiedName);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource
    void searchReferencingClass(String className, String expectedSimpleName) {
        Path path = Paths.get("src/test/java/io/micrometer/docs/commons/search/test1");
        JavaSourceSearchHelper helper = JavaSourceSearchHelper.create(path, Pattern.compile(".*"));
        JavaClassSource enclosingSource = RoasterTestUtils.readJavaClass(ReferenceSample.class);

        JavaSource<?> result = helper.searchReferencingClass(enclosingSource, className);
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(expectedSimpleName);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource
    void searchMethodSource(String methodName, String expectedEnclosingClassName) {
        Path path = Paths.get("src/test/java/io/micrometer/docs/commons/search/test2");
        JavaSourceSearchHelper helper = JavaSourceSearchHelper.create(path, Pattern.compile(".*"));
        JavaClassSource enclosingSource = RoasterTestUtils.readJavaClass(MethodSearchSample.class);

        MethodSource<?> result = helper.searchMethodSource(enclosingSource, methodName);
        assertThat(result).isNotNull();
        assertThat(result.getOrigin().getName()).isEqualTo(expectedEnclosingClassName);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource
    void searchMethodSourceOnEnum(String methodName, String expectedEnclosingClassName) {
        Path path = Paths.get("src/test/java/io/micrometer/docs/commons/search/test3");
        JavaSourceSearchHelper helper = JavaSourceSearchHelper.create(path, Pattern.compile(".*"));
        JavaEnumSource enclosingSource = RoasterTestUtils.readJavaEnum(MethodSearchEnumSample.class);

        MethodSource<?> result = helper.searchMethodSource(enclosingSource, methodName);
        assertThat(result).isNotNull();
        assertThat(result.getOrigin().getName()).isEqualTo(expectedEnclosingClassName);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource
    void searchReferencingEnumConstant(String methodName, String expectedEnumConstantName) {
        Path path = Paths.get("src/test/java/io/micrometer/docs/commons/search/test4");
        JavaSourceSearchHelper helper = JavaSourceSearchHelper.create(path, Pattern.compile(".*"));
        JavaClassSource enclosingSource = RoasterTestUtils.readJavaClass(MyService.class);

        MethodSource<?> methodSource = enclosingSource.getMethod(methodName);
        Expression expression = ParsingUtils.expressionFromReturnMethodDeclaration(methodSource);

        EnumConstantSource result = helper.searchReferencingEnumConstant(enclosingSource, expression);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(expectedEnumConstantName);
    }

    @ParameterizedTest(name = "[{index}] {1}")
    @org.junit.jupiter.params.provider.MethodSource
    void searchObservationConventionInterfaceName(Path path, Class<?> clazz, String expectedName) {
        JavaSourceSearchHelper helper = JavaSourceSearchHelper.create(path, Pattern.compile(".*"));
        JavaSource<?> javaSource = helper.search(clazz.getName());
        String result = helper.searchObservationConventionInterfaceName(javaSource);
        assertThat(result).isEqualTo(expectedName);
    }

    static Stream<Arguments> searchReferencingClass() {
        // uses test1
        // @formatter:off
        return Stream.of(
                // simple name
                Arguments.of("NestedFoo", "NestedFoo"),
                // Roaster type name
                Arguments.of("ReferenceSample.NestedFoo", "NestedFoo"),
                // Canonical name
                Arguments.of("io.micrometer.docs.commons.search.test1.ReferenceSample.NestedFoo", "NestedFoo"),
                // Qualified name
                Arguments.of("io.micrometer.docs.commons.search.test1.ReferenceSample$NestedFoo", "NestedFoo"),

                Arguments.of("NestedBar", "NestedBar"),
                Arguments.of("NestedFoo.NestedBar", "NestedBar"),
                Arguments.of("ReferenceSample.NestedFoo.NestedBar", "NestedBar"),
                Arguments.of("io.micrometer.docs.commons.search.test1.ReferenceSample.NestedFoo.NestedBar", "NestedBar"),
                Arguments.of("InSamePackage", "InSamePackage"),
                Arguments.of("InDifferentPackage", "InDifferentPackage")
        );
        // @formatter:on
    }

    static Stream<Arguments> searchMethodSource() {
        // uses test2
        // @formatter:off
        return Stream.of(
                Arguments.of("hello", "MethodSearchSample"),
                Arguments.of("overrideByChild", "MethodSearchSample"),
                Arguments.of("fromBar", "MethodSearchSample"),
                Arguments.of("fromBarDefaultOverride", "MethodSearchSample"),

                Arguments.of("hi", "MethodSearchSampleParent"),
                Arguments.of("fromFoo", "MethodSearchSampleParent"),
                Arguments.of("fromFooDefaultOverride", "MethodSearchSampleParent"),

                Arguments.of("fromFooDefault", "FooInterface"),
                Arguments.of("fromBarDefault", "BarInterface")
        );
        // @formatter:on
    }

    static Stream<Arguments> searchMethodSourceOnEnum() {
        // uses test3
        // @formatter:off
        return Stream.of(
                Arguments.of("greet", "MethodSearchEnumSample"),
                Arguments.of("custom", "MethodSearchEnumSample"),
                Arguments.of("hello", "GreetingInterface")
        );
        // @formatter:on
    }

    static Stream<Arguments> searchReferencingEnumConstant() {
        // uses test4
        // @formatter:off
        return Stream.of(
                Arguments.of("foo", "FOO"),
                Arguments.of("same", "SAME"),
                Arguments.of("different", "DIFFERENT"),
                Arguments.of("hello", "HELLO")
        );
        // @formatter:on
    }

    static Stream<Arguments> searchObservationConventionInterfaceName() {
        // uses convention
        // @formatter:off
        return Stream.of(
                Arguments.of(Paths.get("src/test/java/io/micrometer/docs/commons/search/convention/test1"),
                        Named.of(SimpleObservationConvention.class.getSimpleName(), SimpleObservationConvention.class),
                        "io.micrometer.observation.ObservationConvention<Observation.Context>"),
                Arguments.of(Paths.get("src/test/java/io/micrometer/docs/commons/search/convention/test2"),
                        Named.of(NestedHolder.class.getSimpleName(), NestedHolder.class),
                        "io.micrometer.observation.ObservationConvention<Observation.Context>"),
                Arguments.of(Paths.get("src/test/java/io/micrometer/docs/commons/search/convention/test3"),
                        Named.of(NoContextObservationConvention.class.getSimpleName(), NoContextObservationConvention.class),
                        "io.micrometer.observation.ObservationConvention"),
                Arguments.of(Paths.get("src/test/java/io/micrometer/docs/commons/search/convention/test4"),
                        Named.of(NoImportObservationConvention.class.getSimpleName(), NoImportObservationConvention.class),
                        "io.micrometer.observation.ObservationConvention<io.micrometer.observation.Observation.Context>"),
                Arguments.of(Paths.get("src/test/java/io/micrometer/docs/commons/search/convention/test5"),
                        Named.of(InterfaceInheritingObservationConvention.class.getSimpleName(), InterfaceInheritingObservationConvention.class),
                        "io.micrometer.observation.ObservationConvention<Observation.Context>"),
                Arguments.of(Paths.get("src/test/java/io/micrometer/docs/commons/search/convention/test6"),
                        Named.of(ClassInheritingObservationConvention.class.getSimpleName(), ClassInheritingObservationConvention.class),
                        "io.micrometer.observation.ObservationConvention<Observation.Context>")
        );
        // @formatter:on
    }

}
