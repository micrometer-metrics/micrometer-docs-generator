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

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.micrometer.core.instrument.docs.TagKey;
import io.micrometer.core.util.internal.logging.InternalLogger;
import io.micrometer.core.util.internal.logging.InternalLoggerFactory;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.CompilationUnit;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Expression;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ImportDeclaration;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MethodDeclaration;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MethodInvocation;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.QualifiedName;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ReturnStatement;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.StringLiteral;
import org.jboss.forge.roaster.model.impl.JavaEnumImpl;
import org.jboss.forge.roaster.model.source.EnumConstantSource;
import org.jboss.forge.roaster.model.source.JavaSource;
import org.jboss.forge.roaster.model.source.MemberSource;

public class ParsingUtils {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ParsingUtils.class);

    public static void updateKeyValuesFromEnum(JavaEnumImpl parentEnum, JavaSource<?> source, Class requiredClass,
            Collection<KeyValueEntry> keyValues) {
        if (!(source instanceof JavaEnumImpl)) {
            return;
        }
        JavaEnumImpl myEnum = (JavaEnumImpl) source;
        if (!myEnum.getInterfaces().contains(requiredClass.getCanonicalName())) {
            return;
        }
        logger.info("Checking [" + parentEnum.getName() + "." + myEnum.getName() + "]");
        if (myEnum.getEnumConstants().size() == 0) {
            return;
        }
        for (EnumConstantSource enumConstant : myEnum.getEnumConstants()) {
            String keyValue = enumKeyValue(enumConstant);
            keyValues.add(new KeyValueEntry(keyValue, enumConstant.getJavaDoc().getText()));
        }
    }

    public static String readStringReturnValue(MethodDeclaration methodDeclaration) {
        return stringFromReturnMethodDeclaration(methodDeclaration);
    }

    public static Collection<KeyValueEntry> keyValueEntries(JavaEnumImpl myEnum, MethodDeclaration methodDeclaration,
            Class requiredClass) {
        Collection<String> enumNames = readClassValue(methodDeclaration);
        Collection<KeyValueEntry> keyValues = new TreeSet<>();
        enumNames.forEach(enumName -> {
            List<JavaSource<?>> nestedTypes = myEnum.getNestedTypes();
            JavaSource<?> nestedSource = nestedTypes.stream()
                    .filter(javaSource -> javaSource.getName().equals(enumName)).findFirst().orElseThrow(
                            () -> new IllegalStateException("There's no nested type with name [" + enumName + "]"));
            ParsingUtils.updateKeyValuesFromEnum(myEnum, nestedSource, requiredClass, keyValues);
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
            // TagKey.merge(TestSpanTags.values(),AsyncSpanTags.values())
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
                    + TagKey.class + " interface or use [TagKey.merge(...)] method to merge multiple values from tags");
        }
        // will return Tags
        return Collections.singletonList(methodInvocation.getExpression().toString());
    }

    private static String enumKeyValue(EnumConstantSource enumConstant) {
        List<MemberSource<EnumConstantSource.Body, ?>> members = enumConstant.getBody().getMembers();
        if (members.isEmpty()) {
            logger.warn("No method declarations in the enum.");
            return "";
        }
        Object internal = members.get(0).getInternal();
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
                tags.addAll(ParsingUtils.keyValueEntries(myEnum, methodDeclaration, TagKey.class));
            }
        }
        return tags;
    }
}
