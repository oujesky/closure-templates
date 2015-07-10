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

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.internal.i18n.SoyBidiUtils;
import com.google.template.soy.phpsrc.SoyPhpSrcOptions;
import com.google.template.soy.shared.internal.ApiCallScopeUtils;
import com.google.template.soy.shared.internal.GuiceSimpleScope;
import com.google.template.soy.shared.internal.GuiceSimpleScope.WithScope;
import com.google.template.soy.shared.internal.MainEntryPointUtils;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.ApiCall;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.PhpBidiIsRtlFn;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.PhpTranslationClass;
import com.google.template.soy.sharedpasses.opti.SimplifyVisitor;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.List;


/**
 * Main entry point for the PHP Src backend (output target).
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class PhpSrcMain {


    /** The scope object that manages the API call scope. */
    private final GuiceSimpleScope apiCallScope;

    /** The instanceof of SimplifyVisitor to use. */
    private final SimplifyVisitor simplifyVisitor;

    /** Provider for getting an instance of GenPhpCodeVisitor. */
    private final Provider<GenPhpCodeVisitor> genPhpCodeVisitorProvider;


    /**
     * @param apiCallScope The scope object that manages the API call scope.
     * @param simplifyVisitor The instance of SimplifyVisitor to use.
     * @param genPhpCodeVisitorProvider Provider for getting an instance of GenPhpCodeVisitor.
     */
    @Inject
    public PhpSrcMain(
            @ApiCall GuiceSimpleScope apiCallScope,
            SimplifyVisitor simplifyVisitor,
            Provider<GenPhpCodeVisitor> genPhpCodeVisitorProvider) {
        this.apiCallScope = apiCallScope;
        this.simplifyVisitor = simplifyVisitor;
        this.genPhpCodeVisitorProvider = genPhpCodeVisitorProvider;
    }


    /**
     * Generates PHP source code given a Soy parse tree and an options object.
     *
     * @param soyTree The Soy parse tree to generate PHP source code for.
     * @param phpSrcOptions The compilation options relevant to this backend.
     * @return A list of strings where each string represents the PHP source code that belongs in
     *     one PHP file. The generated PHP files correspond one-to-one to the original Soy
     *     source files.
     * @throws SoySyntaxException If a syntax error is found.
     */
    public List<String> genPhpSrc(SoyFileSetNode soyTree, SoyPhpSrcOptions phpSrcOptions)
            throws SoySyntaxException {

        try (WithScope withScope = apiCallScope.enter()) {
            // Seed the scoped parameters.
            apiCallScope.seed(SoyPhpSrcOptions.class, phpSrcOptions);
            apiCallScope.seed(Key.get(String.class, PhpBidiIsRtlFn.class), phpSrcOptions.getBidiIsRtlFn());
            apiCallScope.seed(Key.get(String.class, PhpTranslationClass.class),
                    phpSrcOptions.getTranslationClass());

            BidiGlobalDir bidiGlobalDir = SoyBidiUtils.decodeBidiGlobalDirFromPhpOptions(
                    phpSrcOptions.getBidiIsRtlFn());
            ApiCallScopeUtils.seedSharedParams(apiCallScope, null, bidiGlobalDir);

            simplifyVisitor.exec(soyTree);
            return genPhpCodeVisitorProvider.get().exec(soyTree);
        }
    }

    /**
     * Generates PHP source files given a Soy parse tree, an options object, and information on
     * where to put the output files.
     *
     * @param soyTree The Soy parse tree to generate PHP source code for.
     * @param phpSrcOptions The compilation options relevant to this backend.
     * @param outputPathFormat The format string defining how to build the output file path
     *     corresponding to an input file path.
     * @param inputPathsPrefix The input path prefix, or empty string if none.
     * @throws SoySyntaxException If a syntax error is found.
     * @throws IOException If there is an error in opening/writing an output PHP file.
     */
    public void genPhpFiles(SoyFileSetNode soyTree, SoyPhpSrcOptions phpSrcOptions,
                           String outputPathFormat, String inputPathsPrefix) throws SoySyntaxException, IOException {

        List<String> phpFileContents = genPhpSrc(soyTree, phpSrcOptions);

        ImmutableList<SoyFileNode> srcsToCompile = ImmutableList.copyOf(Iterables.filter(
                soyTree.getChildren(), SoyFileNode.MATCH_SRC_FILENODE));

        if (srcsToCompile.size() != phpFileContents.size()) {
            throw new AssertionError(String.format("Expected to generate %d code chunk(s), got %d",
                    srcsToCompile.size(), phpFileContents.size()));
        }

        Multimap<String, Integer> outputs = MainEntryPointUtils.mapOutputsToSrcs(
                null, outputPathFormat, inputPathsPrefix, srcsToCompile);

        for (String outputFilePath : outputs.keySet()) {
            Writer out = Files.newWriter(new File(outputFilePath), Charsets.UTF_8);
            try {
                for (int inputFileIndex : outputs.get(outputFilePath)) {
                    out.write(phpFileContents.get(inputFileIndex));
                }
            } finally {
                out.close();
            }
        }
    }

}
