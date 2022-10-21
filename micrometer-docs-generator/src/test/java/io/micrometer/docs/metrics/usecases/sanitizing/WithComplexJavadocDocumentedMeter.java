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
package io.micrometer.docs.metrics.usecases.sanitizing;

import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.docs.MeterDocumentation;

enum WithComplexJavadocMeterDocumentation implements MeterDocumentation {

    // @formatter:off
    /**
     * This javadoc includes sanitized HTML elements and should result in multi-line output,
     * except when a line is just there for wrapping like this one.
     * <p>
     * A paragraph.
     * </p>
     * <p>
     * An unclosed paragraph.
     * <br>
     * An unclosed BR.
     * <br/>
     * A closed in single tag BR.
     * <ul>
     *     <li>it also contains</li>
     *     <li>an unordered list</li>
     * </ul>
     * <strong>This is a sentence with <b>bold</b> and <i>italics</i> inside a strong tag.</strong>
     *
     * @return nothing
     * @param none no parameter
     */
    // @formatter:on
    HTML {
        @Override
        public String getName() {
            return "html";
        }

        @Override
        public Meter.Type getType() {
            return Meter.Type.TIMER;
        }
    },

    // @formatter:off
    /**
     * This javadoc includes sanitized taglets elements which should all result in a single line:
     * This is code: {@code someCode}.
     * This is a simple link: {@linkplain #HTML}.
     * This is a complex link with alias text: {@link io.micrometer.docs.commons.utils.AsciidocUtils#simpleHtmlToAsciidoc(String, boolean) some custom alias}.
     */
    // @formatter:on
    TAGLETS {
        @Override
        public String getName() {
            return "taglets";
        }

        @Override
        public Meter.Type getType() {
            return Meter.Type.TIMER;
        }
    },

    // @formatter:off
    /**
     * Single line with<br/>inline new line then an admonition.<strong>This is an admonition with *bold* and _italics_.</strong> This text is not part of the admonition.
     */
    // @formatter:on
    INLINE_HTML_TAGS {
        @Override
        public String getName() {
            return "inline_html_tags";
        }

        @Override
        public Meter.Type getType() {
            return Meter.Type.TIMER;
        }
    },

    // @formatter:off
    /**
     * This one demonstrates javadoc extraction and sanitization in tags.
     */
    // @formatter:on
    WITH_TAGS {
        @Override
        public String getName() {
            return "tags";
        }

        @Override
        public Meter.Type getType() {
            return Meter.Type.TIMER;
        }

        @Override
        public KeyName[] getKeyNames() {
            return ComplexJavadocTags.values();
        }
    };

    enum ComplexJavadocTags implements KeyName {

        // @formatter:off
        /**
         * This tag javadoc includes sanitized HTML elements and should result in multi-line output:
         * <p>
         * A paragraph.
         * </p>
         * <p>
         * An unclosed paragraph.
         * <br>
         * An unclosed BR.
         * <br/>
         * A closed in single tag BR.
         * <ul>
         *     <li>it also contains</li>
         *     <li>an unordered list</li>
         * </ul>
         */
        // @formatter:on
        TAG_HTML {
            @Override
            public String asString() {
                return "class";
            }
        },

        // @formatter:off
        /**
         * This tag javadoc includes sanitized taglets elements which should all result in a single line:
         * This is code: {@code someCode}.
         * This is a simple link: {@link #HTML}.
         * This is a link with alias text: {@link #HTML alias}.
         */
        // @formatter:on
        TAG_TAGLETS {
            @Override
            public String asString() {
                return "method";
            }
        }

    }

}
