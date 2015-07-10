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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 *
 * A class for building code for a function call expression in PHP.
 * It builds to a PhpExpr so it could be used function call code recursively.
 *
 * <p> Sample Output:
 * {@code some_func_call(1, "str", 'bar', nested_call(42))}
 *
 *
 */
public final class PhpFunctionExprBuilder {

    private static final Function<PhpExpr, String> LIST_ARG_MAPPER =
            new Function<PhpExpr, String>() {
                @Override
                public String apply(PhpExpr arg) {
                    return arg.getText();
                }
            };

    private final String funcName;
    private final Deque<PhpExpr> argList;

    /**
     * @param funcName The name of the function.
     */
    public PhpFunctionExprBuilder(String funcName) {
        this.funcName = funcName;
        this.argList = new ArrayDeque<>();
    }

    public PhpFunctionExprBuilder addArg(PhpExpr arg) {
        this.argList.add(arg);
        return this;
    }

    public PhpFunctionExprBuilder addArg(String str) {
        this.argList.add(new PhpStringExpr("'" + str + "'"));
        return this;
    }

    public PhpFunctionExprBuilder addArg(boolean b) {
        this.argList.add(new PhpExpr(b ? "true" : "false", Integer.MAX_VALUE));
        return this;
    }

    public PhpFunctionExprBuilder addArg(int i) {
        this.argList.add(new PhpExpr(String.valueOf(i), Integer.MAX_VALUE));
        return this;
    }

    public PhpFunctionExprBuilder addArg(double i) {
        this.argList.add(new PhpExpr(String.valueOf(i), Integer.MAX_VALUE));
        return this;
    }

    public PhpFunctionExprBuilder addArg(long i) {
        this.argList.add(new PhpExpr(String.valueOf(i), Integer.MAX_VALUE));
        return this;
    }

    public String getFuncName() {
        return this.funcName;
    }


    /**
     *  Returns a valid PHP function call as a String.
     */
    public String build() {
        StringBuilder sb = new StringBuilder(funcName + "(");

        Joiner joiner = Joiner.on(", ").skipNulls();

        // Join args and kwargs into simple strings.
        String args = joiner.join(Iterables.transform(argList, LIST_ARG_MAPPER));

        // Strip empty strings.
        args = Strings.emptyToNull(args);

        // Join all pieces together.
        joiner.appendTo(sb, args, null);

        sb.append(")");
        return sb.toString();
    }


    /*
     * Use when the output function is unknown in PHP runtime.
     *
     * @return A PhpExpr represents the function code.
     */
    public PhpExpr asPhpExpr() {
        return new PhpExpr(build(), Integer.MAX_VALUE);
    }

    /*
     * Use when the output function is known to be a String in PHP runtime.
     *
     * @return A PhpStringExpr represents the function code.
     */
    public PhpStringExpr asPhpStringExpr() {
        return new PhpStringExpr(build());
    }
}
