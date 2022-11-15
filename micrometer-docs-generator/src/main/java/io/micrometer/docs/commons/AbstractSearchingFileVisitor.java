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

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.docs.commons.utils.StringUtils;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.EnumConstantSource;
import org.jboss.forge.roaster.model.source.JavaEnumSource;
import org.jboss.forge.roaster.model.source.JavaSource;
import org.jboss.forge.roaster.model.source.MethodSource;

/**
 * Shared logic to process documentation enum classes.
 *
 * @author Tadaya Tsuyukubo
 */
public abstract class AbstractSearchingFileVisitor extends SimpleFileVisitor<Path> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractSearchingFileVisitor.class);

    protected final Pattern pattern;

    protected final JavaSourceSearchHelper searchHelper;

    public AbstractSearchingFileVisitor(Pattern pattern, JavaSourceSearchHelper searchHelper) {
        this.pattern = pattern;
        this.searchHelper = searchHelper;
    }

    @Override
    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
        if (!this.pattern.matcher(path.toString()).matches()) {
            return FileVisitResult.CONTINUE;
        }
        else if (!path.toString().endsWith(".java")) {
            return FileVisitResult.CONTINUE;
        }

        logger.debug("Parsing [" + path + "]");
        JavaSource<?> javaSource = Roaster.parse(JavaSource.class, path.toFile());
        if (!javaSource.isEnum()) {
            return FileVisitResult.CONTINUE;
        }
        JavaEnumSource enumSource = (JavaEnumSource) javaSource;

        if (supportedInterfaces().stream().noneMatch(enumSource::hasInterface)) {
            return FileVisitResult.CONTINUE;
        }

        logger.debug("Checking [" + javaSource.getName() + "]");
        if (enumSource.getMethods().size() > 0) {
            String message = String.format(
                    "The enum constants can define methods but the container enum class(%s) cannot define methods.",
                    enumSource.getName());
            throw new RuntimeException(message);
        }
        if (enumSource.getEnumConstants().size() == 0) {
            return FileVisitResult.CONTINUE;
        }
        for (EnumConstantSource enumConstant : enumSource.getEnumConstants()) {
            if (enumConstant.getBody().getMethods().isEmpty()) {
                continue;
            }
            onEnumConstant(enumSource, enumConstant);
        }
        return FileVisitResult.CONTINUE;
    }

    public abstract Collection<Class<?>> supportedInterfaces();

    public abstract void onEnumConstant(JavaEnumSource enclosingEnumSource, EnumConstantSource enumConstant);

    protected void validateNameOrConvention(String name, @Nullable String conventionClassName,
            JavaEnumSource enclosingEnum) {
        if (StringUtils.hasText(name)) {
            if (conventionClassName != null) {
                throw new IllegalStateException(
                        "You can't declare both [getName(),getContextualName()] and [getDefaultConvention()] methods at the same time, you have to chose only one. Problem occurred in ["
                                + enclosingEnum.getName() + "] class");
            }
        }
        else {
            if (conventionClassName == null) {
                throw new IllegalStateException(
                        "You have to set either [getName()] or [getDefaultConvention()] methods. In case of ["
                                + enclosingEnum.getName() + "] you haven't defined any");
            }
        }
    }

    protected <T> List<T> retrieveEnumValues(JavaSource<?> enclosingJavaSource, MethodSource<?> methodSource,
            EntryEnumConstantReader<?> converter) {
        List<T> result = new ArrayList<>();
        Set<String> enumClassNames = ParsingUtils.readEnumClassNames(methodSource);
        for (String enumClassName : enumClassNames) {
            JavaSource<?> enclosingEnumClass = this.searchHelper.searchReferencingClass(enclosingJavaSource,
                    enumClassName);
            if (enclosingEnumClass == null || !enclosingEnumClass.isEnum()) {
                throw new IllegalStateException("Cannot find enum class with name [" + enumClassName + "]");
            }
            result.addAll(ParsingUtils.retrieveModelsFromEnum((JavaEnumSource) enclosingEnumClass, converter));
        }
        return result;
    }

    /**
     * Check given tags start with the given prefix.
     * @param prefix tag name prefix
     * @param tags tags
     * @param enumName the enum constant
     * @param enclosingClassName the enclosing enum class name
     * @return list of invalid tag messages
     */
    protected List<String> validatePrefixOnTags(String prefix, List<KeyNameEntry> tags, String enumName,
            String enclosingClassName) {
        List<String> messages = new ArrayList<>();
        for (KeyNameEntry tag : tags) {
            String tagName = tag.getName();
            if (!StringUtils.hasText(tagName) || !tagName.startsWith(prefix)) {
                String message = String.format(
                        "\tName <%s> in class <%s> has the following prefix <%s> and following invalid tag keys %s",
                        enumName, enclosingClassName, prefix, tagName);
                messages.add(message);
            }
        }
        return messages;
    }

    protected static class NameInfo {

        private final String name;

        private final String nameOrigin;

        public NameInfo(String name, String nameOrigin) {
            this.name = name;
            this.nameOrigin = nameOrigin;
        }

        public String getName() {
            return this.name;
        }

        public String getNameOrigin() {
            return this.nameOrigin;
        }

    }

}
