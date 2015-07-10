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

/**
 * Value class to represent a PHP array expression. Includes the text of the expression as well
 * as the precedence of the top-most operator.
 *
 * <p> Important: This class may only be used in implementing plugins (e.g. functions, directives).
 */
public final class PhpArrayExpr extends PhpExpr {

    /**
     * @param text The PHP expression text.
     * @param precedence The precedence of the top-most operator. Or Integer.MAX_VALUE.
     */
    public PhpArrayExpr(String text, int precedence) {
        super(text, precedence);
    }

    @Override public PhpStringExpr toPhpString() {
        // Arrays are converted by concatenating all of their values.
        return new PhpStringExpr("implode('', " + getText() + ")", Integer.MAX_VALUE);
    }
}
