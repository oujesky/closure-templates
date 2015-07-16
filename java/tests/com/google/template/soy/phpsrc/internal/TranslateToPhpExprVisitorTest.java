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

package com.google.template.soy.phpsrc.internal;

import static com.google.template.soy.phpsrc.internal.SoyExprForPhpSubject.assertThatSoyExpr;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.PrimitiveData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.phpsrc.restricted.PhpExpr;
import com.google.template.soy.phpsrc.restricted.PhpExprUtils;
import com.google.template.soy.phpsrc.restricted.PhpArrayExpr;
import com.google.template.soy.phpsrc.restricted.PhpStringExpr;

import junit.framework.TestCase;

import java.util.Map;

/**
 * Unit tests for TranslateToPhpExprVisitor.
 *
 */
public class TranslateToPhpExprVisitorTest extends TestCase {

    public void testNullLiteral() {
        assertThatSoyExpr("null").translatesTo(new PhpExpr("null", Integer.MAX_VALUE));
    }

    public void testBooleanLiteral() {
        assertThatSoyExpr("true").translatesTo(new PhpExpr("true", Integer.MAX_VALUE));
        assertThatSoyExpr("false").translatesTo(new PhpExpr("false", Integer.MAX_VALUE));
    }

    public void testStringLiteral() {
        assertThatSoyExpr("'waldo'").translatesTo(
                new PhpExpr("'waldo'", Integer.MAX_VALUE), PhpStringExpr.class);
    }

    public void testListLiteral() {
        assertThatSoyExpr("[]").translatesTo(new PhpExpr("[]", Integer.MAX_VALUE), PhpArrayExpr.class);
        assertThatSoyExpr("['blah', 123, $foo]").translatesTo(
                new PhpExpr("['blah', 123, isset($opt_data['foo']) ? $opt_data['foo'] : null]", Integer.MAX_VALUE), PhpArrayExpr.class);
    }

    public void testMapLiteral() {
        // Unquoted keys.
        assertThatSoyExpr("[:]").translatesTo(new PhpExpr("[]", Integer.MAX_VALUE));
        assertThatSoyExpr("['aaa': 123, 'bbb': 'blah']").translatesTo(
                new PhpExpr("['aaa' => 123, 'bbb' => 'blah']", Integer.MAX_VALUE));
        assertThatSoyExpr("['aaa': $foo, 'bbb': 'blah']").translatesTo(
                new PhpExpr("['aaa' => isset($opt_data['foo']) ? $opt_data['foo'] : null, 'bbb' => 'blah']", Integer.MAX_VALUE));

        // Non-string keys are allowed in PHP.
        assertThatSoyExpr("[1: 'blah', 0: 123]").translatesTo(
                new PhpExpr("[1 => 'blah', 0 => 123]", Integer.MAX_VALUE));
    }

    public void testMapLiteral_quotedKeysIfJS() {
        // QuotedKeysIfJs should change nothing in PHP.
        assertThatSoyExpr("quoteKeysIfJs([:])").translatesTo(new PhpExpr("[]", Integer.MAX_VALUE));
        assertThatSoyExpr("quoteKeysIfJs( ['aaa': $foo, 'bbb': 'blah'] )").translatesTo(
                new PhpExpr("['aaa' => isset($opt_data['foo']) ? $opt_data['foo'] : null, 'bbb' => 'blah']", Integer.MAX_VALUE));
    }

    public void testGlobals() {
        ImmutableMap<String, PrimitiveData> globals = ImmutableMap.<String, PrimitiveData>builder()
                .put("STR", StringData.forValue("Hello World"))
                .put("NUM", IntegerData.forValue(55))
                .put("BOOL", BooleanData.forValue(true))
                .build();

        assertThatSoyExpr("STR").withGlobals(globals).translatesTo(
                new PhpExpr("'Hello World'", Integer.MAX_VALUE));
        assertThatSoyExpr("NUM").withGlobals(globals).translatesTo(
                new PhpExpr("55", Integer.MAX_VALUE));
        assertThatSoyExpr("BOOL").withGlobals(globals).translatesTo(
                new PhpExpr("true", Integer.MAX_VALUE));
    }

    public void testDataRef() {
        assertThatSoyExpr("$boo").translatesTo(
                new PhpExpr("isset($opt_data['boo']) ? $opt_data['boo'] : null",
                        PhpExprUtils.phpPrecedenceForOperator(Operator.CONDITIONAL)));
        assertThatSoyExpr("$boo.goo").translatesTo(
                new PhpExpr("isset($opt_data['boo']['goo']) ? $opt_data['boo']['goo'] : null",
                        PhpExprUtils.phpPrecedenceForOperator(Operator.CONDITIONAL)));
        assertThatSoyExpr("$boo['goo']").translatesTo(
                new PhpExpr("isset($opt_data['boo']['goo']) ? $opt_data['boo']['goo'] : null",
                        PhpExprUtils.phpPrecedenceForOperator(Operator.CONDITIONAL)));
        assertThatSoyExpr("$boo.0").translatesTo(
                new PhpExpr("isset($opt_data['boo'][0]) ? $opt_data['boo'][0] : null",
                        PhpExprUtils.phpPrecedenceForOperator(Operator.CONDITIONAL)));
        assertThatSoyExpr("$boo[0]").translatesTo(
                new PhpExpr("isset($opt_data['boo'][0]) ? $opt_data['boo'][0] : null",
                        PhpExprUtils.phpPrecedenceForOperator(Operator.CONDITIONAL)));
        assertThatSoyExpr("$boo[$foo][$foo+1]").translatesTo(
                new PhpExpr("isset($opt_data['boo']" +
                        "[isset($opt_data['foo']) ? $opt_data['foo'] : null]" +
                        "[Runtime::typeSafeAdd(isset($opt_data['foo']) ? $opt_data['foo'] : null, 1)]) " +
                        "? $opt_data['boo']" +
                        "[isset($opt_data['foo']) ? $opt_data['foo'] : null]" +
                        "[Runtime::typeSafeAdd(isset($opt_data['foo']) ? $opt_data['foo'] : null, 1)] : null",
                        PhpExprUtils.phpPrecedenceForOperator(Operator.CONDITIONAL)));

        assertThatSoyExpr("$boo?.goo").translatesTo(
                new PhpExpr(
                        "isset($opt_data['boo']['goo']) ? $opt_data['boo']['goo'] : null",
                        PhpExprUtils.phpPrecedenceForOperator(Operator.CONDITIONAL)));
        assertThatSoyExpr("$boo?[0]?.1").translatesTo(
                new PhpExpr(
                        "isset($opt_data['boo'][0][1]) ? $opt_data['boo'][0][1] : null",
                        PhpExprUtils.phpPrecedenceForOperator(Operator.CONDITIONAL)));
    }

    public void testDataRef_localVars() {
        Map<String, PhpExpr> frame = Maps.newHashMap();
        frame.put("zoo", new PhpExpr("$zooData8", Integer.MAX_VALUE));

        assertThatSoyExpr("$zoo").with(frame).translatesTo(new PhpExpr("$zooData8", Integer.MAX_VALUE));
        assertThatSoyExpr("$zoo.boo").with(frame).translatesTo(
                new PhpExpr("isset($zooData8['boo']) ? $zooData8['boo'] : null",
                        PhpExprUtils.phpPrecedenceForOperator(Operator.CONDITIONAL)));
    }

    public void testBasicOperators() {
        assertThatSoyExpr("not $boo or true and $foo").translatesTo(
                new PhpExpr("! (isset($opt_data['boo']) ? $opt_data['boo'] : null) || true && (isset($opt_data['foo']) ? $opt_data['foo'] : null)",
                        PhpExprUtils.phpPrecedenceForOperator(Operator.OR)));
    }

    public void testEqualOperator() {
        assertThatSoyExpr("'5' == 5").translatesTo(
                new PhpExpr("'5' == 5",
                        PhpExprUtils.phpPrecedenceForOperator(Operator.EQUAL)));
        assertThatSoyExpr("'5' == $boo").translatesTo(
                new PhpExpr("'5' == (isset($opt_data['boo']) ? $opt_data['boo'] : null)",
                        PhpExprUtils.phpPrecedenceForOperator(Operator.EQUAL)));
    }

    public void testNotEqualOperator() {
        assertThatSoyExpr("'5' != 5").translatesTo(
                new PhpExpr("'5' != 5",
                        PhpExprUtils.phpPrecedenceForOperator(Operator.NOT_EQUAL)));
    }

    public void testPlusOperator() {
        assertThatSoyExpr("( (8-4) + (2-1) )").translatesTo(
                new PhpExpr("Runtime::typeSafeAdd(8 - 4, 2 - 1)", Integer.MAX_VALUE));
    }

    public void testNullCoalescingOperator() {
        assertThatSoyExpr("$boo ?: 5").translatesTo(
                new PhpExpr("((isset($opt_data['boo']) ? $opt_data['boo'] : null) !== null) ? (isset($opt_data['boo']) ? $opt_data['boo'] : null) : 5",
                        PhpExprUtils.phpPrecedenceForOperator(Operator.CONDITIONAL)));
    }

    public void testConditionalOperator() {
        assertThatSoyExpr("$boo ? 5 : 6").translatesTo(
                new PhpExpr("(isset($opt_data['boo']) ? $opt_data['boo'] : null) ? 5 : 6",
                        PhpExprUtils.phpPrecedenceForOperator(Operator.CONDITIONAL)));
    }
}
