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

import com.google.template.soy.data.restricted.IntegerData;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;


/**
 * Unit tests for CodeBuilder.
 *
 */
public final class PhpExprUtilsTest extends TestCase {

    public void testConcatPhpExprs() {
        List<PhpExpr> exprs = new ArrayList<>();

        // Empty Array.
        assertThat(PhpExprUtils.concatPhpExprs(exprs).getText()).isEqualTo("''");

        // Single String value.
        PhpExpr foo = new PhpStringExpr("$foo");
        exprs.add(foo);
        assertThat(PhpExprUtils.concatPhpExprs(exprs).getText()).isEqualTo("$foo");

        // Single unknown value.
        exprs = new ArrayList<PhpExpr>();
        foo = new PhpExpr("$foo", Integer.MAX_VALUE);
        exprs.add(foo);
        assertThat(PhpExprUtils.concatPhpExprs(exprs).getText()).isEqualTo("$foo");

        // Multiple values are added to a list to be joined at a later time.
        PhpExpr bar = new PhpStringExpr("$bar");
        PhpExpr baz = new PhpStringExpr("$baz");
        exprs.add(bar);
        exprs.add(baz);
        assertThat(PhpExprUtils.concatPhpExprs(exprs).getText()).isEqualTo("$foo.$bar.$baz");

        // Added single array value
        foo = new PhpArrayExpr("[1, '2']", Integer.MAX_VALUE);
        exprs.add(foo);
        assertThat(PhpExprUtils.concatPhpExprs(exprs).getText()).isEqualTo("$foo.$bar.$baz.implode('', [1, '2'])");
    }
}
