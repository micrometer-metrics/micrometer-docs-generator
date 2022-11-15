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

import io.micrometer.docs.commons.utils.AsciidocUtils;
import io.micrometer.docs.commons.utils.Assert;
import io.micrometer.observation.Observation;
import org.jboss.forge.roaster.model.source.EnumConstantSource;

/**
 * Responsible for reading {@link Observation.Event} enum source for span.
 *
 * @author Tadaya Tsuyukubo
 */
public class EventEntryForSpanEnumConstantReader implements EntryEnumConstantReader<EventEntry> {

    public static final EventEntryForSpanEnumConstantReader INSTANCE = new EventEntryForSpanEnumConstantReader();

    @Override
    public Class<?> getRequiredClass() {
        return Observation.Event.class;
    }

    @Override
    public EventEntry apply(EnumConstantSource enumConstantSource) {
        String description = AsciidocUtils.javadocToAsciidoc(enumConstantSource.getJavaDoc());
        String name = ParsingUtils.enumMethodValue(enumConstantSource, "getContextualName");
        Assert.notNull(name,
                String.format("Event enum constants require readable getContextualName() for spans documentation. [%s]",
                        enumConstantSource.getName()));

        EventEntry model = new EventEntry();
        model.setName(name);
        model.setDescription(description);
        return model;
    }

}
