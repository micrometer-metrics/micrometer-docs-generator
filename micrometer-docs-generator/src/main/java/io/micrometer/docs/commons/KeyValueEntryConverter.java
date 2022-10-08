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

import org.jboss.forge.roaster.model.source.EnumConstantSource;

/**
 * Temporal bridge from model classes to {@link KeyValueEntry}.
 * This class should be removed once {@link KeyValueEntry} is removed.
 *
 * @author Tadaya Tsuyukubo
 */
public interface KeyValueEntryConverter {

    KeyValueEntry toKeyValueEntry(EnumConstantSource enumConstantSource);

}