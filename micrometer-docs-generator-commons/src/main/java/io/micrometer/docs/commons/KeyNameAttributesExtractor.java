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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.micrometer.common.docs.KeyName;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.docs.commons.KeyValueEntry.ExtraAttributesExtractor;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MethodDeclaration;
import org.jboss.forge.roaster.model.source.EnumConstantSource;
import org.jboss.forge.roaster.model.source.EnumConstantSource.Body;
import org.jboss.forge.roaster.model.source.MemberSource;

/**
 * Retrieve {@link KeyName} specific extra attributes.
 * For example, {@link KeyName#isRequired()} information.
 *
 * @author Tadaya Tsuyukubo
 */
public class KeyNameAttributesExtractor implements ExtraAttributesExtractor {

    public static final KeyNameAttributesExtractor INSTANCE = new KeyNameAttributesExtractor();

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(KeyNameAttributesExtractor.class);

    @Override
    public Map<String, String> apply(EnumConstantSource myEnum) {
        List<MemberSource<Body, ?>> members = myEnum.getBody().getMembers();
        if (members.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> map = new HashMap<>();
        members.stream().filter(member -> member.getName().equals("isRequired")).findFirst().ifPresent(member -> {
            Object internal = member.getInternal();
            if (!(internal instanceof MethodDeclaration)) {
                logger.warn("Can't read the member [" + member.getClass() + "] as a method declaration.");
                return;
            }
            MethodDeclaration methodDeclaration = (MethodDeclaration) internal;
            if (methodDeclaration.getBody().statements().isEmpty()) {
                logger.warn("Body was empty. Continuing...");
                return;
            }

            // read boolean return value
            String isRequired = ParsingUtils.stringFromReturnMethodDeclaration(methodDeclaration);
            map.put("isRequired", isRequired);
        });

        return map;
    }

}
