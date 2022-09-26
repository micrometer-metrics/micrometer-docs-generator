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

package io.micrometer.docs.commons;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.micrometer.common.docs.KeyName;
import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.docs.commons.utils.AsciidocUtils;
import io.micrometer.observation.ObservationConvention;
import org.jboss.forge.roaster.Internal;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.CompilationUnit;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Expression;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ImportDeclaration;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MethodDeclaration;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MethodInvocation;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.QualifiedName;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ReturnStatement;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.StringLiteral;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Type;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.TypeLiteral;
import org.jboss.forge.roaster.model.JavaType;
import org.jboss.forge.roaster.model.JavaUnit;
import org.jboss.forge.roaster.model.impl.AbstractJavaSource;
import org.jboss.forge.roaster.model.impl.JavaClassImpl;
import org.jboss.forge.roaster.model.impl.JavaEnumImpl;
import org.jboss.forge.roaster.model.impl.JavaUnitImpl;
import org.jboss.forge.roaster.model.impl.MethodImpl;
import org.jboss.forge.roaster.model.source.EnumConstantSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.JavaSource;
import org.jboss.forge.roaster.model.source.MemberSource;
import org.jboss.forge.roaster.model.source.MethodSource;

public class ParsingUtils {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ParsingUtils.class);

    public static void updateKeyValuesFromEnum(JavaEnumImpl parentEnum, JavaSource<?> source, Class<?> requiredClass,
            Collection<KeyValueEntry> keyValues, @Nullable String methodName) {
        if (!(source instanceof JavaEnumImpl)) {
            return;
        }
        JavaEnumImpl myEnum = (JavaEnumImpl) source;

        // Based on how interfaces are implemented in enum, "myEnum.getInterfaces()" has different values.
        // For example, "MyEnum" implements "Observation.Event" interface as:
        //  - "enum MyEnum implements Observation.Event {"
        //      "getInterfaces()" returns ["Observation.Event"]
        //  - "enum MyEnum implements Event {"
        //      "getInterfaces()" returns ["io.micrometer.observation.Observation.Event"]
        //
        // To make both cases work, use the simple name("Event" in the above example) for comparison.
        if (!myEnum.hasInterface(requiredClass.getSimpleName())) {
            return;
        }
        logger.debug("Checking [" + parentEnum.getName() + "." + myEnum.getName() + "]");
        if (myEnum.getEnumConstants().size() == 0) {
            return;
        }
        for (EnumConstantSource enumConstant : myEnum.getEnumConstants()) {
            String keyValue = enumKeyValue(enumConstant, methodName);
            keyValues.add(new KeyValueEntry(keyValue, AsciidocUtils.javadocToAsciidoc(enumConstant.getJavaDoc())));
        }
    }

    public static String readStringReturnValue(MethodDeclaration methodDeclaration) {
        return stringFromReturnMethodDeclaration(methodDeclaration);
    }

    @Nullable
    public static String tryToReadStringReturnValue(Path file, String clazz) {
        try {
            return tryToReadNameFromConventionClass(file, clazz);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String tryToReadNameFromConventionClass(Path file, String className) {
        File parent = file.getParent().toFile();
        while (!parent.getAbsolutePath().endsWith(File.separator + "java")) { // TODO: Works only for Java
            parent = parent.getParentFile();
        }
        String filePath = filePath(className, parent);
        try (InputStream streamForOverride = Files.newInputStream(new File(filePath).toPath())) {
            JavaUnit parsedClass = Roaster.parseUnit(streamForOverride);
            JavaType actualConventionImplementation;
            if (className.contains("$")) {
                String actualName = className.substring(className.indexOf("$") + 1);
                List<AbstractJavaSource> nestedTypes = ((AbstractJavaSource) parsedClass.getGoverningType()).getNestedTypes();
                Object foundType = nestedTypes.stream().filter(o -> (o).getName().equals(actualName)).findFirst().orElseThrow(() -> new IllegalStateException("Can't find a class with fqb [" + className + "]"));
                actualConventionImplementation = (JavaType) foundType;
            } else if (parsedClass instanceof JavaUnitImpl) {
                actualConventionImplementation = parsedClass.getGoverningType();
            } else {
                return null;
            }
            if (actualConventionImplementation instanceof JavaClassImpl) {
                List<String> interfaces = ((JavaClassImpl) actualConventionImplementation).getInterfaces();
                if (interfaces.stream().noneMatch(s -> s.contains(ObservationConvention.class.getSimpleName()))) {
                    return null;
                }
                MethodSource<JavaClassSource> name = ((JavaClassImpl) actualConventionImplementation).getMethod("getName");
                try {
                    MethodDeclaration methodDeclaration = (MethodDeclaration) Arrays.stream(MethodImpl.class.getDeclaredFields()).filter(f -> f.getName().equals("method")).findFirst().map(f -> {
                        try {
                            f.setAccessible(true);
                            return f.get(name);
                        }
                        catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    }).get();
                    return ParsingUtils.readStringReturnValue(methodDeclaration);
                } catch (Exception ex) {
                    return name.toString().replace("return ", "").replace("\"", "");
                }
            }

        }
        catch (Throwable e) {
            throw new RuntimeException(e);
        }
        return "";
    }

    private static String filePath(String className, File parent) {
        if (className.contains("$")) {
            return new File(parent, className.replace(".", File.separator).substring(0, className.indexOf("$")) + ".java").getAbsolutePath();
        }
        return new File(parent, className.replace(".", File.separator) + ".java").getAbsolutePath();
    }

    public static Collection<KeyValueEntry> keyValueEntries(JavaEnumImpl myEnum, MethodDeclaration methodDeclaration,
            Class requiredClass) {
        return keyValueEntries(myEnum, methodDeclaration, requiredClass, null);
    }

    public static Collection<KeyValueEntry> keyValueEntries(JavaEnumImpl myEnum, MethodDeclaration methodDeclaration,
            Class requiredClass, @Nullable String methodName) {
        Collection<String> enumNames = readClassValue(methodDeclaration);
        Collection<KeyValueEntry> keyValues = new TreeSet<>();
        enumNames.forEach(enumName -> {
            List<JavaSource<?>> nestedTypes = myEnum.getNestedTypes();
            JavaSource<?> nestedSource = nestedTypes.stream()
                    .filter(javaSource -> javaSource.getName().equals(enumName)).findFirst().orElseThrow(
                            () -> new IllegalStateException("There's no nested type with name [" + enumName + "]"));
            ParsingUtils.updateKeyValuesFromEnum(myEnum, nestedSource, requiredClass, keyValues, methodName);
        });
        return keyValues;
    }

    public static Collection<String> readClassValue(MethodDeclaration methodDeclaration) {
        Object statement = methodDeclaration.getBody().statements().get(0);
        if (!(statement instanceof ReturnStatement)) {
            logger.warn("Statement [" + statement.getClass() + "] is not a return statement.");
            return Collections.emptyList();
        }
        ReturnStatement returnStatement = (ReturnStatement) statement;
        Expression expression = returnStatement.getExpression();
        if (!(expression instanceof MethodInvocation)) {
            logger.warn("Statement [" + statement.getClass() + "] is not a method invocation.");
            return Collections.emptyList();
        }
        MethodInvocation methodInvocation = (MethodInvocation) expression;
        if ("merge".equals(methodInvocation.getName().getIdentifier())) {
            // TODO: There must be a better way to do this...
            // KeyName.merge(TestSpanTags.values(),AsyncSpanTags.values())
            String invocationString = methodInvocation.toString();
            Matcher matcher = Pattern.compile("([a-zA-Z]+.values)").matcher(invocationString);
            Collection<String> classNames = new TreeSet<>();
            while (matcher.find()) {
                String className = matcher.group(1).split("\\.")[0];
                classNames.add(className);
            }
            return classNames;
        }
        else if (!methodInvocation.toString().endsWith(".values()")) {
            throw new IllegalStateException("You have to use the static .values() method on the enum that implements "
                    + KeyName.class + " interface or use [KeyName.merge(...)] method to merge multiple values from tags");
        }
        // will return Tags
        return Collections.singletonList(methodInvocation.getExpression().toString());
    }

    private static String enumKeyValue(EnumConstantSource enumConstant, @Nullable String methodName) {
        List<MemberSource<EnumConstantSource.Body, ?>> members = enumConstant.getBody().getMembers();
        if (members.isEmpty()) {
            logger.warn("No method declarations in the enum.");
            return "";
        }
        Object internal;
        if (methodName == null) {
            internal = members.get(0).getInternal();
        } else {
            internal = members.stream().filter(bodyMemberSource -> bodyMemberSource.getName().equals(methodName)).findFirst().map(Internal::getInternal).orElse(null);
            if (internal == null) {
                logger.warn("Can't find the member with method name [" + methodName + "] on " + enumConstant.getName());
                return "";
            }
        }
        if (!(internal instanceof MethodDeclaration)) {
            logger.warn("Can't read the member [" + internal.getClass() + "] as a method declaration.");
            return "";
        }
        MethodDeclaration methodDeclaration = (MethodDeclaration) internal;
        if (methodDeclaration.getBody().statements().isEmpty()) {
            logger.warn("Body was empty. Continuing...");
            return "";
        }
        return stringFromReturnMethodDeclaration(methodDeclaration);
    }

    private static String stringFromReturnMethodDeclaration(MethodDeclaration methodDeclaration) {
        Object statement = methodDeclaration.getBody().statements().get(0);
        if (!(statement instanceof ReturnStatement)) {
            logger.warn("Statement [" + statement.getClass() + "] is not a return statement.");
            return "";
        }
        ReturnStatement returnStatement = (ReturnStatement) statement;
        Expression expression = returnStatement.getExpression();
        if (!(expression instanceof StringLiteral)) {
            logger.warn("Statement [" + statement.getClass() + "] is not a string literal statement.");
            return "";
        }
        return ((StringLiteral) expression).getLiteralValue();
    }

    @SuppressWarnings("unchecked")
    public static <T extends Enum> T enumFromReturnMethodDeclaration(MethodDeclaration methodDeclaration, Class<T> enumClass) {
        Object statement = methodDeclaration.getBody().statements().get(0);
        if (!(statement instanceof ReturnStatement)) {
            logger.warn("Statement [" + statement.getClass() + "] is not a return statement.");
            return null;
        }
        ReturnStatement returnStatement = (ReturnStatement) statement;
        Expression expression = returnStatement.getExpression();
        if (!(expression instanceof QualifiedName)) {
            logger.warn("Statement [" + statement.getClass() + "] is not a qualified statement.");
            return null;
        }
        QualifiedName qualifiedName = (QualifiedName) expression;
        String enumName = qualifiedName.getName().toString();
        return (T) Enum.valueOf(enumClass, enumName);
    }

    public static String readClass(MethodDeclaration methodDeclaration) {
        Object statement = methodDeclaration.getBody().statements().get(0);
        if (!(statement instanceof ReturnStatement)) {
            logger.warn("Statement [" + statement.getClass() + "] is not a return statement.");
            return null;
        }
        ReturnStatement returnStatement = (ReturnStatement) statement;
        Expression expression = returnStatement.getExpression();
        if (!(expression instanceof TypeLiteral)) {
            logger.warn("Statement [" + statement.getClass() + "] is not a qualified name.");
            return null;
        }
        TypeLiteral typeLiteral = (TypeLiteral) expression;
        Type type = typeLiteral.getType();
        String className = type.toString();
        return matchingImportStatement(expression, className);
    }

    public static Map.Entry<String, String> readClassToEnum(MethodDeclaration methodDeclaration) {
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
        String matchingImportStatement = matchingImportStatement(expression, className);
        return new AbstractMap.SimpleEntry<>(matchingImportStatement, enumName);
    }

    private static String matchingImportStatement(Expression expression, String className) {
        CompilationUnit compilationUnit = (CompilationUnit) expression.getRoot();
        List imports = compilationUnit.imports();
        // Class is in the same package
        String matchingImportStatement = compilationUnit.getPackage().getName().toString() + "." + className;
        for (Object anImport : imports) {
            ImportDeclaration importDeclaration = (ImportDeclaration) anImport;
            String importStatement = importDeclaration.getName().toString();
            if (importStatement.endsWith(className)) {
                // Class got imported from a different package
                matchingImportStatement = importStatement;
            }
        }
        return matchingStatementFromInnerClasses(className, compilationUnit, matchingImportStatement);
    }

    private static String matchingStatementFromInnerClasses(String className, CompilationUnit compilationUnit, String matchingImportStatement) {
        for (Object type : compilationUnit.types()) {
            if (!(type instanceof AbstractTypeDeclaration)) {
                continue;
            }
            AbstractTypeDeclaration typeDeclaration = (AbstractTypeDeclaration) type;
            List declarations = typeDeclaration.bodyDeclarations();
            for (Object declaration : declarations) {
                AbstractTypeDeclaration childDeclaration = (AbstractTypeDeclaration) declaration;
                if (className.equals(childDeclaration.getName().toString())) {
                    // Class is an inner class (we support 1 level of such nesting for now - we can do recursion in the future
                    return compilationUnit.getPackage().getName().toString() + "." + typeDeclaration.getName() + "$" + childDeclaration.getName();
                }
            }
        }
        return matchingImportStatement;
    }

    public static Collection<KeyValueEntry> getTags(EnumConstantSource enumConstant, JavaEnumImpl myEnum, String getterName) {
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
                tags.addAll(ParsingUtils.keyValueEntries(myEnum, methodDeclaration, KeyName.class));
            }
        }
        return tags;
    }
}
