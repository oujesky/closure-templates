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

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.template.soy.ErrorReporterModule;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.phpsrc.SoyPhpSrcOptions;
import com.google.template.soy.phpsrc.internal.GenPhpExprsVisitor.GenPhpExprsVisitorFactory;
import com.google.template.soy.shared.AutoEscapingType;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.shared.internal.GuiceSimpleScope;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.PhpBidiIsRtlFn;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.PhpTranslationClass;
import com.google.template.soy.soytree.SoyNode;

import java.util.List;

/**
 * Truth assertion which compiles the provided soy code and asserts that the generated PHP code
 * matches the expected output.
 *
 */
public final class SoyCodeForPhpSubject extends Subject<SoyCodeForPhpSubject, String> {

    private String bidiIsRtlFn = "";

    private String translationClass = "";

    private boolean isFile;

    private final Injector injector;


    /**
     * A Subject for testing sections of Soy code. The provided data can either be an entire Soy file,
     * or just the body of a template. If just a body is provided, it is wrapped with a simple
     * template before compiling.
     *
     * @param failureStrategy The environment provided FailureStrategy.
     * @param code The input Soy code to be compiled and tested.
     * @param isFile Whether the provided code represents a full file.
     */
    SoyCodeForPhpSubject(FailureStrategy failureStrategy, String code, boolean isFile) {
        super(failureStrategy, code);
        this.isFile = isFile;
        this.injector = Guice.createInjector(new ErrorReporterModule(), new PhpSrcModule());
    }


    public SoyCodeForPhpSubject withBidi(String bidiIsRtlFn) {
        this.bidiIsRtlFn = bidiIsRtlFn;
        return this;
    }

    public SoyCodeForPhpSubject withTranslationClass(String translationClass) {
        this.translationClass = translationClass;
        return this;
    }

    /**
     * Asserts that the subject compiles to the expected PHP output.
     *
     * <p> During compilation, freestanding bodies are compiled as strict templates with the output
     * variable already being initialized. Additionally, any automatically generated variables have
     * generated IDs replaced with '###'. Thus 'name123' would become 'name###'.
     *
     * @param expectedPhpOutput The expected PHP result of compilation.
     */
    public void compilesTo(String expectedPhpOutput) {
        if (isFile) {
            assertThat(compileFile()).isEqualTo(expectedPhpOutput);
        } else {
            assertThat(compileBody()).isEqualTo(expectedPhpOutput);
        }
    }

    /**
     * Asserts that the subject compiles to PHP output which contains the expected output.
     *
     * <p>Compilation follows the same rules as {@link #compilesTo}.
     *
     * @param expectedPhpOutput The expected PHP result of compilation.
     */
    public void compilesToSourceContaining(String expectedPhpOutput) {
        if (isFile) {
            assertThat(compileFile()).contains(expectedPhpOutput);
        } else {
            assertThat(compileBody()).contains(expectedPhpOutput);
        }
    }

    /**
     * Asserts that the subject compilation throws the expected exception.
     *
     * <p>Compilation follows the same rules as {@link #compilesTo}.
     *
     * @param expectedClass The class of the expected exception.
     */
    public void compilesWithException(Class<? extends Exception> expectedClass) {
        try {
            if (isFile) {
                compileFile();
            } else{
                compileBody();
            }
            fail("Compilation suceeded when it should have failed.");
        } catch (Exception actual) {
            assertThat(actual).isInstanceOf(expectedClass);
        }
    }

    private GenPhpCodeVisitor getGenPhpCodeVisitor() {
        // Setup default configs.
        SoyPhpSrcOptions phpSrcOptions = new SoyPhpSrcOptions(bidiIsRtlFn, translationClass);
        GuiceSimpleScope apiCallScope = SharedTestUtils.simulateNewApiCall(injector);
        apiCallScope.seed(SoyPhpSrcOptions.class, phpSrcOptions);

        // Add customizable bidi fn and translation module.
        apiCallScope.seed(Key.get(String.class, PhpBidiIsRtlFn.class), bidiIsRtlFn);
        apiCallScope.seed(Key.get(String.class, PhpTranslationClass.class), translationClass);

        // Execute the compiler.
        return injector.getInstance(GenPhpCodeVisitor.class);
    }

    private String compileFile() {
        SoyNode node = SoyFileSetParserBuilder.forFileContents(getSubject()).parse();
        List<String> fileContents = getGenPhpCodeVisitor().exec(node);
        return fileContents.get(0).replaceAll("(\\$[a-zA-Z_]+)\\d+", "$1###");
    }

    private String compileBody() {
        SoyNode node = SharedTestUtils.getNode(
                SoyFileSetParserBuilder.forTemplateContents(AutoEscapingType.STRICT, getSubject())
                        .declaredSyntaxVersion(SyntaxVersion.V2_2)
                        .parse(), 0);

        // Setup the GenPhpCodeVisitor's state before the node is visited.
        GenPhpCodeVisitor genPhpCodeVisitor = getGenPhpCodeVisitor();
        genPhpCodeVisitor.phpCodeBuilder = new PhpCodeBuilder();
        genPhpCodeVisitor.phpCodeBuilder.pushOutputVar("$output");
        genPhpCodeVisitor.phpCodeBuilder.setOutputVarInited();
        genPhpCodeVisitor.localVarExprs = new LocalVariableStack();
        genPhpCodeVisitor.localVarExprs.pushFrame();
        genPhpCodeVisitor.genPhpExprsVisitor =
                injector.getInstance(GenPhpExprsVisitorFactory.class).create(genPhpCodeVisitor.localVarExprs);

        genPhpCodeVisitor.visitForTesting(node); // note: we're calling visit(), not exec()

        return genPhpCodeVisitor.phpCodeBuilder.getCode().replaceAll("(\\$[a-zA-Z_]+)\\d+", "$1###");
    }


    //-----------------------------------------------------------------------------------------------
    // Public static functions for starting a SoyCodeForPhpSubject test.


    private static final SubjectFactory<SoyCodeForPhpSubject, String> SOYCODE =
            new SubjectFactory<SoyCodeForPhpSubject, String>() {
                @Override
                public SoyCodeForPhpSubject getSubject(FailureStrategy failureStrategy, String code) {
                    return new SoyCodeForPhpSubject(failureStrategy, code, false);
                }
            };

    private static final SubjectFactory<SoyCodeForPhpSubject, String> SOYFILE =
            new SubjectFactory<SoyCodeForPhpSubject, String>() {
                @Override
                public SoyCodeForPhpSubject getSubject(FailureStrategy failureStrategy, String file) {
                    return new SoyCodeForPhpSubject(failureStrategy, file, true);
                }
            };

    public static SoyCodeForPhpSubject assertThatSoyCode(String code) {
        return assertAbout(SOYCODE).that(code);
    }

    public static SoyCodeForPhpSubject assertThatSoyFile(String file) {
        return assertAbout(SOYFILE).that(file);
    }
}
