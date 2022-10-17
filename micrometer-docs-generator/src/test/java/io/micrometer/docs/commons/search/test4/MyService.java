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

package io.micrometer.docs.commons.search.test4;

import io.micrometer.docs.commons.search.test4.sub.DifferentPackageEnum;

import static io.micrometer.docs.commons.search.test4.MyService.MyAnotherEnum.HELLO;

/**
 *
 * @author Tadaya Tsuyukubo
 */
public class MyService {

    public MyEnum foo() {
        // nested enum reference with qualifier
        return MyEnum.FOO;
    }

    public SamePackageEnum same() {
        // same package enum
        return SamePackageEnum.SAME;
    }

    public DifferentPackageEnum different() {
        // enum in different package
        return DifferentPackageEnum.DIFFERENT;
    }

    public MyAnotherEnum hello() {
        // with static import
        return HELLO;
    }

    enum MyEnum {
        FOO
    }

    enum MyAnotherEnum {
        HELLO
    }

}
