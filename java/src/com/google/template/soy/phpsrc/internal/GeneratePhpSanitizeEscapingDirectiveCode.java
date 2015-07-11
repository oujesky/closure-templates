/*
 * Copyright 2014 Google Inc.
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

import com.google.template.soy.shared.internal.AbstractGenerateSoyEscapingDirectiveCode;
import com.google.template.soy.shared.internal.DirectiveDigest;
import com.google.template.soy.shared.restricted.EscapingConventions;
import com.google.template.soy.shared.restricted.EscapingConventions.EscapingLanguage;
import com.google.template.soy.shared.restricted.TagWhitelist;


import java.io.IOException;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.ParametersAreNonnullByDefault;


/**
 * Generates PHP code in GeneratedSanitize.php used by the public functions in Sanitize.php.
 *
 * <p>
 * This is an ant task and can be invoked as:
 * <xmp>
 *   <taskdef name="gen.escape.directives"
 *    classname="com.google.template.soy.phpsrc.internal.GeneratePhpSanitizeEscapingDirectiveCode">
 *     <classpath>
 *       <!-- classpath to Soy classes and dependencies -->
 *     </classpath>
 *   </taskdef>
 *   <gen.escape.directives>
 *     <input path="one or more PHP files that use the generated helpers"/>
 *     <output path="the output PHP file"/>
 *   </gen.escape.directives>
 * </xmp>
 *
 * <p>
 * In the above, the first {@code <taskdef>} is an Ant builtin which links the element named
 * {@code <gen.escape.directives>} to this class.
 * <p>
 * That element contains zero or more {@code <input>}s which are PHP source files that may
 * use the helper functions generated by this task.
 * <p>
 * There must be exactly one {@code <output>} element which specifies where the output should be
 * written.  That output contains the input sources and the generated helper functions.
 *
 */
@ParametersAreNonnullByDefault
public final class GeneratePhpSanitizeEscapingDirectiveCode
        extends AbstractGenerateSoyEscapingDirectiveCode {

    @Override protected EscapingLanguage getLanguage() {
        return EscapingLanguage.PHP;
    }

    @Override protected String getLineCommentSyntax() {
        return "//";
    }

    @Override protected String getLineEndSyntax() { return ";"; }

    @Override protected String getRegexStart() {
        return "'~";
    }

    @Override protected String getRegexEnd() {
        return "~u'";
    }

    @Override protected String escapeOutputString(String input) {
        String escapeCharacters = "\\\'\"\b\f\n\r\t";

        // Give the string builder a little bit of extra space to account for new escape characters.
        StringBuilder result = new StringBuilder((int) (input.length() * 1.2));
        for (char c : input.toCharArray()) {
            if (escapeCharacters.indexOf(c) != -1) {
                result.append('\\');
            }
            result.append(c);
        }

        return result.toString();
    }

    @Override protected String convertFromJavaRegex(Pattern javaPattern) {
        String body = javaPattern.pattern()
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\u2028", "\\u2028")
                .replace("\u2029", "\\u2029")
                .replace("\\z", "\\Z")
                .replace("'", "\\'");

        // DOTALL is not allowed to keep the syntax simple (it's also not available in JavaScript).
        if ((javaPattern.flags() & Pattern.DOTALL) != 0) {
            throw new IllegalArgumentException("Pattern " + javaPattern + " uses DOTALL.");
        }

        StringBuilder buffer = new StringBuilder(body.length() + 40);
        // Default to using unicode character classes.
        buffer.append("'~");
        buffer.append(body);
        buffer.append("~u");
        if ((javaPattern.flags() & Pattern.CASE_INSENSITIVE) != 0) {
            buffer.append("i");
        }
        if ((javaPattern.flags() & Pattern.MULTILINE) != 0) {
            buffer.append("m");
        }
        buffer.append("';");
        return buffer.toString();
    }

    @Override protected void generatePrefix(StringBuilder outputCode) {
        outputCode.replace(0, 0, "<?php\n");

        outputCode
                .append("\n")
                .append("namespace Goog\\Soy;\n")
                .append("\n")
                .append("class GeneratedSanitize \n")
                .append("{\n")
                .append("\n");
    }

    @Override protected void generateCharacterMapSignature(StringBuilder outputCode, String mapName) {
        outputCode.append("private static $_ESCAPE_MAP_FOR_").append(mapName).append("");
    }

    @Override protected void generateMatcher(StringBuilder outputCode, String name, String matcher) {
        outputCode.append("\nconst _MATCHER_FOR_").append(name).append(" = ").append(convertPhpRegex(matcher)).append(";\n");
    }

    @Override protected void generateFilter(StringBuilder outputCode, String name, String filter) {
        outputCode.append("\nconst _FILTER_FOR_").append(name).append(" = ").append(convertPhpRegex(filter)).append("\n");
    }

    private String convertPhpRegex(String regex) {

        return regex
                // PHP PCRE does not support uXXXX syntax, we need to convert to x{XXXX}
                .replaceAll("\\\\u([a-fA-F0-9]{4})", "\\\\x{$1}")
                // \\ => \\\\ for literal slash
                .replaceAll("\\\\\\\\", "\\\\\\\\\\\\\\\\");

    }

    @Override protected void generateReplacerFunction(StringBuilder outputCode, String mapName) {
        outputCode
                .append("\nprivate static function _REPLACER_FOR_")
                .append(mapName)
                .append("($match) {\n")
                .append("  $ch = $match[0];\n")
                .append("  $map = self::$_ESCAPE_MAP_FOR_").append(mapName).append(";\n")
                .append("  return isset($map[$ch]) ? $map[$ch] : '';\n")
                .append("}\n");
    }

    @Override protected void useExistingLibraryFunction(StringBuilder outputCode, String identifier,
                                                        String existingFunction) {
        outputCode
                .append("\npublic static function ").append(identifier).append("Helper($v) {\n")
                .append("  return ").append(existingFunction).append("((string)$v);\n")
                .append("}\n");
    }

    @Override protected void generateHelperFunction(StringBuilder outputCode,
                                                    DirectiveDigest digest) {
        String fnName = digest.getDirectiveName();
        outputCode
                .append("\npublic static function ").append(fnName).append("Helper($value) {\n")
                .append("  $value = (string)$value;\n");
        if (digest.getFilterName() != null) {
            String filterName = digest.getFilterName();
            outputCode
                    .append("  if (!preg_match(self::_FILTER_FOR_").append(filterName).append(", $value)) {\n");
            // TODO(dcphillips): Raising a debugging assertion error could be useful.
            outputCode
                    .append("    return '").append(digest.getInnocuousOutput()).append("';\n")
                    .append("  }\n");
        }

        if (digest.getNonAsciiPrefix() != null) {
            // TODO: We can add a second replace of all non-ascii codepoints below.
//            throw new UnsupportedOperationException("Non ASCII prefix escapers not implemented yet.");
        }
        if (digest.getEscapesName() != null) {
            String escapeMapName = digest.getEscapesName();
            String matcherName = digest.getMatcherName();
            outputCode
                    .append("  return preg_replace_callback(self::_MATCHER_FOR_").append(matcherName).append(", \n")
                    .append("      'Goog\\Soy\\GeneratedSanitize::_REPLACER_FOR_").append(escapeMapName).append("', $value);\n");
        } else {
            outputCode.append("  return $value;\n");
        }
        outputCode.append("}\n");
    }

    @Override protected void generateCommonConstants(StringBuilder outputCode) {
        // Emit patterns and constants needed by escaping functions that are not part of any one
        // escaping convention.
        outputCode.append("const _HTML_TAG_REGEX = ")
                .append(convertFromJavaRegex(EscapingConventions.HTML_TAG_CONTENT))
                .append("\n\n")
                .append("const _LT_REGEX = '~<~';")
                .append("\n\n")
                .append("public static $_SAFE_TAG_WHITELIST = ")
                .append(toPhpStringArray(TagWhitelist.FORMATTING.asSet()))
                .append(";\n\n");



        replaceObjectsToPhp(outputCode);


    }

    private void replaceObjectsToPhp(StringBuilder outputCode) {

        // this is little bit hackish, but the parent implementation is
        // prepared mostly for javascript code at the moment with not many
        // overridable methods

        // we have to transform JS style objects to PHP
        String code = outputCode.toString()
            // opening brace
            .replaceAll("= \\{", "= [")
            // closing brace
            .replaceAll("\\};", "];")
            // key => value assignment
            .replaceAll("': '", "' => '")
            // '\"' => '"'
            .replaceAll("'\\\\\"'", "'\"'")
            // utf-8 chars '\x00' => "\x00"
            .replaceAll("'(\\\\x[a-fA-F0-9]{2})'", "\"$1\"");


        // unicode codepoints '\u2028' => "\xe2\x80\xa8"
        StringBuffer buffer = new StringBuffer();
        Pattern p = Pattern.compile("'\\\\u([a-fA-F0-9]{4})'");
        Matcher m = p.matcher(code);
        while (m.find())
        {
            // transform hexadecimal codepoint to integer
            int codePoints[] = new int[] {Integer.parseInt(m.group(1), 16)};
            // create string from codepoint
            String s = new String(codePoints, 0, 1);

            StringBuilder sb = new StringBuilder();
            sb.append("\"");

            // iterate through bytes of utf-8 representation
            for (byte b : s.getBytes(Charset.forName("UTF-8")))
            {
                // write hexadecimal value of utf-8 byte
                sb.append("\\\\x").append(Integer.toHexString(b & 0xff));
            }
            sb.append("\"");
            m.appendReplacement(buffer, sb.toString());
        }
        m.appendTail(buffer);

        // replace buffer with transformed code
        outputCode.setLength(0);
        outputCode.append(buffer);

        outputCode.append("}");
    }

    /** ["foo", "bar"]' */
    private String toPhpStringArray(Iterable<String> strings) {
        StringBuilder sb = new StringBuilder();
        boolean isFirst = true;
        sb.append('[');
        for (String str : strings) {
            if (!isFirst) { sb.append(", "); }
            isFirst = false;
            writeStringLiteral(str, sb);
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * A non Ant interface for this class.
     */
    public static void main(String[] args) throws IOException {
        GeneratePhpSanitizeEscapingDirectiveCode generator =
                new GeneratePhpSanitizeEscapingDirectiveCode();
        generator.configure(args);
        generator.execute();
    }
}
