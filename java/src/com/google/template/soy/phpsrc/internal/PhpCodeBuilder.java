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

import com.google.common.base.Preconditions;
import com.google.template.soy.phpsrc.restricted.PhpExpr;
import com.google.template.soy.phpsrc.restricted.PhpExprUtils;
import com.google.template.soy.phpsrc.restricted.PhpStringExpr;
import com.google.template.soy.shared.internal.CodeBuilder;

import java.util.List;

/**
 * A PHP implementation of the CodeBuilder class.
 *
 * <p>Usage example that demonstrates most of the methods:
 * <pre>
 *   PhpCodeBuilder pcb = new PhpCodeBuilder();
 *   pcb.appendLine("function title($opt_data) {");
 *   pcb.increaseIndent();
 *   pcb.pushOutputVar("$output");
 *   pcb.initOutputVarIfNecessary();
 *   pcb.pushOutputVar("$temp");
 *   pcb.addToOutputVar(Lists.newArrayList(
 *       new PhpExpr("'Snow White and the '", Integer.MAX_VALUE),
 *       new PhpExpr("$opt_data['numDwarfs']", Integer.MAX_VALUE));
 *   pcb.popOutputVar();
 *   pcb.addToOutputVar(Lists.newArrayList(
 *       new PhpExpr("$temp", Integer.MAX_VALUE),
 *       new PhpExpr("' Dwarfs'", Integer.MAX_VALUE));
 *   pcb.appendLineStart("return ").appendOutputVarName().appendLineEnd();
 *   pcb.popOutputVar();
 *   pcb.decreaseIndent();
 *   pcb.appendLine("}");
 *   String THE_END = "the end";
 *   pcb.appendLine("# ", THE_END);
 * </pre>
 *
 * The above example builds the following Python code:
 * <pre>
 * function title($opt_data) {
 *   $output = '';
 *   $temp = 'Snow White and the ' . $opt_data['numDwarfs'];
 *   $output += $temp . Dwarfs';
 *   return $output;
 * }
 * # the end
 * </pre>
 *
 */
final class PhpCodeBuilder extends CodeBuilder<PhpExpr> {

    @Override public void initOutputVarIfNecessary() {
        if (getOutputVarIsInited()) {
            // Nothing to do since it's already initialized.
            return;
        }

        // output = ''
        appendLine(getOutputVarName(), " = '';");

        setOutputVarInited();
    }

    @Override public void addToOutputVar(List<? extends PhpExpr> phpExprs) {
        addToOutputVar(PhpExprUtils.concatPhpExprs(phpExprs));
    }

    /**
     * Add a single PhpExpr object to the output variable.
     * @param phpExpr
     */
    public void addToOutputVar(PhpExpr phpExpr) {
        if (getOutputVarIsInited())
        {
            appendLine(getOutputVarName(), " .= ", phpExpr.getText(), ";");
        }
        else
        {
            appendLine(getOutputVarName(), " = ", phpExpr.getText(), ";");
            setOutputVarInited();
        }
    }

    /**
     * Provide the output object as a string.
     *
     * @return A PyExpr object of the output joined into a String.
     */
    public PhpStringExpr getOutputAsString() {
        Preconditions.checkState(getOutputVarName() != null);

        initOutputVarIfNecessary();
        return new PhpStringExpr(getOutputVarName(), Integer.MAX_VALUE).toPhpString();
    }

    /**
     * Appends the given strings, then a newline.
     * @param codeFragments The code string(s) to append.
     * @return This CodeBuilder (for stringing together operations).
     */
    @Override public PhpCodeBuilder appendLineEnd(String... codeFragments) {
        append(codeFragments);
        append(";\n");
        return this;
    }
}
