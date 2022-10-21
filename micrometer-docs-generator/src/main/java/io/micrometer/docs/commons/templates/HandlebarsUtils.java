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
package io.micrometer.docs.commons.templates;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
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

    /**
     * Create a handlebar {@link Template} from template location.
     * <p>
     * While loading the template, this method converts the line delimiter from the one
     * used in template file("LF") to the one from the running OS. (for example, "CRLF" on
     * a windows machine).
     * @param templateLocation template location (either in classpath or file system)
     * @return a template
     * @throws IOException If the template's source can't be resolved.
     */
    public static Template createTemplate(String templateLocation) throws IOException {
        Handlebars handlebars = createHandlebars();
        String content = handlebars.getLoader().sourceAt(templateLocation).content(StandardCharsets.UTF_8);

        // replace the line delimiter in template file to the running OS specific one
        StringBuilder sb = new StringBuilder();
        try (Scanner scanner = new Scanner(content)) {
            while (scanner.hasNext()) {
                sb.append(scanner.nextLine());
                sb.append(System.lineSeparator());
            }
        }
        return handlebars.compileInline(sb.toString());
    }

}
