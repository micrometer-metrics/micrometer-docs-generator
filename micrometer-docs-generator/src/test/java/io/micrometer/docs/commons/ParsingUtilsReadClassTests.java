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

import io.micrometer.common.docs.KeyName;
import io.micrometer.docs.RoasterTestUtils;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MethodDeclaration;
import org.jboss.forge.roaster.model.source.EnumConstantSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.JavaEnumSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ParsingUtils#readClass(MethodDeclaration)}.
 *
 * @author Tadaya Tsuyukubo
 */
class ParsingUtilsReadClassTests {

    // for  https://github.com/micrometer-metrics/micrometer-docs-generator/issues/60
    @Test
    void readClass() {
        JavaClassSource classSource = RoasterTestUtils.readJavaClass(ParsingUtilsReadClassTests.class);
        JavaEnumSource enumSource = (JavaEnumSource) classSource.getNestedType(ReadingClassObservationDocumentation.class.getSimpleName());
        EnumConstantSource enumConstantSource = enumSource.getEnumConstant("FOO");
        MethodSource<?> methodSource = enumConstantSource.getBody().getMethod("getDefaultConvention");
        MethodDeclaration methodDeclaration = (MethodDeclaration) methodSource.getInternal();

        String result = ParsingUtils.readClass(methodDeclaration);
        assertThat(result).isEqualTo("io.micrometer.docs.commons.ParsingUtilsReadClassTests$ReadingClassObservationConvention");
    }

    enum ReadingClassObservationDocumentation implements ObservationDocumentation {

        FOO {
            @Override
            public Class<? extends ObservationConvention<? extends Context>> getDefaultConvention() {
                return ReadingClassObservationConvention.class;
            }
        };


        // This method should be implemented in enum constant (e.g. FOO)
        // Having this method here caused failure in docs gen.
        // https://github.com/micrometer-metrics/micrometer-docs-generator/issues/60
        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return new KeyName[] {};
        }
    }

    static class ReadingClassObservationConvention implements ObservationConvention<Context> {
        @Override
        public boolean supportsContext(Context context) {
            return true;
        }
    }

}
