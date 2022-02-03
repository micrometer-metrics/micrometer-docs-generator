/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micrometer.docs.spans;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.micrometer.api.instrument.docs.DocumentedObservation;
import io.micrometer.api.instrument.docs.TagKey;
import io.micrometer.api.internal.logging.InternalLogger;
import io.micrometer.api.internal.logging.InternalLoggerFactory;
import io.micrometer.docs.commons.KeyValueEntry;
import io.micrometer.docs.commons.ParsingUtils;
import io.micrometer.tracing.docs.DocumentedSpan;
import io.micrometer.tracing.docs.EventValue;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.CompilationUnit;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Expression;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ImportDeclaration;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MethodDeclaration;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.QualifiedName;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ReturnStatement;
import org.jboss.forge.roaster.model.JavaType;
import org.jboss.forge.roaster.model.JavaUnit;
import org.jboss.forge.roaster.model.impl.JavaEnumImpl;
import org.jboss.forge.roaster.model.source.EnumConstantSource;
import org.jboss.forge.roaster.model.source.MemberSource;

class SpanSearchingFileVisitor extends SimpleFileVisitor<Path> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SpanSearchingFileVisitor.class);

    private final Pattern pattern;

    private final Collection<SpanEntry> spanEntries;

    SpanSearchingFileVisitor(Pattern pattern, Collection<SpanEntry> spanEntries) {
        this.pattern = pattern;
        this.spanEntries = spanEntries;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (!pattern.matcher(file.toString()).matches()) {
            return FileVisitResult.CONTINUE;
        }
        else if (!file.toString().endsWith(".java")) {
            return FileVisitResult.CONTINUE;
        }
        try (InputStream stream = Files.newInputStream(file)) {
            JavaUnit unit = Roaster.parseUnit(stream);
            JavaType myClass = unit.getGoverningType();
            if (!(myClass instanceof JavaEnumImpl)) {
                return FileVisitResult.CONTINUE;
            }
            JavaEnumImpl myEnum = (JavaEnumImpl) myClass;
            if (Stream.of(DocumentedSpan.class.getCanonicalName(), DocumentedObservation.class.getCanonicalName()).noneMatch(ds -> myEnum.getInterfaces().contains(ds))) {
                return FileVisitResult.CONTINUE;
            }
            logger.info("Checking [" + myEnum.getName() + "]");
            if (myEnum.getEnumConstants().size() == 0) {
                return FileVisitResult.CONTINUE;
            }
            for (EnumConstantSource enumConstant : myEnum.getEnumConstants()) {
                SpanEntry entry = parseSpan(enumConstant, myEnum);
                if (entry != null) {
                    if (entry.overridesDefaultSpanFrom != null && entry.tagKeys.isEmpty()) {
                        addTagsFromOverride(file, entry);
                    }
                    if (!entry.additionalTagKeys.isEmpty()) {
                        entry.tagKeys.addAll(entry.additionalTagKeys);
                    }
                    spanEntries.add(entry);
                    logger.info(
                            "Found [" + entry.tagKeys.size() + "] tags and [" + entry.events.size() + "] events");
                }
            }
            return FileVisitResult.CONTINUE;
        }
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        List<String> overridingNames = spanEntries.stream().filter(s -> s.overridesDefaultSpanFrom != null)
                .map(spanEntry -> spanEntry.overridesDefaultSpanFrom.getKey())
                .collect(Collectors.toList());
        List<SpanEntry> spansToRemove = spanEntries.stream()
                .filter(spanEntry -> overridingNames.stream().anyMatch(name -> spanEntry.enclosingClass.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))))
                .collect(Collectors.toList());
        if (!spansToRemove.isEmpty()) {
            logger.info("Will remove the span entries <" + spansToRemove.stream().map(s -> s.name).collect(Collectors.joining(",")) + "> because they are overridden");
        }
        spanEntries.removeAll(spansToRemove);
        return FileVisitResult.CONTINUE;
    }

    // if entry has overridesDefaultSpanFrom - read tags from that thing
    // if entry has overridesDefaultSpanFrom AND getTagKeys() - we pick only the latter
    // if entry has overridesDefaultSpanFrom AND getAdditionalTagKeys() - we pick both
    private void addTagsFromOverride(Path file, SpanEntry entry) throws IOException {
        Map.Entry<String, String> overridesDefaultSpanFrom = entry.overridesDefaultSpanFrom;
        logger.info("Reading additional meta data from [" + overridesDefaultSpanFrom + "]");
        String className = overridesDefaultSpanFrom.getKey();
        File parent = file.getParent().toFile();
        while (!parent.getAbsolutePath().endsWith(File.separator + "java")) {
            parent = parent.getParentFile();
        }
        String filePath = new File(parent, className.replace(".", File.separator) + ".java").getAbsolutePath();
        try (InputStream streamForOverride = Files.newInputStream(new File(filePath).toPath())) {
            JavaUnit parsedForOverride = Roaster.parseUnit(streamForOverride);
            JavaType overrideClass = parsedForOverride.getGoverningType();
            if (!(overrideClass instanceof JavaEnumImpl)) {
                return;
            }
            JavaEnumImpl myEnum = (JavaEnumImpl) overrideClass;
            if (!myEnum.getInterfaces().contains(DocumentedObservation.class.getCanonicalName())) {
                return;
            }
            logger.info("Checking [" + myEnum.getName() + "]");
            if (myEnum.getEnumConstants().size() == 0) {
                return;
            }
            for (EnumConstantSource enumConstant : myEnum.getEnumConstants()) {
                if (!enumConstant.getName().equals(overridesDefaultSpanFrom.getValue())) {
                    continue;
                }
                Collection<KeyValueEntry> low = getTags(enumConstant, myEnum, "getLowCardinalityTagKeys");
                Collection<KeyValueEntry> high = getTags(enumConstant, myEnum, "getHighCardinalityTagKeys");
                entry.tagKeys.addAll(low);
                entry.tagKeys.addAll(high);
            }
        }
    }

    private SpanEntry parseSpan(EnumConstantSource enumConstant, JavaEnumImpl myEnum) {
        List<MemberSource<EnumConstantSource.Body, ?>> members = enumConstant.getBody().getMembers();
        if (members.isEmpty()) {
            return null;
        }
        String name = "";
        String description = enumConstant.getJavaDoc().getText();
        String prefix = "";
        Collection<KeyValueEntry> tags = new TreeSet<>();
        Collection<KeyValueEntry> additionalTagKeys = new TreeSet<>();
        Collection<KeyValueEntry> events = new TreeSet<>();
        Map.Entry<String, String> overridesDefaultSpanFrom = null;
        for (MemberSource<EnumConstantSource.Body, ?> member : members) {
            Object internal = member.getInternal();
            if (!(internal instanceof MethodDeclaration)) {
                return null;
            }
            MethodDeclaration methodDeclaration = (MethodDeclaration) internal;
            String methodName = methodDeclaration.getName().getIdentifier();
            if ("getName".equals(methodName)) {
                name = ParsingUtils.readStringReturnValue(methodDeclaration);
            }
            else if ("getTagKeys".equals(methodName)) {
                tags.addAll(ParsingUtils.keyValueEntries(myEnum, methodDeclaration, TagKey.class));
            }
            else if ("getLowCardinalityTags".equals(methodName)) {
                tags.addAll(ParsingUtils.keyValueEntries(myEnum, methodDeclaration, TagKey.class));
            }
            else if ("getHighCardinalityTags".equals(methodName)) {
                tags.addAll(ParsingUtils.keyValueEntries(myEnum, methodDeclaration, TagKey.class));
            }
            else if ("getAdditionalTagKeys".equals(methodName)) {
                additionalTagKeys.addAll(ParsingUtils.keyValueEntries(myEnum, methodDeclaration, TagKey.class));
            }
            else if ("getEvents".equals(methodName)) {
                events.addAll(ParsingUtils.keyValueEntries(myEnum, methodDeclaration, EventValue.class));
            }
            else if ("getPrefix".equals(methodName)) {
                prefix = ParsingUtils.readStringReturnValue(methodDeclaration);
            }
            else if ("overridesDefaultSpanFrom".equals(methodName)) {
                overridesDefaultSpanFrom = readClassToEnum(methodDeclaration);
            }
        }
        return new SpanEntry(name, myEnum.getCanonicalName(), enumConstant.getName(), description, prefix, tags,
                additionalTagKeys, events, overridesDefaultSpanFrom);
    }

    private Collection<KeyValueEntry> getTags(EnumConstantSource enumConstant, JavaEnumImpl myEnum, String getterName) {
        List<MemberSource<EnumConstantSource.Body, ?>> members = enumConstant.getBody().getMembers();
        if (members.isEmpty()) {
            return Collections.emptyList();
        }
        Collection<KeyValueEntry> tags = new TreeSet<>();
        for (MemberSource<EnumConstantSource.Body, ?> member : members) {
            Object internal = member.getInternal();
            if (!(internal instanceof MethodDeclaration)) {
                return null;
            }
            MethodDeclaration methodDeclaration = (MethodDeclaration) internal;
            String methodName = methodDeclaration.getName().getIdentifier();
            if (getterName.equals(methodName)) {
                tags.addAll(ParsingUtils.keyValueEntries(myEnum, methodDeclaration, TagKey.class));
            }
        }
        return tags;
    }

    private Map.Entry<String, String> readClassToEnum(MethodDeclaration methodDeclaration) {
        Object statement = methodDeclaration.getBody().statements().get(0);
        if (!(statement instanceof ReturnStatement)) {
            logger.warn("Statement [" + statement.getClass() + "] is not a return statement.");
            return null;
        }
        ReturnStatement returnStatement = (ReturnStatement) statement;
        Expression expression = returnStatement.getExpression();
        if (!(expression instanceof QualifiedName)) {
            logger.warn("Statement [" + statement.getClass() + "] is not a qualified name.");
            return null;
        }
        QualifiedName qualifiedName = (QualifiedName) expression;
        String className = qualifiedName.getQualifier().toString();
        String enumName = qualifiedName.getName().toString();
        CompilationUnit compilationUnit = (CompilationUnit) expression.getRoot();
        List imports = compilationUnit.imports();
        String matchingImportStatement = compilationUnit.getPackage().getName().toString() + "." + className;
        for (Object anImport : imports) {
            ImportDeclaration importDeclaration = (ImportDeclaration) anImport;
            String importStatement = importDeclaration.getName().toString();
            if (importStatement.endsWith(className)) {
                matchingImportStatement = importStatement;
            }
        }
        return new AbstractMap.SimpleEntry<>(matchingImportStatement, enumName);
    }

}
