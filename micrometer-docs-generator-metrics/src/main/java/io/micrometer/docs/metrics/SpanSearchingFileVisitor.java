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

package io.micrometer.docs.metrics;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

import io.micrometer.api.instrument.docs.DocumentedSample;
import io.micrometer.api.instrument.docs.TagKey;
import io.micrometer.api.internal.logging.InternalLogger;
import io.micrometer.api.internal.logging.InternalLoggerFactory;
import io.micrometer.docs.commons.KeyValueEntry;
import io.micrometer.docs.commons.ParsingUtils;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MethodDeclaration;
import org.jboss.forge.roaster.model.JavaType;
import org.jboss.forge.roaster.model.JavaUnit;
import org.jboss.forge.roaster.model.impl.JavaEnumImpl;
import org.jboss.forge.roaster.model.source.EnumConstantSource;
import org.jboss.forge.roaster.model.source.MemberSource;

class SpanSearchingFileVisitor extends SimpleFileVisitor<Path> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SpanSearchingFileVisitor.class);

    private final Pattern pattern;

    private final Collection<SampleEntry> spanEntries;

    SpanSearchingFileVisitor(Pattern pattern, Collection<SampleEntry> spanEntries) {
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
            if (!myEnum.getInterfaces().contains(DocumentedSample.class.getCanonicalName())) {
                return FileVisitResult.CONTINUE;
            }
            logger.info("Checking [" + myEnum.getName() + "]");
            if (myEnum.getEnumConstants().size() == 0) {
                return FileVisitResult.CONTINUE;
            }
            for (EnumConstantSource enumConstant : myEnum.getEnumConstants()) {
                SampleEntry entry = parseSpan(enumConstant, myEnum);
                if (entry != null) {
                    spanEntries.add(entry);
                    logger.info(
                            "Found [" + entry.lowCardinalityTagKeys.size() + "] low cardinality tags and [" + entry.highCardinalityTagKeys.size() + "] high cardinality tags");
                }
            }
            return FileVisitResult.CONTINUE;
        }
    }

    private SampleEntry parseSpan(EnumConstantSource enumConstant, JavaEnumImpl myEnum) {
        List<MemberSource<EnumConstantSource.Body, ?>> members = enumConstant.getBody().getMembers();
        if (members.isEmpty()) {
            return null;
        }
        String name = "";
        String description = enumConstant.getJavaDoc().getText();
        String prefix = "";
        Collection<KeyValueEntry> tags = new TreeSet<>();
        Collection<KeyValueEntry> events = new TreeSet<>();
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
            else if ("getLowCardinalityTagKeys".equals(methodName)) {
                tags.addAll(ParsingUtils.keyValueEntries(myEnum, methodDeclaration, TagKey.class));
            }
            else if ("getHighCardinalityTagKeys".equals(methodName)) {
                events.addAll(ParsingUtils.keyValueEntries(myEnum, methodDeclaration, TagKey.class));
            }
            else if ("prefix".equals(methodName)) {
                prefix = ParsingUtils.readStringReturnValue(methodDeclaration);
            }
        }
        return new SampleEntry(name, myEnum.getCanonicalName(), enumConstant.getName(), description, prefix, tags,
                events);
    }

}
