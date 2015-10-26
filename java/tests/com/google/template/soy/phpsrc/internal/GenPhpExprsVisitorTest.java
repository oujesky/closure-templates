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

import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.phpsrc.restricted.PhpExpr;
import com.google.template.soy.phpsrc.restricted.PhpExprUtils;

import junit.framework.TestCase;

/**
 * Unit tests for GenPhpExprsVisitor.
 *
 */
public final class GenPhpExprsVisitorTest extends TestCase {

    public void testRawText() {
        assertThatSoyExpr("I'm feeling lucky!").compilesTo(
                new PhpExpr("'I\\'m feeling lucky!'", Integer.MAX_VALUE));
    }

    public void testCss() {
        assertThatSoyExpr("{css primary}").compilesTo(
                new PhpExpr("Runtime::getCssName('primary')", Integer.MAX_VALUE));

        assertThatSoyExpr("{css $foo, bar}").compilesTo(
                new PhpExpr("Runtime::getCssName(isset($opt_data['foo']) ? $opt_data['foo'] : null, 'bar')", Integer.MAX_VALUE));
    }

    public void testIf() {
        String soyNodeCode =
                "{if $boo}\n"
                        + "  Blah\n"
                        + "{elseif not $goo}\n"
                        + "  Bleh\n"
                        + "{else}\n"
                        + "  Bluh\n"
                        + "{/if}\n";
        String expectedPhpExprText =
                "((isset($opt_data['boo']) ? $opt_data['boo'] : null) ? 'Blah' : (! (isset($opt_data['goo']) ? $opt_data['goo'] : null) ? 'Bleh' : 'Bluh'))";

        assertThatSoyExpr(soyNodeCode).compilesTo(
                new PhpExpr(expectedPhpExprText,
                        PhpExprUtils.phpPrecedenceForOperator(Operator.CONDITIONAL)));
    }

    public void testIf_nested() {
        String soyNodeCode =
                "{if $boo}\n"
                        + "  {if $goo}\n"
                        + "    Blah\n"
                        + "  {/if}\n"
                        + "{else}\n"
                        + "  Bleh\n"
                        + "{/if}\n";
        String expectedPhpExprText =
                "((isset($opt_data['boo']) ? $opt_data['boo'] : null) ? (((isset($opt_data['goo']) ? $opt_data['goo'] : null) ? 'Blah' : '')) : 'Bleh')";

        assertThatSoyExpr(soyNodeCode).compilesTo(
                new PhpExpr(expectedPhpExprText,
                        PhpExprUtils.phpPrecedenceForOperator(Operator.CONDITIONAL)));
    }

    public void testSimpleMsgFallbackGroupNodeWithOneNode() {
        String soyCode =
                "{msg meaning=\"verb\" desc=\"Used as a verb.\"}\n"
                        + "  Archive\n"
                        + "{/msg}\n";

        String expectedPhpCode =
                "Translator::renderLiteral("
                        + "Translator::prepareLiteral("
                        + "###, "
                        + "'Archive', 'Used as a verb.', 'verb'))";

        assertThatSoyExpr(soyCode).compilesTo(new PhpExpr(expectedPhpCode, Integer.MAX_VALUE));
    }

    public void testMsgFallbackGroupNodeWithTwoNodes() {
        String soyCode =
                "{msg meaning=\"verb\" desc=\"Used as a verb.\"}\n"
                        + "  archive\n"
                        + "{fallbackmsg desc=\"\"}\n"
                        + "  ARCHIVE\n"
                        + "{/msg}\n";

        String expectedPhpCode =
                "Translator::isMsgAvailable(###) || !Translator::isMsgAvailable(###) ? "
                        + "Translator::renderLiteral("
                        + "Translator::prepareLiteral("
                        + "###, "
                        + "'archive', 'Used as a verb.', 'verb')) : "
                        + "Translator::renderLiteral("
                        + "Translator::prepareLiteral(###, 'ARCHIVE', '', null))";

        assertThatSoyExpr(soyCode).compilesTo(new PhpExpr(expectedPhpCode,
                PhpExprUtils.phpPrecedenceForOperator(Operator.CONDITIONAL)));
    }

    public void testMsgOnlyLiteral() {
        String soyCode =
                "{msg meaning=\"verb\" desc=\"The word 'Archive' used as a verb.\"}"
                        + "Archive"
                        + "{/msg}\n";

        String expectedPhpCode =
                "Translator::renderLiteral("
                        + "Translator::prepareLiteral("
                        + "###, "
                        + "'Archive', 'The word \\'Archive\\' used as a verb.', 'verb'))";

        assertThatSoyExpr(soyCode).compilesTo(new PhpExpr(expectedPhpCode, Integer.MAX_VALUE));
    }

    public void testMsgOnlyLiteralWithBraces() {
        String soyCode =
                "{msg meaning=\"verb\" desc=\"The word 'Archive' used as a verb.\"}"
                        + "{lb}Archive{rb}"
                        + "{/msg}\n";
        String expectedPhpCode =
                "Translator::renderLiteral("
                        + "Translator::prepareLiteral("
                        + "###, "
                        + "'{Archive}', 'The word \\'Archive\\' used as a verb.', 'verb'))";

        assertThatSoyExpr(soyCode).compilesTo(new PhpExpr(expectedPhpCode, Integer.MAX_VALUE));
    }

    public void testMsgSimpleSoyExpression() {
        String soyCode =
                "{msg desc=\"var placeholder\"}"
                        + "Hello {$username}"
                        + "{/msg}\n";

        String expectedPhpCode =
                "Translator::render("
                        + "Translator::prepare("
                        + "###, "
                        + "'Hello {USERNAME}', "
                        + "['USERNAME'], 'var placeholder', null), "
                        + "['USERNAME' => isset($opt_data['username']) ? $opt_data['username'] : null])";

        assertThatSoyExpr(soyCode).compilesTo(new PhpExpr(expectedPhpCode, Integer.MAX_VALUE));
    }

    public void testMsgMultipleSoyExpressions() {
        String soyCode =
                "{msg desc=\"var placeholder\"}"
                        + "{$greet} {$username}"
                        + "{/msg}\n";

        String expectedPhpCode =
                "Translator::render("
                        + "Translator::prepare("
                        + "###, "
                        + "'{GREET} {USERNAME}', "
                        + "['GREET', 'USERNAME'], 'var placeholder', null), "
                        + "["
                        + "'GREET' => isset($opt_data['greet']) ? $opt_data['greet'] : null, "
                        + "'USERNAME' => isset($opt_data['username']) ? $opt_data['username'] : null"
                        + "])";

        assertThatSoyExpr(soyCode).compilesTo(new PhpExpr(expectedPhpCode, Integer.MAX_VALUE));
    }

    public void testMsgMultipleSoyExpressionsWithBraces() {
        String soyCode =
                "{msg desc=\"var placeholder\"}"
                        + "{$greet} {lb}{$username}{rb}"
                        + "{/msg}\n";

        String expectedPhpCode =
                "Translator::render("
                        + "Translator::prepare("
                        + "###, "
                        + "'{GREET} {{USERNAME}}', "
                        + "['GREET', 'USERNAME'], 'var placeholder', null), "
                        + "["
                        + "'GREET' => isset($opt_data['greet']) ? $opt_data['greet'] : null, "
                        + "'USERNAME' => isset($opt_data['username']) ? $opt_data['username'] : null"
                        + "])";

        assertThatSoyExpr(soyCode).compilesTo(new PhpExpr(expectedPhpCode, Integer.MAX_VALUE));
    }

    public void testMsgNamespacedSoyExpression() {
        String soyCode =
                "{msg desc=\"placeholder with namespace\"}"
                        + "Hello {$foo.bar}"
                        + "{/msg}\n";

        String expectedPhpCode =
                "Translator::render("
                        + "Translator::prepare("
                        + "###, "
                        + "'Hello {BAR}', "
                        + "['BAR'], 'placeholder with namespace', null), "
                        + "['BAR' => isset($opt_data['foo']['bar']) ? $opt_data['foo']['bar'] : null])";

        assertThatSoyExpr(soyCode).compilesTo(new PhpExpr(expectedPhpCode, Integer.MAX_VALUE));
    }
    public void testMsgWithArithmeticExpression() {
        String soyCode =
                "{msg desc=\"var placeholder\"}"
                        + "Hello {$username + 1}"
                        + "{/msg}\n";

        String expectedPhpCode =
                "Translator::render("
                        + "Translator::prepare("
                        + "###, "
                        + "'Hello {XXX}', "
                        + "['XXX'], 'var placeholder', null), "
                        + "['XXX' => Runtime::typeSafeAdd(isset($opt_data['username']) ? $opt_data['username'] : null, 1)])";

        assertThatSoyExpr(soyCode).compilesTo(new PhpExpr(expectedPhpCode, Integer.MAX_VALUE));
    }
    public void testMsgWithHtmlNode() {
        // msg with HTML tags and raw texts
        String soyCode =
                "{msg desc=\"with link\"}"
                        + "Please click <a href='{$url}'>here</a>."
                        + "{/msg}";

        String expectedPhpCode =
                "Translator::render("
                        + "Translator::prepare("
                        + "###, "
                        + "'Please click {START_LINK}here{END_LINK}.', "
                        + "['START_LINK', 'END_LINK'], 'with link', null), "
                        + "["
                        + "'START_LINK' => '<a href=\\''.(isset($opt_data['url']) ? $opt_data['url'] : null).'\\'>', "
                        + "'END_LINK' => '</a>'"
                        + "])";

        assertThatSoyExpr(soyCode).compilesTo(new PhpExpr(expectedPhpCode, Integer.MAX_VALUE));
    }

    public void testMsgWithPlural() {
        String soyCode =
                "{msg desc=\"simple plural\"}"
                        + "{plural $numDrafts}"
                        + "{case 0}No drafts"
                        + "{case 1}1 draft"
                        + "{default}{$numDrafts} drafts"
                        + "{/plural}"
                        + "{/msg}";

        String expectedPhpCode =
                "Translator::renderPlural("
                        + "Translator::preparePlural("
                        + "###, "
                        + "["
                        + "'=0' => 'No drafts', "
                        + "'=1' => '1 draft', "
                        + "'other' => '{NUM_DRAFTS_2} drafts'"
                        + "], "
                        + "['NUM_DRAFTS_1', 'NUM_DRAFTS_2'], 'simple plural', null), "
                        + "isset($opt_data['numDrafts']) ? $opt_data['numDrafts'] : null, "
                        + "["
                        + "'NUM_DRAFTS_1' => isset($opt_data['numDrafts']) ? $opt_data['numDrafts'] : null, "
                        + "'NUM_DRAFTS_2' => isset($opt_data['numDrafts']) ? $opt_data['numDrafts'] : null"
                        + "])";

        assertThatSoyExpr(soyCode).compilesTo(new PhpExpr(expectedPhpCode, Integer.MAX_VALUE));
    }

    public void testMsgWithPluralAndOffset() {
        String soyCode =
                "{msg desc=\"offset plural\"}"
                        + "{plural $numDrafts offset=\"2\"}"
                        + "{case 0}No drafts"
                        + "{case 1}1 draft"
                        + "{default}{remainder($numDrafts)} drafts"
                        + "{/plural}"
                        + "{/msg}";

        String expectedPhpCode =
                "Translator::renderPlural("
                        + "Translator::preparePlural("
                        + "###, "
                        + "["
                        + "'=0' => 'No drafts', "
                        + "'=1' => '1 draft', "
                        + "'other' => '{XXX} drafts'"
                        + "], "
                        + "['NUM_DRAFTS', 'XXX'], 'offset plural', null), "
                        + "isset($opt_data['numDrafts']) ? $opt_data['numDrafts'] : null, "
                        + "["
                        + "'NUM_DRAFTS' => isset($opt_data['numDrafts']) ? $opt_data['numDrafts'] : null, "
                        + "'XXX' => (isset($opt_data['numDrafts']) ? $opt_data['numDrafts'] : null) - 2"
                        + "])";

        assertThatSoyExpr(soyCode).compilesTo(new PhpExpr(expectedPhpCode, Integer.MAX_VALUE));
    }

    public void testMsgWithSelect() {
        String soyCode =
                "{msg desc=\"...\"}\n"
                        + "  {select $userGender}\n"
                        + "    {case 'female'}\n"
                        + "      {select $targetGender}\n"
                        + "        {case 'female'}Reply to her.{case 'male'}Reply to him.{default}Reply to them.\n"
                        + "      {/select}\n"
                        + "    {case 'male'}\n"
                        + "      {select $targetGender}\n"
                        + "        {case 'female'}Reply to her.{case 'male'}Reply to him.{default}Reply to them.\n"
                        + "      {/select}\n"
                        + "    {default}\n"
                        + "      {select $targetGender}\n"
                        + "        {case 'female'}Reply to her.{case 'male'}Reply to him.{default}Reply to them.\n"
                        + "      {/select}\n"
                        + "   {/select}\n"
                        + "{/msg}\n";

        String expectedPhpCode =
                "Translator::renderIcu("
                        + "Translator::prepareIcu("
                        + "###, "
                        + "'{USER_GENDER,select,"
                        + "female{"
                        + "{TARGET_GENDER,select,"
                        + "female{Reply to her.}"
                        + "male{Reply to him.}"
                        + "other{Reply to them.}}"
                        + "}"
                        + "male{"
                        + "{TARGET_GENDER,select,"
                        + "female{Reply to her.}"
                        + "male{Reply to him.}"
                        + "other{Reply to them.}}"
                        + "}"
                        + "other{"
                        + "{TARGET_GENDER,select,"
                        + "female{Reply to her.}"
                        + "male{Reply to him.}"
                        + "other{Reply to them.}}"
                        + "}"
                        + "}', "
                        + "['USER_GENDER', 'TARGET_GENDER'], '...', null), "
                        + "["
                        + "'USER_GENDER' => isset($opt_data['userGender']) ? $opt_data['userGender'] : null, "
                        + "'TARGET_GENDER' => isset($opt_data['targetGender']) ? $opt_data['targetGender'] : null"
                        + "])";

        assertThatSoyExpr(soyCode).compilesTo(new PhpExpr(expectedPhpCode, Integer.MAX_VALUE));
    }

    public void testMsgWithPluralWithGender() {
        String soyCode =
                "{msg genders=\"$people[0]?.gender, $people[1]?.gender\" desc=\"plural with offsets\"}\n"
                        + "  {plural length($people)}\n"
                        + "    {case 1}{$people[0].name} is attending\n"
                        + "    {case 2}{$people[0].name} and {$people[1]?.name} are attending\n"
                        + "    {case 3}{$people[0].name}, {$people[1]?.name}, and 1 other are attending\n"
                        + "    {default}{$people[0].name}, {$people[1]?.name}, and length($people) others\n"
                        + "  {/plural}\n"
                        + "{/msg}\n";

        String expectedPhpCode = "Translator::renderIcu("
                + "Translator::prepareIcu("
                + "###, "
                + "'{PEOPLE_0_GENDER,select,"
                + "female{{PEOPLE_1_GENDER,select,"
                + "female{{NUM,plural,"
                + "=1{{NAME_1} is attending}"
                + "=2{{NAME_1} and {NAME_2} are attending}"
                + "=3{{NAME_1}, {NAME_2}, and 1 other are attending}"
                + "other{{NAME_1}, {NAME_2}, and length($people) others}}"
                + "}"
                + "male{{NUM,plural,"
                + "=1{{NAME_1} is attending}"
                + "=2{{NAME_1} and {NAME_2} are attending}"
                + "=3{{NAME_1}, {NAME_2}, and 1 other are attending}"
                + "other{{NAME_1}, {NAME_2}, and length($people) others}}"
                + "}"
                + "other{{NUM,plural,"
                + "=1{{NAME_1} is attending}"
                + "=2{{NAME_1} and {NAME_2} are attending}"
                + "=3{{NAME_1}, {NAME_2}, and 1 other are attending}"
                + "other{{NAME_1}, {NAME_2}, and length($people) others}}}"
                + "}"
                + "}"
                + "male{{PEOPLE_1_GENDER,select,"
                + "female{{NUM,plural,"
                + "=1{{NAME_1} is attending}"
                + "=2{{NAME_1} and {NAME_2} are attending}"
                + "=3{{NAME_1}, {NAME_2}, and 1 other are attending}"
                + "other{{NAME_1}, {NAME_2}, and length($people) others}}"
                + "}"
                + "male{{NUM,plural,"
                + "=1{{NAME_1} is attending}"
                + "=2{{NAME_1} and {NAME_2} are attending}"
                + "=3{{NAME_1}, {NAME_2}, and 1 other are attending}"
                + "other{{NAME_1}, {NAME_2}, and length($people) others}}"
                + "}"
                + "other{{NUM,plural,"
                + "=1{{NAME_1} is attending}"
                + "=2{{NAME_1} and {NAME_2} are attending}"
                + "=3{{NAME_1}, {NAME_2}, and 1 other are attending}"
                + "other{{NAME_1}, {NAME_2}, and length($people) others}}}"
                + "}"
                + "}"
                + "other{{PEOPLE_1_GENDER,select,"
                + "female{{NUM,plural,"
                + "=1{{NAME_1} is attending}"
                + "=2{{NAME_1} and {NAME_2} are attending}"
                + "=3{{NAME_1}, {NAME_2}, and 1 other are attending}"
                + "other{{NAME_1}, {NAME_2}, and length($people) others}}"
                + "}"
                + "male{{NUM,plural,"
                + "=1{{NAME_1} is attending}"
                + "=2{{NAME_1} and {NAME_2} are attending}"
                + "=3{{NAME_1}, {NAME_2}, and 1 other are attending}"
                + "other{{NAME_1}, {NAME_2}, and length($people) others}}"
                + "}"
                + "other{{NUM,plural,"
                + "=1{{NAME_1} is attending}"
                + "=2{{NAME_1} and {NAME_2} are attending}"
                + "=3{{NAME_1}, {NAME_2}, and 1 other are attending}"
                + "other{{NAME_1}, {NAME_2}, and length($people) others}}}"
                + "}"
                + "}"
                + "}', "
                + "['PEOPLE_0_GENDER', 'PEOPLE_1_GENDER', 'NUM', 'NAME_1', 'NAME_2'], 'plural with offsets', null), "
                + "["
                + "'PEOPLE_0_GENDER' => isset($opt_data['people'][0]['gender']) ? $opt_data['people'][0]['gender'] : null, "
                + "'PEOPLE_1_GENDER' => isset($opt_data['people'][1]['gender']) ? $opt_data['people'][1]['gender'] : null, "
                + "'NUM' => count(isset($opt_data['people']) ? $opt_data['people'] : null), "
                + "'NAME_1' => isset($opt_data['people'][0]['name']) ? $opt_data['people'][0]['name'] : null, "
                + "'NAME_2' => isset($opt_data['people'][1]['name']) ? $opt_data['people'][1]['name'] : null"
                + "]"
                + ")";

        assertThatSoyExpr(soyCode).compilesTo(new PhpExpr(expectedPhpCode, Integer.MAX_VALUE));
    }


    public void testMsgWithApostrophe() {
        String soyCode =
                "{msg meaning=\"'verb'\" desc=\"The word 'Archive' used as a verb.\"}"
                        + "'Archive'"
                        + "{/msg}\n";

        String expectedPhpCode =
                "Translator::renderLiteral("
                        + "Translator::prepareLiteral("
                        + "###, "
                        + "'\\'Archive\\'', 'The word \\'Archive\\' used as a verb.', '\\'verb\\''))";

        assertThatSoyExpr(soyCode).compilesTo(new PhpExpr(expectedPhpCode, Integer.MAX_VALUE));
    }
}
