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

import static com.google.template.soy.phpsrc.internal.SoyCodeForPhpSubject.assertThatSoyCode;
import static com.google.template.soy.phpsrc.internal.SoyCodeForPhpSubject.assertThatSoyFile;

import com.google.template.soy.base.SoySyntaxException;

import junit.framework.TestCase;
import org.junit.Ignore;

/**
 * Unit tests for GenPhpCodeVisitor.
 *
 * <p>TODO(dcphillips): Add non-inlined 'if' test after adding LetNode support.
 *
 */
public final class GenPhpCodeVisitorTest extends TestCase {

    private static final String SOY_NAMESPACE = "{namespace boo.foo autoescape=\"strict\"}\n";

    private static final String EXPECTED_PHPFILE_START =
            "<?php\n" +
                    "/**\n" +
                    " * This file was automatically generated from no-path.\n" +
                    " * Please don't edit this file by hand.\n" +
                    " * \n" +
                    " * Templates in namespace boo.foo.\n" +
                    " */\n" +
                    "\n" +
                    "namespace boo;\n" +
                    "\n" +
                    "use Goog\\Soy\\Bidi;\n" +
                    "use Goog\\Soy\\Directives;\n" +
                    "use Goog\\Soy\\Sanitize;\n" +
                    "use Goog\\Soy\\Runtime;\n" +
                    "\n" +
                    "class foo {\n";

    private static final String EXPECTED_PHPFILE_END  =
            "\n"
                + "}\n"
                + "\n";

    private static final String EXPECTED_PHPMETHOD_START =
            "  \n"
                + "  \n"
                + "  /**\n"
                + "   * @param array|null $opt_data\n"
                + "   * @param array|null $opt_ijData\n"
                + "   * @return \\Goog\\Soy\\SanitizedContent\n"
                + "   */\n";

    public void testSoyFile() {
        String expectedPhpFile = EXPECTED_PHPFILE_START + EXPECTED_PHPFILE_END;

        assertThatSoyFile(SOY_NAMESPACE).compilesToSourceContaining(expectedPhpFile);

        // TODO(dcphillips): Add external template dependency import test once templates are supported.
    }

    // @todo bidi
    public void _testBidiConfiguration() {
        String exptectedBidiConfig = "from example import bidi as external_bidi\n";

        assertThatSoyFile(SOY_NAMESPACE).withBidi("example.bidi.fn")
                .compilesToSourceContaining(exptectedBidiConfig);
    }

    public void testTranslationConfiguration() {
        String exptectedTranslationConfig = "use Goog\\Soy\\SimpleTranslator as Translator;\n";

        assertThatSoyFile(SOY_NAMESPACE).withTranslationClass("Goog\\Soy\\SimpleTranslator")
                .compilesToSourceContaining(exptectedTranslationConfig);
    }

    public void testBlankTemplate() {
        String soyFile = SOY_NAMESPACE
                + "{template .helloWorld}\n"
                + "{/template}\n";

        String expectedPhpFile = EXPECTED_PHPFILE_START
                + EXPECTED_PHPMETHOD_START
                + "  public static function helloWorld($opt_data = null, $opt_ijData = null) {\n"
                + "    $output = '';\n"
                + "    return new \\Goog\\Soy\\SanitizedHtml($output);\n"
                + "  }\n"
                + EXPECTED_PHPFILE_END;

        assertThatSoyFile(soyFile).compilesTo(expectedPhpFile);
    }

    public void testSimpleTemplate() {
        String soyFile = SOY_NAMESPACE
                + "{template .helloWorld}\n"
                + "  Hello World!\n"
                + "{/template}\n";

        String expectedPhpFile = EXPECTED_PHPFILE_START
                + EXPECTED_PHPMETHOD_START
                + "  public static function helloWorld($opt_data = null, $opt_ijData = null) {\n"
                + "    $output = 'Hello World!';\n"
                + "    return new \\Goog\\Soy\\SanitizedHtml($output);\n"
                + "  }\n"
                + EXPECTED_PHPFILE_END;

        assertThatSoyFile(soyFile).compilesTo(expectedPhpFile);
    }

    public void testOutputScope() {
        String soyFile = SOY_NAMESPACE
                + "{template .helloWorld}\n"
                + "  {if $foo}\n"
                + "    {for $i in range(5)}\n"
                + "      {$boo[$i]}\n"
                + "    {/for}\n"
                + "  {else}\n"
                + "    Blah\n"
                + "  {/if}\n"
                + "{/template}\n";

        String expectedPhpFile = EXPECTED_PHPFILE_START
                + EXPECTED_PHPMETHOD_START
                + "  public static function helloWorld($opt_data = null, $opt_ijData = null) {\n"
                + "    $opt_data = is_array($opt_data) ? $opt_data : [];\n"
                + "    $output = '';\n"
                + "    if (isset($opt_data['foo']) ? $opt_data['foo'] : null) {\n"
                + "      for ($i### = 0; $i### < 5; $i###++) {\n"
                + "        $output .= isset($opt_data['boo'][$i###]) ? $opt_data['boo'][$i###] : null;\n"
                + "      }\n"
                + "    }\n"
                + "    else {\n"
                + "      $output .= 'Blah';\n"
                + "    }\n"
                + "    return new \\Goog\\Soy\\SanitizedHtml($output);\n"
                + "  }\n"
                + EXPECTED_PHPFILE_END;

        assertThatSoyFile(soyFile).compilesTo(expectedPhpFile);
    }

    public void testSwitch() {
        String soyCode =
                "{switch $boo}\n"
                        + "  {case 0}\n"
                        + "     Hello\n"
                        + "  {case 1}\n"
                        + "     World\n"
                        + "  {default}\n"
                        + "     !\n"
                        + "{/switch}\n";
        String expectedPhpCode =
                "switch (isset($opt_data['boo']) ? $opt_data['boo'] : null) {\n"
                        + "  case 0:\n"
                        + "    $output .= 'Hello';\n"
                        + "    break;\n"
                        + "  case 1:\n"
                        + "    $output .= 'World';\n"
                        + "    break;\n"
                        + "  default:\n"
                        + "    $output .= '!';\n"
                        + "}\n";
        assertThatSoyCode(soyCode).compilesTo(expectedPhpCode);
    }

    public void testSwitch_defaultOnly() {
        String soyCode =
                "{switch $boo}\n"
                        + "  {default}\n"
                        + "     Hello World!\n"
                        + "{/switch}\n";
        String expectedPhpCode =
                "switch (isset($opt_data['boo']) ? $opt_data['boo'] : null) {\n"
                        + "  default:\n"
                        + "    $output .= 'Hello World!';\n"
                        + "}\n";
        assertThatSoyCode(soyCode).compilesTo(expectedPhpCode);
    }

    public void testFor() {
        String soyCode =
                "{for $i in range(5)}\n"
                        + "  {$boo[$i]}\n"
                        + "{/for}\n";
        String expectedPhpCode =
                "for ($i### = 0; $i### < 5; $i###++) {\n"
                        + "  $output .= isset($opt_data['boo'][$i###]) ? $opt_data['boo'][$i###] : null;\n"
                        + "}\n";
        assertThatSoyCode(soyCode).compilesTo(expectedPhpCode);

        soyCode =
                "{for $i in range(5, 10)}\n"
                        + "  {$boo[$i]}\n"
                        + "{/for}\n";
        expectedPhpCode =
                "for ($i### = 5; $i### < 10; $i###++) {\n"
                        + "  $output .= isset($opt_data['boo'][$i###]) ? $opt_data['boo'][$i###] : null;\n"
                        + "}\n";
        assertThatSoyCode(soyCode).compilesTo(expectedPhpCode);

        soyCode =
                "{for $i in range($foo, $boo, $goo)}\n"
                        + "  {$boo[$i]}\n"
                        + "{/for}\n";
        expectedPhpCode =
                "$iInit### = isset($opt_data['foo']) ? $opt_data['foo'] : null;\n"
                        + "$iLimit### = isset($opt_data['boo']) ? $opt_data['boo'] : null;\n"
                        + "$iIncrement### = isset($opt_data['goo']) ? $opt_data['goo'] : null;\n"
                        + "for ($i### = $iInit###; $i### < $iLimit###; $i### += $iIncrement###) {\n"
                        + "  $output .= isset($opt_data['boo'][$i###]) ? $opt_data['boo'][$i###] : null;\n"
                        + "}\n";
        assertThatSoyCode(soyCode).compilesTo(expectedPhpCode);
    }

    public void testForeach() {
        String soyCode =
                "{foreach $operand in $operands}\n"
                        + "  {$operand}\n"
                        + "{/foreach}\n";

        String expectedPhpCode =
                "$operandList### = (array)(isset($opt_data['operands']) ? $opt_data['operands'] : null);\n"
                        + "reset($operandList###);\n"
                        + "$operandFirstKey### = key($operandList###);\n"
                        + "end($operandList###);\n"
                        + "$operandLastKey### = key($operandList###);\n"
                        + "foreach ($operandList### as $operandIndex### => $operandData###) {\n"
                        + "  $output .= $operandData###;\n"
                        + "}\n";

        assertThatSoyCode(soyCode).compilesTo(expectedPhpCode);

        soyCode =
                "{foreach $operand in $operands}\n"
                        + "  {isFirst($operand)}\n"
                        + "  {isLast($operand)}\n"
                        + "  {index($operand)}\n"
                        + "{/foreach}\n";

        expectedPhpCode =
                "$operandList### = (array)(isset($opt_data['operands']) ? $opt_data['operands'] : null);\n"
                        + "reset($operandList###);\n"
                        + "$operandFirstKey### = key($operandList###);\n"
                        + "end($operandList###);\n"
                        + "$operandLastKey### = key($operandList###);\n"
                        + "foreach ($operandList### as $operandIndex### => $operandData###) {\n"
                        + "  $output .= ($operandIndex### === $operandFirstKey###).($operandIndex### === $operandLastKey###).$operandIndex###;\n"
                        + "}\n";

        assertThatSoyCode(soyCode).compilesTo(expectedPhpCode);
    }

    public void testForeach_ifempty() {
        String soyCode =
                "{foreach $operand in $operands}\n"
                        + "  {$operand}\n"
                        + "{ifempty}\n"
                        + "  {$foo}"
                        + "{/foreach}\n";

        String expectedPhpCode =
                "$operandList### = (array)(isset($opt_data['operands']) ? $opt_data['operands'] : null);\n"
                        + "if (!empty($operandList###)) {\n"
                        + "  reset($operandList###);\n"
                        + "  $operandFirstKey### = key($operandList###);\n"
                        + "  end($operandList###);\n"
                        + "  $operandLastKey### = key($operandList###);\n"
                        + "  foreach ($operandList### as $operandIndex### => $operandData###) {\n"
                        + "    $output .= $operandData###;\n"
                        + "  }\n"
                        + "} else {\n"
                        + "  $output .= isset($opt_data['foo']) ? $opt_data['foo'] : null;\n"
                        + "}\n";

        assertThatSoyCode(soyCode).compilesTo(expectedPhpCode);
    }

    public void testLetValue() {
        assertThatSoyCode("{let $foo: $boo /}\n").compilesTo("$foo__soy### = isset($opt_data['boo']) ? $opt_data['boo'] : null;\n");
    }

    public void testLetContent() {
        String soyCode =
                "{let $foo kind=\"html\"}\n"
                        + "  Hello {$boo}\n"
                        + "{/let}\n";

        String expectedPhpCode =
                "$foo__soy### = 'Hello '.(isset($opt_data['boo']) ? $opt_data['boo'] : null);\n"
                        + "$foo__soy### = new \\Goog\\Soy\\SanitizedHtml($foo__soy###);\n";

        assertThatSoyCode(soyCode).compilesTo(expectedPhpCode);
    }

    public void testLetContent_notComputableAsExpr() {
        String soyCode =
                "{let $foo kind=\"html\"}\n"
                        + "  {for $num in range(5)}\n"
                        + "    {$num}\n"
                        + "  {/for}\n"
                        + "  Hello {$boo}\n"
                        + "{/let}\n";

        String expectedPhpCode =
                "$foo__soy### = '';\n"
                        + "for ($num### = 0; $num### < 5; $num###++) {\n"
                        + "  $foo__soy### .= $num###;\n"
                        + "}\n"
                        + "$foo__soy### .= 'Hello '.(isset($opt_data['boo']) ? $opt_data['boo'] : null);\n"
                        + "$foo__soy### = new \\Goog\\Soy\\SanitizedHtml($foo__soy###);\n";

        assertThatSoyCode(soyCode).compilesTo(expectedPhpCode);
    }

    public void testLetContent_noContentKind() {
        String soyCode =
                "{let $foo}\n"
                        + "  Hello {$boo}\n"
                        + "{/let}\n";

        assertThatSoyCode(soyCode).compilesWithException(SoySyntaxException.class);
    }

    public void testCallReturnsString() {
        String soyCode = "{call .foo data=\"all\" /}";

        String expectedPhpCode = "$output .= \\brittle\\test\\ns::foo($opt_data, $opt_ijData);\n";

        assertThatSoyCode(soyCode).compilesTo(expectedPhpCode);
    }
}
