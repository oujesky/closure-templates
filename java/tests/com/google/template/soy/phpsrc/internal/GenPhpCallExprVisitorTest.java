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

import static com.google.template.soy.phpsrc.internal.SoyCodeForPhpSubject.assertThatSoyFile;

import junit.framework.TestCase;

/**
 * Unit tests for GenCallCodeVisitor.
 *
 */
public final class GenPhpCallExprVisitorTest extends TestCase {

    private static final String SOY_BASE = "{namespace boo.foo autoescape=\"strict\"}\n"
            + "{template .goo}\n"
            + "  Hello\n"
            + "{/template}\n"
            + "{template .moo}\n"
            + "  %s\n"
            + "{/template}\n";


    public void testBasicCall() {
        String soyCode = "{call .goo data=\"all\" /}";
        String expectedPhpCode = "self::goo($opt_data, $opt_ijData)";

        assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPhpCode);


        soyCode = "{call .goo data=\"$bar\" /}";
        expectedPhpCode = "self::goo(isset($opt_data['bar']) ? $opt_data['bar'] : null, $opt_ijData)";

        assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPhpCode);
    }

    public void testBasicCall_external() {
        String soyCode = "{call external.library.boo data=\"all\" /}";
        String expectedPhpCode = "\\external\\library::boo($opt_data, $opt_ijData)";

        assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPhpCode);


        soyCode = "{call external.library.boo data=\"$bar\" /}";
        expectedPhpCode = "\\external\\library::boo(isset($opt_data['bar']) ? $opt_data['bar'] : null, $opt_ijData)";

        assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPhpCode);
    }

    public void testBasicCall_params() {
        String soyCode = "{call .goo}\n"
                + "  {param goo: $moo /}\n"
                + "{/call}\n";
        String expectedPhpCode = "self::goo(['goo' => isset($opt_data['moo']) ? $opt_data['moo'] : null], $opt_ijData)";

        assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPhpCode);


        soyCode = "{call .goo}\n"
                + "  {param goo kind=\"text\"}Hello{/param}\n"
                + "{/call}\n";
        expectedPhpCode = "self::goo(['goo' => new \\Goog\\Soy\\UnsanitizedText('Hello')], $opt_ijData)";

        assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPhpCode);


        soyCode = "{call .goo}\n"
                + "  {param goo: $moo /}\n"
                + "  {param moo kind=\"text\"}Hello{/param}\n"
                + "{/call}\n";
        expectedPhpCode = "self::goo(['goo' => isset($opt_data['moo']) ? $opt_data['moo'] : null, " +
                "'moo' => new \\Goog\\Soy\\UnsanitizedText('Hello')], $opt_ijData)";

        assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPhpCode);


        soyCode = "{call .goo data=\"$bar\"}"
                + "  {param goo: $moo /}\n"
                + "{/call}\n";
        expectedPhpCode =
                "self::goo(array_replace(['goo' => isset($opt_data['moo']) ? $opt_data['moo'] : null], " +
                        "isset($opt_data['bar']) ? $opt_data['bar'] : null), $opt_ijData)";

        assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPhpCode);
    }

    public void testBasicCall_blockParams() {
        String soyCode = "{call .goo}\n"
                + "  {param moo kind=\"text\"}\n"
                + "    {for $i in range(3)}{$i}{/for}\n"
                + "  {/param}\n"
                + "{/call}\n";
        String expectedPhpCode = "self::goo(['moo' => new \\Goog\\Soy\\UnsanitizedText($param###)], $opt_ijData)";

        assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPhpCode);
    }

    public void testDelegateCall() {
        String soyCode = "{delcall moo.goo data=\"$bar\" /}";
        String expectedPhpCode = "call_user_func(Runtime::getDelegateFn('moo.goo', '', true), " +
                "isset($opt_data['bar']) ? $opt_data['bar'] : null, $opt_ijData)";

        assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPhpCode);


        soyCode = "{delcall moo.goo data=\"$bar\" variant=\"'beta'\" /}";
        expectedPhpCode = "call_user_func(Runtime::getDelegateFn('moo.goo', 'beta', true), " +
                "isset($opt_data['bar']) ? $opt_data['bar'] : null, $opt_ijData)";

        assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPhpCode);


        soyCode = "{delcall moo.goo data=\"$bar\" variant=\"'beta'\" allowemptydefault=\"false\" /}";
        expectedPhpCode = "call_user_func(Runtime::getDelegateFn('moo.goo', 'beta', false), " +
                "isset($opt_data['bar']) ? $opt_data['bar'] : null, $opt_ijData)";

        assertThatSoyFile(String.format(SOY_BASE, soyCode)).compilesToSourceContaining(expectedPhpCode);
    }
}
