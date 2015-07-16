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

import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.SoyModule;
import com.google.template.soy.data.restricted.PrimitiveData;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.phpsrc.internal.GenPhpExprsVisitor.GenPhpExprsVisitorFactory;
import com.google.template.soy.phpsrc.internal.TranslateToPhpExprVisitor.TranslateToPhpExprVisitorFactory;
import com.google.template.soy.phpsrc.restricted.PhpExpr;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.sharedpasses.SubstituteGlobalsVisitor;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;

import java.util.List;
import java.util.Map;

/**
 * Truth assertion which compiles the provided soy code and asserts that the generated PhpExprs match
 * the expected expressions. This subject is only valid for soy code which can be represented as one
 * or more PHP expressions.
 *
 */
public final class SoyExprForPhpSubject extends Subject<SoyExprForPhpSubject, String> {

    private ImmutableMap<String, PrimitiveData> globals;

    private final LocalVariableStack localVarExprs;

    private final Injector injector;


    private SoyExprForPhpSubject(FailureStrategy failureStrategy, String expr) {
        super(failureStrategy, expr);
        localVarExprs = new LocalVariableStack();
        injector = Guice.createInjector(new SoyModule());
    }

    /**
     * Adds a frame of local variables to the top of the {@link LocalVariableStack}.
     *
     * @param localVarFrame one frame of local variables
     * @return the current subject for chaining
     */
    public SoyExprForPhpSubject with(Map<String, PhpExpr> localVarFrame) {
        localVarExprs.pushFrame();
        for (Map.Entry<String, PhpExpr> entry : localVarFrame.entrySet()) {
            localVarExprs.addVariable(entry.getKey(), entry.getValue());
        }
        return this;
    }

    /**
     * Sets a map of key to {@link PrimitiveData} values as the current globally available data. Any
     * compilation step will use these globals to replace unrecognized variables.
     *
     * @param globals a map of keys to PrimitiveData values
     * @return the current subject for chaining
     */
    public SoyExprForPhpSubject withGlobals(ImmutableMap<String, PrimitiveData> globals) {
        this.globals = globals;
        return this;
    }

    /**
     * Asserts the subject compiles to the correct PhpExpr.
     *
     * @param expectedPhpExpr the expected result of compilation
     */
    public void compilesTo(PhpExpr expectedPhpExpr) {
        compilesTo(ImmutableList.of(expectedPhpExpr));
    }

    /**
     * Asserts the subject compiles to the correct list of PhpExprs.
     *
     * <p>The given Soy expr is wrapped in a full body of a template. The actual result is replaced
     * with ids for ### so that tests don't break when ids change.
     *
     * @param expectedPhpExprs the expected result of compilation
     */
    public void compilesTo(List<PhpExpr> expectedPhpExprs) {
        SoyFileSetNode soyTree =
                SoyFileSetParserBuilder.forTemplateContents(getSubject()).parse();
        SoyNode node = SharedTestUtils.getNode(soyTree, 0);

        SharedTestUtils.simulateNewApiCall(injector);
        GenPhpExprsVisitor genPhpExprsVisitor = injector.getInstance(
                GenPhpExprsVisitorFactory.class).create(localVarExprs);
        List<PhpExpr> actualPhpExprs = genPhpExprsVisitor.exec(node);

        assertThat(actualPhpExprs).hasSize(expectedPhpExprs.size());
        for (int i = 0; i < expectedPhpExprs.size(); i++) {
            PhpExpr expectedPhpExpr = expectedPhpExprs.get(i);
            PhpExpr actualPhpExpr = actualPhpExprs.get(i);
            assertThat(actualPhpExpr.getText().replaceAll("\\([0-9]+", "(###"))
                    .isEqualTo(expectedPhpExpr.getText());
            assertThat(actualPhpExpr.getPrecedence()).isEqualTo(expectedPhpExpr.getPrecedence());
        }
    }

    /**
     * Asserts the subject translates to the expected PhpExpr.
     *
     * @param expectedPhpExpr the expected result of translation
     */
    public void translatesTo(PhpExpr expectedPhpExpr) {
        translatesTo(expectedPhpExpr, null);
    }

    /**
     * Asserts the subject translates to the expected PhpExpr including verification of the exact
     * PhpExpr class (e.g. {@code PyStringExpr.class}).
     *
     * @param expectedPhpExpr the expected result of translation
     * @param expectedClass the expected class of the resulting PhpExpr
     */
    public void translatesTo(PhpExpr expectedPhpExpr, Class<? extends PhpExpr> expectedClass) {
        String soyExpr = String.format("{print %s}", getSubject());
        SoyFileSetNode soyTree = SoyFileSetParserBuilder.forTemplateContents(soyExpr).parse();
        if (this.globals != null) {
            ErrorReporter boom = ExplodingErrorReporter.get();
            new SubstituteGlobalsVisitor(globals, null /* typeRegistry */,
                    true /* shouldAssertNoUnboundGlobals */, boom).exec(soyTree);
        }
        PrintNode node = (PrintNode)SharedTestUtils.getNode(soyTree, 0);
        ExprNode exprNode = node.getExprUnion().getExpr();

        PhpExpr actualPhpExpr = injector.getInstance(TranslateToPhpExprVisitorFactory.class)
                .create(localVarExprs)
                .exec(exprNode);
        assertThat(actualPhpExpr.getText()).isEqualTo(expectedPhpExpr.getText());
        assertThat(actualPhpExpr.getPrecedence()).isEqualTo(expectedPhpExpr.getPrecedence());

        if (expectedClass != null) {
            assertThat(actualPhpExpr.getClass()).isEqualTo(expectedClass);
        }
    }


    //-----------------------------------------------------------------------------------------------
    // Public static functions for starting a SoyExprForPhpSubject test.


    private static final SubjectFactory<SoyExprForPhpSubject, String> SOYEXPR =
            new SubjectFactory<SoyExprForPhpSubject, String>() {
                @Override
                public SoyExprForPhpSubject getSubject(FailureStrategy failureStrategy, String expr) {
                    return new SoyExprForPhpSubject(failureStrategy, expr);
                }
            };

    public static SoyExprForPhpSubject assertThatSoyExpr(String expr) {
        return assertAbout(SOYEXPR).that(expr);
    }
}
