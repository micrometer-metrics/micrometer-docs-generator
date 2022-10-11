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

package io.micrometer.docs;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;

/**
 * Test Utility for roaster related operations.
 *
 * @author Tadaya Tsuyukubo
 */
public class RoasterTestUtils {

    public static JavaClassSource readJavaClass(Class<?> clazz) {
        String filename = clazz.getCanonicalName().replace(".", "/") + ".java";
        Path path = Paths.get("src/test/java", filename);
        try {
            return Roaster.parse(JavaClassSource.class, path.toFile());
        }
        catch (IOException ex) {
            throw new RuntimeException("Failed to read class file on " + path, ex);
        }
    }

}
