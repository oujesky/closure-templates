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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.internal.targetexpr.ExprUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Common utilities for dealing with PHP expressions.
 *
 * <p> Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 */
public final class PhpExprUtils {

    /** The variable name used to reference the current translator instance. */
    public static final String TRANSLATOR_NAME = "Translator";

    /** Expression constant for empty string. */
    private static final PhpExpr EMPTY_STRING = new PhpStringExpr("''");

    /**
     * Map used to provide operator precedences in PHP.
     *
     * @see <a href="http://php.net/manual/en/language.operators.precedence.php"> PHP
     *      operator precedence.</a>
     */
    private static final ImmutableMap<Operator, Integer> PHP_PRECEDENCES =
            new ImmutableMap.Builder<Operator, Integer>()
                    .put(Operator.NEGATIVE, 8)
                    .put(Operator.TIMES, 7)
                    .put(Operator.DIVIDE_BY, 7)
                    .put(Operator.MOD, 7)
                    .put(Operator.PLUS, 6)
                    .put(Operator.MINUS, 6)
                    .put(Operator.LESS_THAN, 5)
                    .put(Operator.GREATER_THAN, 5)
                    .put(Operator.LESS_THAN_OR_EQUAL, 5)
                    .put(Operator.GREATER_THAN_OR_EQUAL, 5)
                    .put(Operator.EQUAL, 5)
                    .put(Operator.NOT_EQUAL, 5)
                    .put(Operator.NOT, 4)
                    .put(Operator.AND, 3)
                    .put(Operator.OR, 2)
                    .put(Operator.NULL_COALESCING, 1)
                    .put(Operator.CONDITIONAL, 1)
                    .build();


    private PhpExprUtils() {}

    /**
     * Builds one PHP expression that computes the concatenation of the given PHP expressions.
     *
     * @param phpExprs The PHP expressions to concatenate.
     * @return One PHP expression that computes the concatenation of the given PHP expressions.
     */
    public static PhpExpr concatPhpExprs(List<? extends PhpExpr> phpExprs) {

        if (phpExprs.isEmpty()) {
            return EMPTY_STRING;
        }

        if (phpExprs.size() == 1) {
            // If there's only one element, simply return the expression as a String.
            return phpExprs.get(0).toPhpString();
        }

        StringBuilder resultSb = new StringBuilder();

        boolean isFirst = true;
        for (PhpExpr phpExpr : phpExprs) {

            if (isFirst) {
                isFirst = false;
            } else {
                resultSb.append('.');
            }

            resultSb.append(phpExpr.toPhpString().getText());
        }

        return new PhpStringExpr(resultSb.toString(), Integer.MAX_VALUE);
    }

    /**
     * Generate a PHP not null check expression for a given PhpExpr.
     *
     * @param phpExpr The input expression to test.
     * @return A PhpExpr containing the null check.
     */
    public static PhpExpr genPhpNotNullCheck(PhpExpr phpExpr) {
        List<PhpExpr> exprs = ImmutableList.of(phpExpr, new PhpExpr("null", Integer.MAX_VALUE));

        String conditionalExpr = ExprUtils.genExprWithNewToken(Operator.NOT_EQUAL, exprs, "!==");
        return new PhpExpr(conditionalExpr, PhpExprUtils.phpPrecedenceForOperator(Operator.NOT_EQUAL));
    }

    /**
     * Wraps an expression with parenthesis if it's not above the minimum safe precedence.
     *
     * <p>NOTE: For the sake of brevity, this implementation loses typing information in the
     * expressions.
     *
     * @param expr The expression to wrap.
     * @param minSafePrecedence The minimum safe precedence (not inclusive).
     * @return The PhpExpr potentially wrapped in parenthesis.
     */
    public static PhpExpr maybeProtect(PhpExpr expr, int minSafePrecedence) {
        if (expr.getPrecedence() > minSafePrecedence) {
            return expr;
        } else {
            return new PhpExpr("(" + expr.getText() + ")", Integer.MAX_VALUE);
        }
    }

    /**
     * Wraps an expression with the proper SanitizedContent constructor.
     *
     * @param contentKind The kind of sanitized content.
     * @param phpExpr The expression to wrap.
     */
    public static PhpExpr wrapAsSanitizedContent(ContentKind contentKind, PhpExpr phpExpr) {
        String sanitizer = NodeContentKinds.toPhpSanitizedContentOrdainer(contentKind);
        return new PhpExpr("new " + sanitizer + "(" + phpExpr.getText() + ")", Integer.MAX_VALUE);
    }

    /**
     * Provide the PHP operator precedence for a given operator.
     *
     * @param op The operator.
     * @return The PHP precedence as an integer.
     */
    public static int phpPrecedenceForOperator(Operator op) {
        return PHP_PRECEDENCES.get(op);
    }

    /**
     * Convert a java Iterable object to valid PhpExpr as array.
     * @param iterable Iterable of Objects to be converted to PhpExpr, it must be Number, PhpExpr or
     *        String.
     */
    public static PhpExpr convertIterableToPhpArrayExpr(Iterable<?> iterable) {
        return convertIterableToPhpExpr(iterable);
    }

    /**
     * Convert a java Map to valid PhpExpr as associative array.
     *
     * @param assoc A Map to be converted to PhpExpr as a dictionary, both key and value should be
     *        PhpExpr.
     */
    public static PhpExpr convertMapToPhpExpr(Map<PhpExpr, PhpExpr> assoc) {
        List<String> values = new ArrayList<>();

        for (Map.Entry<PhpExpr, PhpExpr> entry : assoc.entrySet()) {
            values.add(entry.getKey().getText() + " => " + entry.getValue().getText());
        }

        Joiner joiner = Joiner.on(", ");
        return new PhpExpr("[" + joiner.join(values) + "]", Integer.MAX_VALUE);
    }

    private static PhpExpr convertIterableToPhpExpr(Iterable<?> iterable) {
        List<String> values = new ArrayList<>();
        String leftDelimiter = "[";
        String rightDelimiter = "]";

        for (Object elem : iterable) {
            if (!(elem instanceof Number || elem instanceof String || elem instanceof PhpExpr)) {
                throw new UnsupportedOperationException("Only Number, String and PhpExpr is allowed");
            } else if (elem instanceof Number) {
                values.add(String.valueOf(elem));
            } else if (elem instanceof PhpExpr) {
                values.add(((PhpExpr) elem).getText());
            } else if (elem instanceof String) {
                values.add("'" + elem + "'");
            }
        }

        String contents = Joiner.on(", ").join(values);

        return new PhpArrayExpr(leftDelimiter + contents + rightDelimiter, Integer.MAX_VALUE);
    }


    /**
     * List of PHP reserved keywords
     *
     * @see <a href="http://php.net/manual/en/reserved.keywords.php"> PHP Reserved keywords.</a>
     */
    public static final ImmutableList<String> PHP_KEYWORDS = ImmutableList.of(
        "__halt_compiler", "abstract", "and", "array", "as", "break", "callable", "case",
        "catch", "class", "clone", "const", "continue",	"declare", "default", "die",
        "do", "echo", "else", "elseif", "empty", "enddeclare", "endfor", "endforeach", "endif",
        "endswitch", "endwhile", "eval", "exit", "extends", "final", "finally", "for", "foreach",
        "function", "global", "goto", "if", "implements", "include", "include_once", "instanceof",
        "insteadof", "interface", "isset", "list", "namespace", "new", "or", "print", "private",
        "protected", "public", "require", "require_once", "return", "static", "switch", "throw",
        "trait", "try", "unset", "use", "var", "while", "xor", "yield"
    );


    /**
     * Escapes PHP method name
     *
     * @param phpMethodName Unescpaed PHP method name
     * @return Escaped PHP method name
     */
    public static String escapePhpMethodName(String phpMethodName) {

        // if PHP method name is one of PHP reserved keywords, add _ at the end
        if (PHP_KEYWORDS.contains(phpMethodName))
        {
            return phpMethodName + "_";
        }

        return phpMethodName;
    }
}
