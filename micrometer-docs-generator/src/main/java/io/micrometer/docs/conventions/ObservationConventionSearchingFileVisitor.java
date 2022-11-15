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
package io.micrometer.docs.conventions;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.docs.commons.JavaSourceSearchHelper;
import io.micrometer.observation.GlobalObservationConvention;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaSource;
import org.jboss.forge.roaster.model.source.TypeHolderSource;

class ObservationConventionSearchingFileVisitor extends SimpleFileVisitor<Path> {

    private static final InternalLogger logger = InternalLoggerFactory
            .getInstance(ObservationConventionSearchingFileVisitor.class);

    private final Pattern pattern;

    private final Collection<ObservationConventionEntry> observationConventionEntries;

    private final JavaSourceSearchHelper searchHelper;

    ObservationConventionSearchingFileVisitor(Pattern pattern,
            Collection<ObservationConventionEntry> observationConventionEntries, JavaSourceSearchHelper searchHelper) {
        this.pattern = pattern;
        this.observationConventionEntries = observationConventionEntries;
        this.searchHelper = searchHelper;
    }

    @Override
    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
        if (!pattern.matcher(path.toString()).matches()) {
            return FileVisitResult.CONTINUE;
        }
        else if (!path.toString().endsWith(".java")) {
            return FileVisitResult.CONTINUE;
        }

        logger.debug("Parsing [" + path + "]");
        JavaSource<?> javaSource = Roaster.parse(JavaSource.class, path.toFile());
        List<JavaSource<?>> candidates = getCandidates(javaSource);
        if (candidates.isEmpty()) {
            return FileVisitResult.CONTINUE;
        }

        for (JavaSource<?> candidate : candidates) {
            process(candidate);
        }
        return FileVisitResult.CONTINUE;
    }

    private List<JavaSource<?>> getCandidates(JavaSource<?> javaSource) {
        List<JavaSource<?>> candidates = new ArrayList<>();
        candidates.add(javaSource);
        if (javaSource instanceof TypeHolderSource) {
            for (JavaSource<?> nestedType : ((TypeHolderSource<?>) javaSource).getNestedTypes()) {
                if (nestedType.isClass()) {
                    candidates.add(nestedType);
                }
            }
        }
        return candidates;
    }

    private void process(JavaSource<?> javaSource) {
        String interfaceName = this.searchHelper.searchObservationConventionInterfaceName(javaSource);
        if (interfaceName == null) {
            return;
        }
        // the returned interface name is canonical name with generics.
        // e.g. "io.micrometer.observation.ObservationConvention<Observation.Context>"
        boolean isGlobal = interfaceName.startsWith(GlobalObservationConvention.class.getName());
        String canonicalName = javaSource.getCanonicalName();
        Pattern classPattern = Pattern.compile("^.*ObservationConvention<(.*)>$");
        String conventionContextName = contextClassName(classPattern, interfaceName);

        if (isGlobal) {
            this.observationConventionEntries.add(new ObservationConventionEntry(canonicalName,
                    ObservationConventionEntry.Type.GLOBAL, conventionContextName));
        }
        else {
            this.observationConventionEntries.add(new ObservationConventionEntry(canonicalName,
                    ObservationConventionEntry.Type.LOCAL, conventionContextName));
        }
    }

    private String contextClassName(Pattern classPattern, String anInterface) {
        Matcher matcher = classPattern.matcher(anInterface);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        if (!anInterface.contains("<") && !anInterface.contains(">")) {
            return "n/a";
        }
        return "";
    }

}
