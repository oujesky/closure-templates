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
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.msgs.internal.MsgUtils;
import com.google.template.soy.phpsrc.internal.MsgFuncGenerator.MsgFuncGeneratorFactory;
import com.google.template.soy.phpsrc.internal.TranslateToPhpExprVisitor.TranslateToPhpExprVisitorFactory;
import com.google.template.soy.phpsrc.restricted.PhpExpr;
import com.google.template.soy.phpsrc.restricted.PhpExprUtils;
import com.google.template.soy.phpsrc.restricted.PhpStringExpr;
import com.google.template.soy.phpsrc.restricted.SoyPhpSrcPrintDirective;
import com.google.template.soy.soytree.*;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Visitor for generating PHP expressions for parse tree nodes.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class GenPhpExprsVisitor extends AbstractSoyNodeVisitor<List<PhpExpr>> {


    /**
     * Injectable factory for creating an instance of this class.
     */
    public static interface GenPhpExprsVisitorFactory {
        public GenPhpExprsVisitor create(LocalVariableStack localVarExprs);
    }


    /** Map of all SoyPhpSrcPrintDirectives (name to directive). */
    Map<String, SoyPhpSrcPrintDirective> soyPhpSrcDirectivesMap;

    private final IsComputableAsPhpExprVisitor isComputableAsPhpExprVisitor;

    private final GenPhpExprsVisitorFactory genPhpExprsVisitorFactory;

    private final TranslateToPhpExprVisitorFactory translateToPhpExprVisitorFactory;

    private final GenPhpCallExprVisitor genPhpCallExprVisitor;

    private final MsgFuncGeneratorFactory msgFuncGeneratorFactory;

    private final LocalVariableStack localVarExprs;

    /** List to collect the results. */
    private List<PhpExpr> phpExprs;


    /**
     * @param soyPhpSrcDirectivesMap Map of all SoyPhpSrcPrintDirectives (name to directive).
     */
    @AssistedInject
    GenPhpExprsVisitor(
            ImmutableMap<String, SoyPhpSrcPrintDirective> soyPhpSrcDirectivesMap,
            IsComputableAsPhpExprVisitor isComputableAsPhpExprVisitor,
            GenPhpExprsVisitorFactory genPhpExprsVisitorFactory,
            MsgFuncGeneratorFactory msgFuncGeneratorFactory,
            TranslateToPhpExprVisitorFactory translateToPhpExprVisitorFactory,
            GenPhpCallExprVisitor genPhpCallExprVisitor,
            ErrorReporter errorReporter,
            @Assisted LocalVariableStack localVarExprs) {
        super(errorReporter);
        this.soyPhpSrcDirectivesMap = soyPhpSrcDirectivesMap;
        this.isComputableAsPhpExprVisitor = isComputableAsPhpExprVisitor;
        this.genPhpExprsVisitorFactory = genPhpExprsVisitorFactory;
        this.translateToPhpExprVisitorFactory = translateToPhpExprVisitorFactory;
        this.genPhpCallExprVisitor = genPhpCallExprVisitor;
        this.msgFuncGeneratorFactory = msgFuncGeneratorFactory;
        this.localVarExprs = localVarExprs;
    }


    @Override public List<PhpExpr> exec(SoyNode node) {
        Preconditions.checkArgument(isComputableAsPhpExprVisitor.exec(node));
        phpExprs = new ArrayList<>();
        visit(node);
        return phpExprs;
    }

    /**
     * Executes this visitor on the children of the given node, without visiting the given node
     * itself.
     */
    List<PhpExpr> execOnChildren(ParentSoyNode<?> node) {
        Preconditions.checkArgument(isComputableAsPhpExprVisitor.execOnChildren(node));
        phpExprs = new ArrayList<>();
        visitChildren(node);
        return phpExprs;
    }


    // -----------------------------------------------------------------------------------------------
    // Implementations for specific nodes.


    /**
     * Example:
     * <pre>
     *   I'm feeling lucky!
     * </pre>
     * generates
     * <pre>
     *   'I\'m feeling lucky!'
     * </pre>
     */
    @Override protected void visitRawTextNode(RawTextNode node) {
        // Escape special characters in the text before writing as a string.
        String exprText = BaseUtils.escapeToSoyString(node.getRawText(), false);
        phpExprs.add(new PhpStringExpr(exprText));
    }

    /**
     * Visiting a print node accomplishes 3 basic tasks. It loads data, it performs any operations
     * needed, and it executes the appropriate print directives.
     *
     * <p>TODO(dcphillips): Add support for local variables once LetNode are supported.
     *
     * <p>Example:
     * <pre>
     *   {$boo |changeNewlineToBr}
     *   {$goo + 5}
     * </pre>
     * might generate
     * <pre>
     *   Sanitize::changeNewlineToBr($opt_data['boo'])
     *   $opt_data['goo'] + 5
     * </pre>
     */
    @Override protected void visitPrintNode(PrintNode node) {
        TranslateToPhpExprVisitor translator = translateToPhpExprVisitorFactory.create(localVarExprs);

        PhpExpr phpExpr = translator.exec(node.getExprUnion().getExpr());

        // Process directives.
        for (PrintDirectiveNode directiveNode : node.getChildren()) {

            // Get directive.
            SoyPhpSrcPrintDirective directive = soyPhpSrcDirectivesMap.get(directiveNode.getName());
            if (directive == null) {
                throw SoySyntaxExceptionUtils.createWithNode(
                        "Failed to find SoyPhpSrcPrintDirective with name '" + directiveNode.getName() + "'"
                                + " (tag " + node.toSourceString() + ")",
                        directiveNode);
            }

            // Get directive args.
            List<ExprRootNode> args = directiveNode.getArgs();
            if (!directive.getValidArgsSizes().contains(args.size())) {
                throw SoySyntaxExceptionUtils.createWithNode(
                        "Print directive '" + directiveNode.getName() + "' used with the wrong number of"
                                + " arguments (tag " + node.toSourceString() + ").",
                        directiveNode);
            }

            // Translate directive args.
            List<PhpExpr> argsPhpExprs = new ArrayList<>(args.size());
            for (ExprRootNode arg : args) {
                argsPhpExprs.add(translator.exec(arg));
            }

            // Apply directive.
            phpExpr = directive.applyForPhpSrc(phpExpr, argsPhpExprs);
        }

        phpExprs.add(phpExpr);
    }

    @Override protected void visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
        PhpExpr msg = msgFuncGeneratorFactory.create(node.getMsg(), localVarExprs).getPhpExpr();

        // MsgFallbackGroupNode could only have one child or two children. See MsgFallbackGroupNode.
        if (node.hasFallbackMsg()) {
            StringBuilder phpExprTextSb = new StringBuilder();
            PhpExpr fallbackMsg = msgFuncGeneratorFactory.create(
                    node.getFallbackMsg(), localVarExprs).getPhpExpr();

            // The fallback message is only used if the first message is not available, but the fallback
            // is. So availability of both messages must be tested.
            long firstId = MsgUtils.computeMsgIdForDualFormat(node.getMsg());
            long secondId = MsgUtils.computeMsgIdForDualFormat(node.getFallbackMsg());
            phpExprTextSb.append(PhpExprUtils.TRANSLATOR_NAME + "::isMsgAvailable(" + firstId + ")")
                    .append(" || ")
                    .append(PhpExprUtils.TRANSLATOR_NAME + "::isMsgAvailable(" + secondId + ")");


            // Build PHP ternary expression: cond ? a : b
            phpExprTextSb.append(" ? ").append(msg.getText());

            phpExprTextSb.append(" : ").append(fallbackMsg.getText());
            msg = new PhpStringExpr(phpExprTextSb.toString(),
                    PhpExprUtils.phpPrecedenceForOperator(Operator.CONDITIONAL));
        }

        // Escaping directives apply to messages, especially in attribute context.
        for (String directiveName : node.getEscapingDirectiveNames()) {
            SoyPhpSrcPrintDirective directive = soyPhpSrcDirectivesMap.get(directiveName);
            Preconditions.checkNotNull(
                    directive, "Contextual autoescaping produced a bogus directive: %s", directiveName);
            msg = directive.applyForPhpSrc(msg, ImmutableList.<PhpExpr>of());
        }
        phpExprs.add(msg);
    }

    @Override protected void visitCssNode(CssNode node) {
        StringBuilder sb = new StringBuilder("Runtime::getCssName(");

        ExprRootNode componentNameExpr = node.getComponentNameExpr();
        if (componentNameExpr != null) {
            TranslateToPhpExprVisitor translator = translateToPhpExprVisitorFactory.create(localVarExprs);
            PhpExpr basePhpExpr = translator.exec(componentNameExpr);
            sb.append(basePhpExpr.getText()).append(", ");
        }

        sb.append("'").append(node.getSelectorText()).append("');");
        phpExprs.add(new PhpExpr(sb.toString(), Integer.MAX_VALUE));
    }

    /**
     * If all the children are computable as expressions, the IfNode can be written as a ternary
     * conditional expression.
     */
    @Override protected void visitIfNode(IfNode node) {
        // Create another instance of this visitor for generating Python expressions from children.
        GenPhpExprsVisitor genPhpExprsVisitor = genPhpExprsVisitorFactory.create(localVarExprs);
        TranslateToPhpExprVisitor translator = translateToPhpExprVisitorFactory.create(localVarExprs);

        StringBuilder phpExprTextSb = new StringBuilder();

        int openConditions = 0;
        boolean hasElse = false;
        for (SoyNode child : node.getChildren()) {

            if (child instanceof IfCondNode) {
                IfCondNode icn = (IfCondNode) child;

                PhpExpr condBlock = PhpExprUtils.concatPhpExprs(genPhpExprsVisitor.exec(icn)).toPhpString();
                condBlock = PhpExprUtils.maybeProtect(condBlock,
                        PhpExprUtils.phpPrecedenceForOperator(Operator.CONDITIONAL));

                // Append opening parentheses to enclose expression, as ternary operator in PHP is left-associative
                openConditions++;
                phpExprTextSb.append("(");

                // Append the conditional and if/else syntax.
                PhpExpr condPhpExpr = translator.exec(icn.getExprUnion().getExpr());
                phpExprTextSb.append(condPhpExpr.getText()).append(" ? ");
                phpExprTextSb.append(condBlock.getText()).append(" : ");

            } else if (child instanceof IfElseNode) {
                hasElse = true;
                IfElseNode ien = (IfElseNode) child;

                PhpExpr elseBlock = PhpExprUtils.concatPhpExprs(genPhpExprsVisitor.exec(ien)).toPhpString();
                phpExprTextSb.append(elseBlock.getText());
            } else {
                throw new AssertionError("Unexpected if child node type. Child: " + child);
            }
        }

        if (!hasElse) {
            phpExprTextSb.append("''");
        }

        // Append all closing parentheses to enclose expression, as ternary operator in PHP is left-associative
        for (int i = 0; i < openConditions; i++)
        {
            phpExprTextSb.append(")");
        }

        // By their nature, inline'd conditionals can only contain output strings, so they can be
        // treated as a string type with a conditional precedence.
        phpExprs.add(new PhpStringExpr(phpExprTextSb.toString(),
                PhpExprUtils.phpPrecedenceForOperator(Operator.CONDITIONAL)));
    }

    @Override protected void visitIfCondNode(IfCondNode node) {
        visitChildren(node);
    }

    @Override protected void visitIfElseNode(IfElseNode node) {
        visitChildren(node);
    }

    @Override protected void visitCallNode(CallNode node) {
        phpExprs.add(genPhpCallExprVisitor.exec(node, localVarExprs));
    }

    @Override protected void visitCallParamContentNode(CallParamContentNode node) {
        visitChildren(node);
    }
}
