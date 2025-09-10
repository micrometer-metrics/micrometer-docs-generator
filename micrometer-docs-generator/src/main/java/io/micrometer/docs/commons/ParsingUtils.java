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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import io.micrometer.common.docs.KeyName;
import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.docs.commons.utils.Assert;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.BooleanLiteral;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Expression;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MethodDeclaration;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MethodInvocation;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.QualifiedName;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ReturnStatement;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.StringLiteral;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.TypeLiteral;
import org.jboss.forge.roaster.model.source.EnumConstantSource;
import org.jboss.forge.roaster.model.source.JavaEnumSource;
import org.jboss.forge.roaster.model.source.MethodSource;

public class ParsingUtils {

    private ParsingUtils() {
    }

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ParsingUtils.class);

    @SuppressWarnings("unchecked")
    public static <T> List<T> retrieveModelsFromEnum(JavaEnumSource enumSource, EntryEnumConstantReader<?> converter) {
        // @formatter:off
        // Based on how interfaces are implemented in enum, "myEnum.getInterfaces()" has
        // different values.
        // For example, "MyEnum" implements "Observation.Event" interface as:
        //  - "enum MyEnum implements Observation.Event {"
        //      "getInterfaces()" returns ["Observation.Event"]
        //  - "enum MyEnum implements Event {"
        //      "getInterfaces()" returns ["io.micrometer.observation.Observation.Event"]
        //
        // To make both cases work, use the simple name("Event" in the above example) for
        // comparison.
        // @formatter:on
        if (!enumSource.hasInterface(converter.getRequiredClass().getSimpleName())) {
            return Collections.emptyList();
        }
        logger.debug("Checking [" + enumSource.getName() + "." + enumSource.getName() + "]");
        if (enumSource.getEnumConstants().size() == 0) {
            return Collections.emptyList();
        }

        List<T> models = new ArrayList<>();
        for (EnumConstantSource enumConstant : enumSource.getEnumConstants()) {
            models.add((T) converter.apply(enumConstant));
        }
        return models;
    }

    /**
     * Read the return statement value as string. Currently, the string literal and
     * boolean literal are supported.
     * <p>
     * For example: <code>
     * String returnStringLiteral() {
     * return "my-string";
     * }
     * boolean returnBooleanPrimitive() {
     * return true;
     * }
     * </code> It resolves as "my-string" and "true" respectively.
     * @param methodSource a method source which has the first line return statement with
     * literal string or boolean.
     * @return literal value as string
     */
    public static String readStringReturnValue(MethodSource<?> methodSource) {
        return stringFromReturnMethodDeclaration(methodSource);
    }

    public static Set<String> readEnumClassNames(MethodSource<?> methodSource) {
        Expression expression = expressionFromReturnMethodDeclaration(methodSource);
        if (!(expression instanceof MethodInvocation)) {
            logger.warn("Statement [" + expression + "] is not a method invocation.");
            return Collections.emptySet();
        }
        MethodInvocation methodInvocation = (MethodInvocation) expression;

        if ("merge".equals(methodInvocation.getName().getIdentifier())) {
            // KeyName.merge(TestSpanTags.values(),AsyncSpanTags.values())
            Set<String> classNames = new TreeSet<>();

            // Traverse arguments of the "merge" method
            for (Object argument : methodInvocation.arguments()) {
                if (argument instanceof MethodInvocation) {
                    MethodInvocation argInvocation = (MethodInvocation) argument;

                    // Ensure the method is ".values()" and extract its scope
                    if ("values".equals(argInvocation.getName().getIdentifier())
                            && argInvocation.getExpression() != null) {
                        Expression scope = argInvocation.getExpression();

                        // If KeyName is nested, extract only the corresponding class name
                        if (scope instanceof QualifiedName) {
                            String qualifiedName = scope.toString();
                            String className = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
                            classNames.add(className);
                        }
                        else {
                            classNames.add(scope.toString());
                        }
                    }
                }
            }
            return classNames;
        }
        else if (!methodInvocation.toString().endsWith(".values()")) {
            throw new IllegalStateException(
                    "You have to use the static .values() method on the enum that implements " + KeyName.class
                            + " interface or use [KeyName.merge(...)] method to merge multiple values from tags");
        }
        // will return Tags
        return Collections.singleton(methodInvocation.getExpression().toString());
    }

    @Nullable
    static String enumMethodValue(EnumConstantSource enumConstant, String methodName) {
        MethodSource<?> methodSource = enumConstant.getBody().getMethod(methodName);
        if (methodSource == null) {
            logger.debug("Can't find the member with method name [" + methodName + "] on " + enumConstant.getName());
            return null;
        }
        return readStringReturnValue(methodSource);
    }

    private static String stringFromReturnMethodDeclaration(MethodSource<?> methodSource) {
        Expression expression = expressionFromReturnMethodDeclaration(methodSource);
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
        logger.warn("Failed to retrieve string return value from [" + methodSource.getName() + "].");
        return "";
    }

    @Nullable
    public static Expression expressionFromReturnMethodDeclaration(MethodSource<?> methodSource) {
        MethodDeclaration methodDeclaration = getMethodDeclaration(methodSource);
        Object statement = methodDeclaration.getBody().statements().get(0);
        if (!(statement instanceof ReturnStatement)) {
            logger.warn("Statement [" + statement.getClass() + "] is not a return statement.");
            return null;
        }
        ReturnStatement returnStatement = (ReturnStatement) statement;
        return returnStatement.getExpression();
    }

    /**
     * Retrieve {@link MethodDeclaration} from {@link MethodSource}.
     * @param methodSource a method source
     * @return retrieved method declaration
     */
    public static MethodDeclaration getMethodDeclaration(MethodSource<?> methodSource) {
        Object methodInternal = methodSource.getInternal();
        Assert.isInstanceOf(MethodDeclaration.class, methodInternal);
        return (MethodDeclaration) methodInternal;
    }

}
