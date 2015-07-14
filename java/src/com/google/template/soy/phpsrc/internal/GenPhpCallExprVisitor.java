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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.phpsrc.internal.GenPhpExprsVisitor.GenPhpExprsVisitorFactory;
import com.google.template.soy.phpsrc.internal.TranslateToPhpExprVisitor.TranslateToPhpExprVisitorFactory;
import com.google.template.soy.phpsrc.restricted.PhpExpr;
import com.google.template.soy.phpsrc.restricted.PhpExprUtils;
import com.google.template.soy.phpsrc.restricted.PhpFunctionExprBuilder;
import com.google.template.soy.phpsrc.restricted.PhpArrayExpr;
import com.google.template.soy.phpsrc.restricted.PhpStringExpr;
import com.google.template.soy.phpsrc.restricted.SoyPhpSrcPrintDirective;
import com.google.template.soy.soytree.AbstractReturningSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.CallParamValueNode;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.inject.Inject;

/**
 * Functions for generating PHP code for template calls and their parameters.
 *
 */
final class GenPhpCallExprVisitor extends AbstractReturningSoyNodeVisitor<PhpExpr>{

    private final ImmutableMap<String, SoyPhpSrcPrintDirective> soyPhpSrcDirectivesMap;

    private final IsComputableAsPhpExprVisitor isComputableAsPhpExprVisitor;

    private final IsCalleeInFileVisitor isCalleeInFileVisitor;

    private final GenPhpExprsVisitorFactory genPhpExprsVisitorFactory;

    private final TranslateToPhpExprVisitorFactory translateToPhpExprVisitorFactory;

    private LocalVariableStack localVarStack;


    @Inject
    GenPhpCallExprVisitor(
            ImmutableMap<String, SoyPhpSrcPrintDirective> soyPhpSrcDirectivesMap,
            IsComputableAsPhpExprVisitor isComputableAsPhpExprVisitor,
            IsCalleeInFileVisitor isCalleeInFileVisitor,
            GenPhpExprsVisitorFactory genPhpExprsVisitorFactory,
            TranslateToPhpExprVisitorFactory translateToPhpExprVisitorFactory,
            ErrorReporter errorReporter) {
        super(errorReporter);
        this.soyPhpSrcDirectivesMap = soyPhpSrcDirectivesMap;
        this.isComputableAsPhpExprVisitor = isComputableAsPhpExprVisitor;
        this.isCalleeInFileVisitor = isCalleeInFileVisitor;
        this.genPhpExprsVisitorFactory = genPhpExprsVisitorFactory;
        this.translateToPhpExprVisitorFactory = translateToPhpExprVisitorFactory;
    }

    /**
     * Generates the PHP expression for a given call.
     *
     * <p>Important: If there are CallParamContentNode children whose contents are not computable as
     * PHP expressions, then this function assumes that, elsewhere, code has been generated to
     * define their respective {@code param<n>} temporary variables.
     *
     * <p>Here are five example calls:
     * <pre>
     *   {call some.func data="all" /}
     *   {call some.func data="$boo" /}
     *   {call some.func}
     *     {param goo = $moo /}
     *   {/call}
     *   {call some.func data="$boo"}
     *     {param goo}Blah{/param}
     *   {/call}
     *   {call some.func}
     *     {param goo}
     *       {for $i in range(3)}{$i}{/for}
     *     {/param}
     *   {/call}
     * </pre>
     * Their respective generated calls might be the following:
     * <pre>
     *   some::func($opt_data)
     *   some::func($opt_data['boo'])
     *   some::func(['goo' => $opt_data['moo']})
     *   some::func(array_replace(['goo' => 'Blah'], $opt_data['boo']))
     *   some::func(['goo' => $param65])
     * </pre>
     * Note that in the last case, the param content is not computable as PHP expressions, so we
     * assume that code has been generated to define the temporary variable {@code param<n>}.
     *
     * @param callNode The call to generate code for.
     * @param localVarStack The current stack of replacement PHP expressions for the local
     *     variables (and foreach-loop special functions) current in scope.
     * @return The PHP expression for the call.
     */
    PhpExpr exec(CallNode callNode, LocalVariableStack localVarStack) {
        this.localVarStack = localVarStack;
        PhpExpr callExpr = visit(callNode);
        this.localVarStack = null;
        return callExpr;
    }

    /**
     * Visits basic call nodes and builds the call expression. If the callee is in the file, it can be
     * accessed directly, but if it's in another file, the module name must be prefixed.
     *
     * @param node The basic call node.
     * @return The call PHP expression.
     */
    @Override protected PhpExpr visitCallBasicNode(CallBasicNode node) {
        String calleeName = node.getCalleeName();

        // Build the PHP expr text for the callee.
        String calleeExprText;
        if (isCalleeInFileVisitor.visitCallBasicNode(node)) {
            // If in the same module no namespace is required.
            calleeExprText = "self::" + PhpExprUtils.escapePhpMethodName(calleeName.substring(calleeName.lastIndexOf('.') + 1));
        } else {
            // If in another class, the full class name is required along with the method name.
            int lastDotIndex = calleeName.lastIndexOf('.');
            calleeExprText = "\\" + calleeName.substring(0, lastDotIndex).replace('.', '\\')
                + "::" + calleeName.substring(lastDotIndex + 1);
        }

        String callExprText = calleeExprText + "(" + genObjToPass(node) + ", $opt_ijData)";
        return escapeCall(callExprText, node.getEscapingDirectiveNames());
    }

    /**
     * Visits a delegate call node and builds the call expression to retrieve the function and execute
     * it. The getDelegateFn returns the function directly, so its output can be called directly.
     *
     * @param node The delegate call node.
     * @return The call PHP expression.
     */
    @Override protected PhpExpr visitCallDelegateNode(CallDelegateNode node) {
        ExprRootNode variantSoyExpr = node.getDelCalleeVariantExpr();
        PhpExpr variantPyExpr;
        if (variantSoyExpr == null) {
            // Case 1: Delegate call with empty variant.
            variantPyExpr = new PhpStringExpr("''");
        } else {
            // Case 2: Delegate call with variant expression.
            TranslateToPhpExprVisitor translator = translateToPhpExprVisitorFactory.create(localVarStack);
            variantPyExpr = translator.exec(variantSoyExpr);
        }
        String calleeExprText = new PhpFunctionExprBuilder("Runtime::getDelegateFn")
                .addArg(node.getDelCalleeName())
                .addArg(variantPyExpr)
                .addArg(node.allowsEmptyDefault())
                .build();

        String callExprText = "call_user_func(" + calleeExprText + ", " + genObjToPass(node) + ", $opt_ijData)";
        return escapeCall(callExprText, node.getEscapingDirectiveNames());
    }

    /**
     * Generates the PHP expression for the object to pass in a given call. This expression will be
     * a combination of passed data and additional content params. If both are passed, they'll be
     * combined into one dictionary.
     *
     * @param callNode The call to generate code for.
     * @return The PHP expression for the object to pass in the call.
     */
    public String genObjToPass(CallNode callNode) {
        TranslateToPhpExprVisitor translator = translateToPhpExprVisitorFactory.create(localVarStack);

        // Generate the expression for the original data to pass.
        String dataToPass;
        if (callNode.dataAttribute().isPassingAllData()) {
            dataToPass = "$opt_data";
        } else if (callNode.dataAttribute().isPassingData()) {
            dataToPass = translator.exec(callNode.dataAttribute().dataExpr()).getText();
        } else {
            dataToPass = "null";
        }

        // Case 1: No additional params.
        if (callNode.numChildren() == 0) {
            return dataToPass;
        }

        // Build an object literal containing the additional params.
        Map<PhpExpr, PhpExpr> additionalParams = new LinkedHashMap<>();

        for (CallParamNode child : callNode.getChildren()) {
            PhpExpr key = new PhpStringExpr("'" + child.getKey() + "'");

            if (child instanceof CallParamValueNode) {
                CallParamValueNode cpvn = (CallParamValueNode) child;
                additionalParams.put(key, translator.exec(cpvn.getValueExprUnion().getExpr()));
            } else {
                CallParamContentNode cpcn = (CallParamContentNode) child;
                PhpExpr valuePhpExpr;
                if (isComputableAsPhpExprVisitor.exec(cpcn)) {
                    valuePhpExpr = PhpExprUtils.concatPhpExprs(
                            genPhpExprsVisitorFactory.create(localVarStack).exec(cpcn));
                } else {
                    // This is a param with content that cannot be represented as PHP expressions, so we
                    // assume that code has been generated to define the temporary variable 'param<n>'.
                    String paramExpr = "$param" + cpcn.getId();
                    valuePhpExpr = new PhpExpr(paramExpr, Integer.MAX_VALUE);
                }

                // Param content nodes require a content kind in strict autoescaping, so the content must be
                // wrapped as SanitizedContent.
                valuePhpExpr = PhpExprUtils.wrapAsSanitizedContent(cpcn.getContentKind(),
                        valuePhpExpr.toPhpString());

                additionalParams.put(key, valuePhpExpr);
            }
        }

        PhpExpr additionalParamsExpr = PhpExprUtils.convertMapToPhpExpr(additionalParams);

        // Cases 2 and 3: Additional params with and without original data to pass.
        if (callNode.dataAttribute().isPassingData()) {
            return "array_replace(" + additionalParamsExpr.getText() + ", " + dataToPass + ")";
        } else {
            return additionalParamsExpr.getText();
        }
    }

    /**
     * Escaping directives might apply to the output of the call node, so wrap the output with all
     * required directives.
     *
     * @param callExpr The expression text of the call itself.
     * @param directiveNames The list of the directive names to be applied to the call.
     * @return A PhpExpr containing the call expression with all directives applied.
     */
    private PhpExpr escapeCall(String callExpr, ImmutableList<String> directiveNames) {
        PhpExpr escapedExpr = new PhpExpr(callExpr, Integer.MAX_VALUE);
        if (directiveNames.isEmpty()) {
            return escapedExpr;
        }

        // Successively wrap each escapedExpr in various directives.
        for (String directiveName : directiveNames) {
            SoyPhpSrcPrintDirective directive = soyPhpSrcDirectivesMap.get(directiveName);
            Preconditions.checkNotNull(directive,
                    "Autoescaping produced a bogus directive: %s", directiveName);
            escapedExpr = directive.applyForPhpSrc(escapedExpr, ImmutableList.<PhpExpr>of());
        }
        return escapedExpr;
    }
}
