/**
 * Copyright 2022 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.docs.commons;

import java.util.AbstractMap;
import java.util.ArrayList;
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
import io.micrometer.docs.commons.utils.Assert;
import org.jboss.forge.roaster.Internal;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.BooleanLiteral;
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
import org.jboss.forge.roaster.model.source.EnumConstantSource;
import org.jboss.forge.roaster.model.source.JavaEnumSource;
import org.jboss.forge.roaster.model.source.JavaSource;
import org.jboss.forge.roaster.model.source.MemberSource;
import org.jboss.forge.roaster.model.source.MethodSource;

public class ParsingUtils {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ParsingUtils.class);

    @SuppressWarnings("unchecked")
    private static <T> void updateModelsFromEnum(JavaEnumSource parentEnum, JavaSource<?> source,
            Collection<T> models, EntryEnumConstantReader<?> converter) {
        if (!(source instanceof JavaEnumSource)) {
            return;
        }
        JavaEnumSource myEnum = (JavaEnumSource) source;

        // Based on how interfaces are implemented in enum, "myEnum.getInterfaces()" has different values.
        // For example, "MyEnum" implements "Observation.Event" interface as:
        //  - "enum MyEnum implements Observation.Event {"
        //      "getInterfaces()" returns ["Observation.Event"]
        //  - "enum MyEnum implements Event {"
        //      "getInterfaces()" returns ["io.micrometer.observation.Observation.Event"]
        //
        // To make both cases work, use the simple name("Event" in the above example) for comparison.
        if (!myEnum.hasInterface(converter.getRequiredClass().getSimpleName())) {
            return;
        }
        logger.debug("Checking [" + parentEnum.getName() + "." + myEnum.getName() + "]");
        if (myEnum.getEnumConstants().size() == 0) {
            return;
        }
        for (EnumConstantSource enumConstant : myEnum.getEnumConstants()) {
            models.add((T) converter.apply(enumConstant));
        }
    }

    /**
     * Read the return statement value as string.
     * Currently, the string literal and boolean literal are supported.
     * For example:
     * <code>
     * String returnStringLiteral() {
     * return "my-string";
     * }
     * boolean returnBooleanPrimitive() {
     * return true;
     * }
     * </code>
     * It resolves as "my-string" and "true" respectively.
     *
     * @param methodDeclaration a method declaration which has the first line return statement with literal string or boolean.
     * @return literal value as string
     */
    public static String readStringReturnValue(MethodDeclaration methodDeclaration) {
        return stringFromReturnMethodDeclaration(methodDeclaration);
    }

    public static <T> List<T> retrieveModels(JavaEnumSource myEnum, MethodDeclaration methodDeclaration,
            EntryEnumConstantReader<?> converter) {
        Collection<String> enumNames = readClassValue(methodDeclaration);
        List<T> models = new ArrayList<>();
        enumNames.forEach(enumName -> {
            List<JavaSource<?>> nestedTypes = myEnum.getNestedTypes();
            JavaSource<?> nestedSource = nestedTypes.stream()
                    .filter(javaSource -> javaSource.getName().equals(enumName)).findFirst().orElseThrow(
                            () -> new IllegalStateException("There's no nested type with name [" + enumName + "]"));
            ParsingUtils.updateModelsFromEnum(myEnum, nestedSource, models, converter);
        });
        return models;
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

    @Nullable
    static String enumMethodValue(EnumConstantSource enumConstant, String methodName) {
        List<MemberSource<EnumConstantSource.Body, ?>> members = enumConstant.getBody().getMembers();
        if (members.isEmpty()) {
            logger.debug("No method declarations in the enum.");
            return null;
        }
        Object internal = members.stream().filter(bodyMemberSource -> bodyMemberSource.getName().equals(methodName)).findFirst().map(Internal::getInternal).orElse(null);
        if (internal == null) {
            logger.debug("Can't find the member with method name [" + methodName + "] on " + enumConstant.getName());
            return null;
        }
        if (!(internal instanceof MethodDeclaration)) {
            logger.debug("Can't read the member [" + internal.getClass() + "] as a method declaration.");
            return null;
        }
        MethodDeclaration methodDeclaration = (MethodDeclaration) internal;
        if (methodDeclaration.getBody().statements().isEmpty()) {
            logger.debug("Body was empty.");
            return null;
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
        if (expression instanceof StringLiteral) {
            return ((StringLiteral) expression).getLiteralValue();
        }
        else if (expression instanceof BooleanLiteral) {
            return Boolean.toString(((BooleanLiteral) expression).booleanValue());
        }
        else if (expression instanceof TypeLiteral) {
            // e.g. return String.class;
            return ((TypeLiteral) expression).getType().toString();
        }
        else if (expression instanceof QualifiedName) {
            // enum value
            return ((QualifiedName) expression).getName().toString();
        }
        logger.warn("Statement [" + statement.getClass() + "] is not a string literal statement.");
        return "";
    }

    @Nullable
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

    @Nullable
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
        List<?> imports = compilationUnit.imports();
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
            List<?> declarations = typeDeclaration.bodyDeclarations();
            for (Object declaration : declarations) {
                if (!(declaration instanceof AbstractTypeDeclaration)) {
                    continue;
                }
                AbstractTypeDeclaration childDeclaration = (AbstractTypeDeclaration) declaration;
                if (className.equals(childDeclaration.getName().toString())) {
                    // Class is an inner class (we support 1 level of such nesting for now - we can do recursion in the future
                    return compilationUnit.getPackage().getName().toString() + "." + typeDeclaration.getName() + "$" + childDeclaration.getName();
                }
            }
        }
        return matchingImportStatement;
    }

    public static List<KeyNameEntry> getTags(EnumConstantSource enumConstant, JavaEnumSource myEnum, String methodName) {
        MethodSource<?> methodSource = enumConstant.getBody().getMethod(methodName);
        if (methodSource == null) {
            return Collections.emptyList();
        }
        // TODO: try to avoid the usage of internal
        Object internal = methodSource.getInternal();
        if (!(internal instanceof MethodDeclaration)) {
            return Collections.emptyList();
        }
        MethodDeclaration methodDeclaration = (MethodDeclaration) internal;
        return ParsingUtils.retrieveModels(myEnum, methodDeclaration, KeyNameEnumConstantReader.INSTANCE);
    }

    /**
     * Retrieve {@link MethodDeclaration} from {@link MethodSource}.
     *
     * @param methodSource a method source
     * @return retrieved method declaration
     */
    public static MethodDeclaration getMethodDeclaration(MethodSource<?> methodSource) {
        Object methodInternal = methodSource.getInternal();
        Assert.isInstanceOf(MethodDeclaration.class, methodInternal);
        return (MethodDeclaration) methodInternal;
    }
}
