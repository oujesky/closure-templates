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

import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.template.soy.phpsrc.SoyPhpSrcOptions;
import com.google.template.soy.phpsrc.internal.GenPhpExprsVisitor.GenPhpExprsVisitorFactory;
import com.google.template.soy.phpsrc.internal.MsgFuncGenerator.MsgFuncGeneratorFactory;
import com.google.template.soy.phpsrc.internal.TranslateToPhpExprVisitor.TranslateToPhpExprVisitorFactory;
import com.google.template.soy.phpsrc.restricted.SoyPhpSrcFunction;
import com.google.template.soy.phpsrc.restricted.SoyPhpSrcPrintDirective;
import com.google.template.soy.shared.internal.ApiCallScope;
import com.google.template.soy.shared.internal.GuiceSimpleScope;
import com.google.template.soy.shared.internal.ModuleUtils;
import com.google.template.soy.shared.internal.SharedModule;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.PhpBidiIsRtlFn;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.PhpTranslationClass;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.sharedpasses.SharedPassesModule;

import java.util.Set;

import javax.inject.Singleton;


/**
 * Guice module for the PHP Source backend.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class PhpSrcModule extends AbstractModule {

    @Override protected void configure() {
        // Install requisite modules.
        install(new SharedModule());
        install(new SharedPassesModule());

        // Bindings for when explicit dependencies are required.
        bind(PhpSrcMain.class);
        bind(GenPhpCodeVisitor.class);
        bind(GenPhpCallExprVisitor.class);
        bind(IsComputableAsPhpExprVisitor.class);
        bind(IsCalleeInFileVisitor.class);

        // Bind providers of factories (created via assisted inject).
        install(new FactoryModuleBuilder().build(GenPhpExprsVisitorFactory.class));
        install(new FactoryModuleBuilder().build(TranslateToPhpExprVisitorFactory.class));
        install(new FactoryModuleBuilder().build(MsgFuncGeneratorFactory.class));

        // Bind unscoped providers for parameters in ApiCallScope (these throw exceptions).
        bind(SoyPhpSrcOptions.class)
                .toProvider(GuiceSimpleScope.<SoyPhpSrcOptions>getUnscopedProvider())
                .in(ApiCallScope.class);
        bind(String.class).annotatedWith(PhpBidiIsRtlFn.class)
                .toProvider(GuiceSimpleScope.<String>getUnscopedProvider())
                .in(ApiCallScope.class);
        bind(String.class).annotatedWith(PhpTranslationClass.class)
                .toProvider(GuiceSimpleScope.<String>getUnscopedProvider())
                .in(ApiCallScope.class);
    }

    /**
     * Builds and provides the map of SoyPhpSrcFunctions (name to function).
     * @param soyFunctionsSet The installed set of SoyFunctions (from Guice Multibinder). Each
     *     SoyFunction may or may not implement SoyPySrcFunction.
     */
    @Provides
    @Singleton
    ImmutableMap<String, SoyPhpSrcFunction> provideSoyPhpSrcFunctionsMap(
            Set<SoyFunction> soyFunctionsSet) {
        return ModuleUtils.buildSpecificSoyFunctionsMap(soyFunctionsSet, SoyPhpSrcFunction.class);
    }

    /**
     * Builds and provides the map of SoyPhpSrcDirectives (name to directive).
     * @param soyDirectivesSet The installed set of SoyPrintDirectives (from Guice Multibinder). Each
     *     SoyDirective may or may not implement SoyPhpSrcPrintDirective.
     */
    @Provides
    @Singleton
    ImmutableMap<String, SoyPhpSrcPrintDirective> provideSoyPhpSrcDirectivesMap(
            Set<SoyPrintDirective> soyDirectivesSet) {
        return ModuleUtils.buildSpecificSoyDirectivesMap(soyDirectivesSet,
                SoyPhpSrcPrintDirective.class);
    }
}
