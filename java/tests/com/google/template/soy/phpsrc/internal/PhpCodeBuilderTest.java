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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Lists;
import com.google.template.soy.exprtree.Operator;

import com.google.template.soy.phpsrc.restricted.PhpExpr;
import com.google.template.soy.phpsrc.restricted.PhpExprUtils;
import com.google.template.soy.phpsrc.restricted.PhpStringExpr;
import junit.framework.TestCase;


/**
 * Unit tests for PhpCodeBuilder.
 *
 */
public final class PhpCodeBuilderTest extends TestCase {

    public void testSimpleOutputVar() {
        // Output initialization.
        PhpCodeBuilder pcb = new PhpCodeBuilder();
        pcb.pushOutputVar("$output");
        pcb.appendOutputVarName().appendLineEnd();
        assertThat(pcb.getCode()).isEqualTo("$output;\n");
        pcb.initOutputVarIfNecessary();
        assertThat(pcb.getCode()).isEqualTo("$output;\n$output = '';\n");
        pcb.pushOutputVar("$param5");
        pcb.appendOutputVarName().appendLineEnd();
        pcb.setOutputVarInited();
        pcb.initOutputVarIfNecessary();  // nothing added
        assertThat(pcb.getCode()).isEqualTo("$output;\n$output = '';\n$param5;\n");
    }

    public void testComplexOutput() {
        // Output assignment should initialize and use append.
        PhpCodeBuilder pcb = new PhpCodeBuilder();
        pcb.pushOutputVar("$output");
        pcb.addToOutputVar(Lists.newArrayList(new PhpStringExpr("$boo")));
        assertThat(pcb.getCode()).isEqualTo("$output = $boo;\n");

        // Multiple expressions should use extend to track output as one large list.
        pcb.pushOutputVar("$param5");
        pcb.setOutputVarInited();
        pcb.addToOutputVar(Lists.newArrayList(
                new PhpExpr("$a - $b", PhpExprUtils.phpPrecedenceForOperator(Operator.MINUS)),
                new PhpExpr("$c - $d", PhpExprUtils.phpPrecedenceForOperator(Operator.MINUS)),
                new PhpExpr("$e * $f", PhpExprUtils.phpPrecedenceForOperator(Operator.TIMES))));
        assertThat(pcb.getCode())
                .isEqualTo(
                        "$output = $boo;\n$param5 .= ($a - $b).($c - $d).$e * $f;\n");
        pcb.popOutputVar();
        pcb.appendOutputVarName().appendLineEnd();
        assertThat(pcb.getCode())
                .isEqualTo(
                        "$output = $boo;\n$param5 .= ($a - $b).($c - $d).$e * $f;\n"
                                + "$output;\n");
    }

    public void testOutputAsString() {
        // Output should use String joining to convert to a String.
        PhpCodeBuilder pcb = new PhpCodeBuilder();
        pcb.pushOutputVar("$output");
        pcb.addToOutputVar(Lists.newArrayList(new PhpStringExpr("$boo")));
        assertThat(pcb.getCode()).isEqualTo("$output = $boo;\n");
        assertThat(pcb.getOutputAsString().getText()).isEqualTo("$output");
    }
}
