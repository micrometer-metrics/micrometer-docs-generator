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
import io.micrometer.docs.commons.utils.AsciidocUtils;
import io.micrometer.docs.commons.utils.Assert;
import org.jboss.forge.roaster.model.source.EnumConstantSource;

/**
 * Responsible for reading {@link KeyName} enum source.
 *
 * @author Tadaya Tsuyukubo
 */
public class KeyNameEnumConstantReader implements EntryEnumConstantReader<KeyNameEntry> {

    public static final KeyNameEnumConstantReader INSTANCE = new KeyNameEnumConstantReader();

    @Override
    public Class<?> getRequiredClass() {
        return KeyName.class;
    }

    @Override
    public KeyNameEntry apply(EnumConstantSource enumConstantSource) {
        String description = AsciidocUtils.javadocToAsciidoc(enumConstantSource.getJavaDoc());
        String value = ParsingUtils.enumMethodValue(enumConstantSource, "asString");
        String required = ParsingUtils.enumMethodValue(enumConstantSource, "isRequired");

        Assert.notNull(value, "KeyName enum constants require readable asString().");

        // isRequired is "true" by default in KeyName.
        // When the enum does not override the method, the returned value is empty, which
        // means "isRequired=true".
        // Set false, only when the value read is "false"
        boolean isRequired = !"false".equals(required);

        KeyNameEntry model = new KeyNameEntry();
        model.setName(value);
        model.setDescription(description);
        model.setRequired(isRequired);
        return model;
    }

}
