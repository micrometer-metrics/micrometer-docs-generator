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
package io.micrometer.docs.commons.templates;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.helper.StringHelpers;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.CompositeTemplateLoader;
import com.github.jknack.handlebars.io.FileTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;

/**
 * Utility for {@link Handlebars}.
 *
 * @author Tadaya Tsuyukubo
 */
public class HandlebarsUtils {

    public static Handlebars createHandlebars() {
        // specify default prefix and empty suffix. The empty suffix forces users to
        // specify the full template file name. (e.g. foo.adoc.hbs)
        ClassPathTemplateLoader classPathLoader = new ClassPathTemplateLoader(TemplateLoader.DEFAULT_PREFIX, "");
        FileTemplateLoader fileLoader = new FileTemplateLoader(TemplateLoader.DEFAULT_PREFIX, "");
        CompositeTemplateLoader compositeLoader = new CompositeTemplateLoader(classPathLoader, fileLoader);

        Handlebars handlebars = new Handlebars(compositeLoader);
        handlebars.registerHelpers(ADocHelpers.class);
        StringHelpers.register(handlebars);

        return handlebars;
    }
}
