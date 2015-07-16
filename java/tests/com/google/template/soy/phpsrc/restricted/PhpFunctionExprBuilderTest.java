/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.template.soy.phpsrc.restricted;

import static com.google.common.truth.Truth.assertThat;

import junit.framework.TestCase;

import java.util.ArrayList;

/**
 * Unit tests for PhpFunctionBuilder.
 *
 */

public final class PhpFunctionExprBuilderTest extends TestCase {
    public void testSingleNumberArgument() {
        PhpFunctionExprBuilder func = new PhpFunctionExprBuilder("some_func");
        func.addArg(600851475143L);
        assertEquals(func.build(), "some_func(600851475143)");
    }

    public void testSingleStringArgument() {
        PhpFunctionExprBuilder func = new PhpFunctionExprBuilder("some_func");
        func.addArg("10");
        assertEquals("some_func('10')", func.build());
    }

    public void testSingleArrayArgument() {
        PhpFunctionExprBuilder func = new PhpFunctionExprBuilder("some_func");
        ArrayList<Object> list = new ArrayList<>();
        list.add("foo");
        list.add("bar");
        list.add(42);

        func.addArg(PhpExprUtils.convertIterableToPhpArrayExpr(list));
        assertEquals("some_func(['foo', 'bar', 42])", func.build());
    }

    public void testSinglePyFunctionBuilderArgument() {
        PhpFunctionExprBuilder nestedFunc = new PhpFunctionExprBuilder("nested_func");
        nestedFunc.addArg(10);

        PhpFunctionExprBuilder func = new PhpFunctionExprBuilder("some_func");
        func.addArg(nestedFunc.asPhpExpr());

        assertEquals(func.build(), "some_func(nested_func(10))");
    }

    public void testMultipleArguments() {
        PhpFunctionExprBuilder func = new PhpFunctionExprBuilder("some_func");
        func.addArg(42);
        func.addArg("foobar");
        assertEquals(func.build(), "some_func(42, 'foobar')");
    }

}
