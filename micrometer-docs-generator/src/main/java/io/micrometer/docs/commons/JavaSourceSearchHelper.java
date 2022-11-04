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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.docs.commons.utils.Assert;
import io.micrometer.observation.GlobalObservationConvention;
import io.micrometer.observation.ObservationConvention;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Expression;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.QualifiedName;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.SimpleName;
import org.jboss.forge.roaster.model.Extendable;
import org.jboss.forge.roaster.model.InterfaceCapable;
import org.jboss.forge.roaster.model.source.EnumConstantSource;
import org.jboss.forge.roaster.model.source.Import;
import org.jboss.forge.roaster.model.source.JavaEnumSource;
import org.jboss.forge.roaster.model.source.JavaSource;
import org.jboss.forge.roaster.model.source.MethodHolderSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.jboss.forge.roaster.model.source.TypeHolderSource;

/**
 * Helper class to search any {@link JavaSource} from java files under specified
 * directory.
 *
 * @author Tadaya Tsuyukubo
 */
public class JavaSourceSearchHelper {

    // @formatter:off
    //
    // Java Class Names:
    //   Qualified Name: "io.micrometer.Foo", "io.micrometer.Foo$Bar$Baz"
    //   Canonical Name: "io.micrometer.Foo", "io.micrometer.Foo.Bar.Baz"
    //   Simple Name: "Foo", "Bar", "Baz"
    //
    // Note: Qualified name is unique in a class loader. In rare case, same canonical name
    // can be created from different qualified names.
    //
    // @formatter:on

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(JavaSourceSearchHelper.class);

    /**
     * This map holds keys with enclosing classes and their nested classes using qualified
     * name. <pre>
     * Example:
     *    key: io.micrometer.Foo  (enclosing class in "io/micrometer/Foo.java" file)
     *    key: io.micrometer.Foo$Bar (nested class in "io/micrometer/Foo.java" file)
     * </pre>
     */
    private final Map<String, JavaSourcePathInfo> pathInfoMap;

    /**
     * Canonical class name to qualified class names. (note: in rare case, different
     * qualified name can be same canonical name. (ref <a href=
     * "https://docs.oracle.com/javase/specs/jls/se11/html/jls-6.html#jls-6.7">Java
     * Spec</a>) This map is useful for resolving import statement referenced classes
     * since they use canonical names.
     */
    private final Map<String, Set<String>> qualifiedClassNames = new HashMap<>();

    public static JavaSourceSearchHelper create(Path projectRoot, Pattern inclusionPattern) {
        PathCollectingFileVisitor visitor = new PathCollectingFileVisitor(inclusionPattern);
        try {
            long before = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            Files.walkFileTree(projectRoot, visitor);
            long after = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            logger.debug("Memory usage: before{}, after={}, diff={}", before, after, after - before);
        }
        catch (IOException ex) {
            throw new RuntimeException("Failed to parse java files.", ex);
        }
        return create(visitor.getMap());
    }

    static JavaSourceSearchHelper create(Map<String, JavaSourcePathInfo> map) {
        return new JavaSourceSearchHelper(map);
    }

    private JavaSourceSearchHelper(Map<String, JavaSourcePathInfo> pathInfoMap) {
        this.pathInfoMap = pathInfoMap;

        // compose canonical name to qualified name map
        for (Entry<String, JavaSourcePathInfo> entry : pathInfoMap.entrySet()) {
            String qualifiedName = entry.getKey();
            String canonicalName = entry.getValue().canonicalName;

            this.qualifiedClassNames.compute(canonicalName, (key, set) -> {
                if (set == null) {
                    return Collections.singleton(qualifiedName);
                }
                // rare case, one canonical name has multiple qualified names
                Set<String> newSet = new HashSet<>(set);
                newSet.add(qualifiedName);
                return newSet;
            });
        }
    }

    /**
     * Search a {@link JavaSource} by qualified class name.
     * @param qualifiedName a qualified class name
     * @return matched {@link JavaSource} or {@code null} if not found.
     */
    @Nullable
    public JavaSource<?> search(String qualifiedName) {
        boolean isGeneric = qualifiedName.contains("<");
        String resolvedName = qualifiedName;
        if (isGeneric) { // TODO: need to handle generics here??
            resolvedName = qualifiedName.substring(0, qualifiedName.indexOf("<"));
        }

        JavaSourcePathInfo info = this.pathInfoMap.get(resolvedName);
        if (info == null) {
            return null;
        }
        return info.getJavaSource();
    }

    /**
     * Search the class which is referenced by the enclosing class.
     * @param enclosingJavaSource enclosing java class source
     * @param className search target class name. This cannot be an enum constant name.
     * @return matched java source or {@code null}
     */
    @Nullable
    public JavaSource<?> searchReferencingClass(JavaSource<?> enclosingJavaSource, String className) {
        // the given classname could be:
        // - qualified name: "io.micrometer.Foo$Bar$Baz"
        // - canonical name: "io.micrometer.Foo.Bar.Baz"
        // - roaster TypeLiteral: "Foo.Bar"
        // - simple name: "Foo", "Bar", "Baz"
        // - with generics(?)
        // - array (?)

        // for qualified name
        JavaSource<?> javaSource = search(className);
        if (javaSource != null) {
            return javaSource;
        }

        // Roaster behavior. when nested class is referenced as "Foo.Bar.class", the
        // TypeLiteral becomes "Foo.Bar" instead of the proper qualified name
        // "io.micrometer.Foo$Bar". In this case, convert the target class name to a
        // simple name "Bar".
        String enclosingClassSimpleName = enclosingJavaSource.getName();
        if (className.startsWith(enclosingClassSimpleName + ".")) {
            className = className.substring(enclosingClassSimpleName.length() + 1);
        }

        // search in nested classes
        if (enclosingJavaSource instanceof TypeHolderSource) {
            for (JavaSource<?> nestedType : ((TypeHolderSource<?>) enclosingJavaSource).getNestedTypes()) {
                String simpleName = nestedType.getName();
                String canonicalName = nestedType.getCanonicalName();
                String qualifiedName = nestedType.getQualifiedName();
                if (className.equals(simpleName) || className.equals(canonicalName)
                        || className.equals(qualifiedName)) {
                    return nestedType;
                }

                // recursively check nested class
                JavaSource<?> source = searchReferencingClass(nestedType, className);
                if (source != null) {
                    return source;
                }
            }
        }

        // search from import
        for (Import anImport : enclosingJavaSource.getImports()) {
            if (anImport.isStatic()) {
                continue; // for class reference, static import doesn't apply
            }
            if (anImport.isWildcard()) {
                // search the package
                JavaSource<?> match = searchWithinPackage(anImport.getPackage(), className);
                if (match != null) {
                    return match;
                }
            }
            else {
                if (anImport.getQualifiedName().equals(className) || anImport.getSimpleName().equals(className)) {
                    return search(anImport.getQualifiedName());
                }
            }
        }

        // search within the same package
        String packageName = enclosingJavaSource.getPackage();
        JavaSource<?> match = searchWithinPackage(packageName, className);
        if (match != null) {
            return match;
        }

        return null;
    }

    @Nullable
    private JavaSource<?> searchWithinPackage(String packageName, String className) {
        // TODO: do we want to handle empty package case? (top level classes)
        if ("".equals(packageName)) {
            return search(className);
        }

        // TODO: currently only considering className as simple classname
        // TODO: inefficient algorithm for now.
        for (String qualifiedName : this.pathInfoMap.keySet()) {
            if (!qualifiedName.contains(".")) {
                continue; // exclude top level classes
            }
            // when target is a nested class, only look for the nested class entries
            if (className.contains("$") && !qualifiedName.contains("$")) {
                continue;
            }

            int index = qualifiedName.lastIndexOf(".");
            String entryPackage = qualifiedName.substring(0, index);
            String entryClassName = qualifiedName.substring(index + 1);
            if (packageName.equals(entryPackage) && entryClassName.equals(className)) {
                return search(qualifiedName);
            }
        }
        return null;
    }

    /**
     * Search an enum constant referenced by the enclosing class.
     * @param enclosingJavaSource enclosing class {@link JavaSource}.
     * @param expression target enum constant. This can be {@link QualifiedName}, such as
     * {@code MyEnum.FOO} or {@link SimpleName}, such as {@code FOO} for static imported
     * one.
     * @return an enum constant source or {@code null} if not found.
     */
    @Nullable
    public EnumConstantSource searchReferencingEnumConstant(JavaSource<?> enclosingJavaSource, Expression expression) {
        if (expression instanceof QualifiedName) {
            // e.g. MyEnum.FOO
            QualifiedName qualifiedName = (QualifiedName) expression;
            String qualifier = qualifiedName.getQualifier().getFullyQualifiedName(); // e.g.
                                                                                     // MyEnum
            String enumValue = qualifiedName.getName().getIdentifier();
            JavaSource<?> javaSource = searchReferencingClass(enclosingJavaSource, qualifier);
            if (javaSource == null) {
                return null;
            }
            if (!javaSource.isEnum()) {
                return null;
            }
            return ((JavaEnumSource) javaSource).getEnumConstant(enumValue);
        }
        else if (expression instanceof SimpleName) {
            // The enum doesn't have qualifier, which means it is static imported. e.g.
            // FOO
            String enumConstantName = ((SimpleName) expression).getIdentifier();

            // search from import
            for (Import anImport : enclosingJavaSource.getImports()) {
                if (!anImport.isStatic()) {
                    continue; // enum must be static imported
                }
                if (anImport.isWildcard()) {
                    // getPackage() returns "package name" + "enum class name" in this
                    // case
                    String enumClassCanonicalName = anImport.getPackage();
                    JavaSource<?> javaSource = searchByCanonicalName(enumClassCanonicalName);
                    if (javaSource == null || !javaSource.isEnum()) {
                        // possibly other static import
                        continue;
                    }
                    EnumConstantSource enumConstant = ((JavaEnumSource) javaSource).getEnumConstant(enumConstantName);
                    if (enumConstant != null) {
                        return enumConstant;
                    }
                }
                else if (anImport.getSimpleName().equals(enumConstantName)) {
                    JavaSource<?> javaSource = searchByCanonicalName(anImport.getPackage());
                    // TODO: continue or fail here
                    if (javaSource == null || !javaSource.isEnum()) {
                        continue;
                    }
                    EnumConstantSource enumConstant = ((JavaEnumSource) javaSource).getEnumConstant(enumConstantName);
                    if (enumConstant != null) {
                        return enumConstant;
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    private JavaSource<?> searchByCanonicalName(String canonicalName) {
        Set<String> qualifiedNames = this.qualifiedClassNames.get(canonicalName);
        if (qualifiedNames == null) {
            return null;
        }
        // TODO: add warning when multiple qualified names found
        for (String qualifiedName : qualifiedNames) {
            JavaSource<?> javaSource = search(qualifiedName);
            if (javaSource == null) {
                continue;
            }
            return javaSource;
        }
        return null;
    }

    /**
     * Search the method source in the hierarchy(parents/interfaces) of the given
     * {@link JavaSource}.
     * @param javaSource a {@link JavaSource} to search from
     * @param methodName target method name
     * @return found {@link MethodSource} or {@code null} if not found
     */
    @Nullable
    public MethodSource<?> searchMethodSource(JavaSource<?> javaSource, String methodName) {
        // discovery - DFS to look for itself, parent and interfaces classes

        Assert.isInstanceOf(MethodHolderSource.class, javaSource);
        MethodSource<?> methodSource = ((MethodHolderSource<?>) javaSource).getMethod(methodName);
        if (methodSource != null) {
            return methodSource;
        }

        if (javaSource instanceof Extendable) {
            String parentClassName = ((Extendable<?>) javaSource).getSuperType();
            if (!Object.class.getName().equals(parentClassName)) {
                JavaSource<?> parentSource = searchJavaSourceByRoasterTypeName(javaSource, parentClassName);
                if (parentSource != null) {
                    methodSource = searchMethodSource(parentSource, methodName);
                }
                if (methodSource != null) {
                    return methodSource;
                }
            }
        }

        // search for interfaces
        if (javaSource instanceof InterfaceCapable) {
            List<String> interfaces = ((InterfaceCapable) javaSource).getInterfaces();
            for (String interfaceName : interfaces) {
                JavaSource<?> interfaceSource = searchJavaSourceByRoasterTypeName(javaSource, interfaceName);
                if (interfaceSource != null) {
                    methodSource = searchMethodSource(interfaceSource, methodName);
                }
                if (methodSource != null) {
                    return methodSource;
                }
            }
        }
        return null;
    }

    @Nullable
    private JavaSource<?> searchJavaSourceByRoasterTypeName(JavaSource<?> enclosingJavaSource, String typeName) {
        // @formatter:off

        // Roaster behavior for getting a parent class type("getSuperType()") and
        // an interface ("getInterfaces()"):
        //
        // 1) When the parent class is a nested class, Roaster returns package + "." +
        //    simple-name. For example, when "class Bar extends Foo", it returns
        //    "io.micrometer.Bar" instead of the proper qualified name
        //    "io.micrometer.Foo$Bar".
        // 2) Based on how parent/interface class is specified, the returning value of
        //    "getSuperType()" or "getInterfaces()" changes.
        //
        // In the following examples, "barJavaSource.getSuperType()" returns differ:
        //
        // [example]
        // package io.micrometer
        // class Container {
        //   static class Foo {}
        //   static class Bar extends Foo {}
        // }
        //
        // The return value is "io.micrometer.Foo"
        //
        // [example]
        // package io.micrometer
        // class Container {
        //   static class Foo {}
        //   static class Bar extends Container.Foo {}   // <== see
        // }
        // The return is "Container.Foo"
        //
        // [example]
        // package io.micrometer
        // class Container {
        //   static class Foo {}
        //   static class Bar extends io.micrometer.Container.Foo {}   // <== see
        // }
        // The return is "io.micrometer.Container.Foo" (same as canonical name)
        //
        // @formatter:on

        // Due to the different return values, this method creates multiple candidate
        // target class names, then check each of them.
        List<String> candidateNames = new ArrayList<>();
        candidateNames.add(typeName);

        String packageName = enclosingJavaSource.getPackage();
        if (typeName.contains(packageName)) {
            String targetClassName = typeName.substring(0, packageName.length());
            String qualifiedName = enclosingJavaSource.getQualifiedName() + "$" + targetClassName;
            candidateNames.add(targetClassName);
            candidateNames.add(qualifiedName);
        }
        for (String candidate : candidateNames) {
            JavaSource<?> javaSource = searchReferencingClass(enclosingJavaSource, candidate);
            if (javaSource != null) {
                return javaSource;
            }
        }
        return null;
    }

    /**
     * Hierarchically search the implementing name of {@link ObservationConvention} or
     * {@link GlobalObservationConvention}.
     * <p>
     * NOTE: the observation convention has generics and returning name will contain the
     * generics information.
     * @param javaSource enclosing java source
     * @return name of the convention class with generics. (e.g.
     * "io.micrometer.observation.ObservationConvention&lt;KafkaRecordReceiverContext&gt;")
     */
    @Nullable
    public String searchObservationConventionInterfaceName(JavaSource<?> javaSource) {
        return searchObservationConventionInterfaceName(javaSource, new HashSet<>());
    }

    @Nullable
    private String searchObservationConventionInterfaceName(JavaSource<?> javaSource, Set<String> visited) {
        String qualifiedName = javaSource.getQualifiedName();
        logger.trace("Searching ObservationConvention on {} - start", qualifiedName);

        if (visited.contains(qualifiedName)) {
            return null; // already visited but not found
        }
        visited.add(qualifiedName);

        // search on interfaces and parent interfaces
        if (javaSource instanceof InterfaceCapable) {
            List<String> interfaces = ((InterfaceCapable) javaSource).getInterfaces();
            for (String interfaceName : interfaces) {
                if (interfaceName.contains(ObservationConvention.class.getCanonicalName())
                        || interfaceName.contains(GlobalObservationConvention.class.getCanonicalName())) {
                    return interfaceName;
                }

                JavaSource<?> interfaceSource = searchJavaSourceByRoasterTypeName(javaSource, interfaceName);
                if (interfaceSource != null) {
                    String result = searchObservationConventionInterfaceName(interfaceSource, visited);
                    if (result != null) {
                        return result;
                    }
                }
            }
        }

        // search on nested classes
        if (javaSource instanceof TypeHolderSource) {
            for (JavaSource<?> nested : ((TypeHolderSource<?>) javaSource).getNestedTypes()) {
                String result = searchObservationConventionInterfaceName(nested, visited);
                if (result != null) {
                    return result;
                }
            }
        }

        // search on parent classes
        if (javaSource instanceof Extendable) {
            String parentClassName = ((Extendable<?>) javaSource).getSuperType();
            if (!Object.class.getName().equals(parentClassName)) {
                JavaSource<?> parentSource = searchJavaSourceByRoasterTypeName(javaSource, parentClassName);
                if (parentSource != null) {
                    String result = searchObservationConventionInterfaceName(parentSource, visited);
                    if (result != null) {
                        return result;
                    }
                }
            }
        }

        logger.trace("Searching ObservationConvention on {} - not found", qualifiedName);
        return null; // not found
    }

    static class PathCollectingFileVisitor extends SimpleFileVisitor<Path> {

        private final Pattern pattern;

        private final Map<String, JavaSourcePathInfo> map = new HashMap<>();

        PathCollectingFileVisitor(Pattern pattern) {
            this.pattern = pattern;
        }

        @Override
        public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
            if (!pattern.matcher(path.toString()).matches()) {
                return FileVisitResult.CONTINUE;
            }
            else if (!path.toString().endsWith(".java")) {
                return FileVisitResult.CONTINUE;
            }
            else if (path.toString().endsWith("package-info.java") || path.toString().endsWith("module-info.java")) {
                return FileVisitResult.CONTINUE;
            }

            JavaSource<?> javaSource = Roaster.parse(JavaSource.class, path.toFile());

            // Because of the equality in JavaClassImpl, nested classes under the same
            // enclosing class are considered equal. Therefore, here needs to use List
            // to keep each nested class as an independent entry.
            List<JavaSource<?>> sources = new ArrayList<>();
            populateJavaSource(javaSource, sources);

            // To reduce memory usage, for now just putting path instead of JavaSource.
            // May consider using WeakHashMap with proper key.
            for (JavaSource<?> source : sources) {
                String canonicalName = source.getCanonicalName();
                String qualifiedName = source.getQualifiedName();
                String simpleName = source.getName();
                this.map.put(qualifiedName, new JavaSourcePathInfo(path, canonicalName, qualifiedName, simpleName));
            }
            return FileVisitResult.CONTINUE;
        }

        public Map<String, JavaSourcePathInfo> getMap() {
            return this.map;
        }

        // recursively populate nested classes
        private void populateJavaSource(JavaSource<?> javaSource, List<JavaSource<?>> list) {
            list.add(javaSource);
            if (javaSource instanceof TypeHolderSource) {
                for (JavaSource<?> nested : ((TypeHolderSource<?>) javaSource).getNestedTypes()) {
                    populateJavaSource(nested, list);
                }
            }
        }

    }

    static class JavaSourcePathInfo {

        final Path path;

        // e.g. "io.micrometer.Foo.Bar.Baz"
        final String canonicalName;

        // e.g. "io.micrometer.Foo$Bar$Baz"
        final String qualifiedName;

        // e.g. "Foo", "Baz"
        final String simpleName;

        public JavaSourcePathInfo(Path path, String canonicalName, String qualifiedName, String simpleName) {
            this.path = path;
            this.canonicalName = canonicalName;
            this.qualifiedName = qualifiedName;
            this.simpleName = simpleName;
        }

        JavaSource<?> getJavaSource() {
            JavaSource<?> javaSource;
            try {
                javaSource = Roaster.parse(JavaSource.class, this.path.toFile());
            }
            catch (IOException ex) {
                throw new RuntimeException("Failed to parse " + this.path, ex);
            }
            JavaSource<?> result = findJavaSource(javaSource, this.qualifiedName);
            if (result == null) {
                throw new RuntimeException(String.format("Could not find %s in %s", this.qualifiedName, this.path));
            }
            return result;
        }

        // TODO: may move to appropriate place
        @Nullable
        private JavaSource<?> findJavaSource(JavaSource<?> javaSource, String qualifiedName) {
            if (javaSource.getQualifiedName().equals(qualifiedName)) {
                return javaSource;
            }
            if (javaSource instanceof TypeHolderSource) {
                for (JavaSource<?> nested : ((TypeHolderSource<?>) javaSource).getNestedTypes()) {
                    JavaSource<?> result = findJavaSource(nested, qualifiedName);
                    if (result != null) {
                        return result;
                    }
                }
            }
            return null;
        }

    }

}
