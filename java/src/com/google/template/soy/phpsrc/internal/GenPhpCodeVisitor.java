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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.phpsrc.internal.GenPhpExprsVisitor.GenPhpExprsVisitorFactory;
import com.google.template.soy.phpsrc.internal.TranslateToPhpExprVisitor.TranslateToPhpExprVisitorFactory;
import com.google.template.soy.phpsrc.restricted.PhpExpr;
import com.google.template.soy.phpsrc.restricted.PhpExprUtils;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.PhpBidiIsRtlFn;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.PhpTranslationClass;
import com.google.template.soy.sharedpasses.ShouldEnsureDataIsDefinedVisitor;
import com.google.template.soy.soytree.*;
import com.google.template.soy.soytree.ForNode.RangeArgs;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.aggregate.UnionType;
import com.google.template.soy.types.primitive.SanitizedType;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.inject.Inject;

/**
 * Visitor for generating full PHP code (i.e. statements) for parse tree nodes.
 *
 * <p> {@link #exec} should be called on a full parse tree. PHP source code will be generated
 * for all the Soy files. The return value is a list of strings, each string being the content of
 * one generated PHP file (corresponding to one Soy file).
 *
 */
final class GenPhpCodeVisitor extends AbstractSoyNodeVisitor<List<String>> {

    private static final SoyError NON_NAMESPACED_TEMPLATE =
            SoyError.of("Called template does not reside in a namespace.");

    /** Regex pattern for an integer. */
    private static final Pattern INTEGER = Pattern.compile("-?\\d+");

    /** The module and function name for the bidi isRtl function. */
    private final String bidiIsRtlFn;

    /** The module and class name for the translation class used at runtime. */
    private final String translationClass;

    /** The contents of the generated PHP files. */
    private List<String> phpFilesContents;

    @VisibleForTesting protected PhpCodeBuilder phpCodeBuilder;

    private final IsComputableAsPhpExprVisitor isComputableAsPhpExprVisitor;

    private final GenPhpExprsVisitorFactory genPhpExprsVisitorFactory;

    @VisibleForTesting protected GenPhpExprsVisitor genPhpExprsVisitor;

    private final TranslateToPhpExprVisitorFactory translateToPhpExprVisitorFactory;

    private final GenPhpCallExprVisitor genPhpCallExprVisitor;

    private List<String> delegateRegisterCalls;

    /**
     * @see LocalVariableStack
     */
    @VisibleForTesting protected LocalVariableStack localVarExprs;

    /**
     * @param translationClass Python class path used in python runtime to execute translation.
     */
    @Inject
    GenPhpCodeVisitor(@PhpBidiIsRtlFn String bidiIsRtlFn,
                     @PhpTranslationClass String translationClass,
                     IsComputableAsPhpExprVisitor isComputableAsPhpExprVisitor,
                     GenPhpExprsVisitorFactory genPhpExprsVisitorFactory,
                     TranslateToPhpExprVisitorFactory translateToPhpExprVisitorFactory,
                     GenPhpCallExprVisitor genPhpCallExprVisitor,
                     ErrorReporter errorReporter) {
        super(errorReporter);
        this.bidiIsRtlFn = bidiIsRtlFn;
        this.translationClass = translationClass;
        this.isComputableAsPhpExprVisitor = isComputableAsPhpExprVisitor;
        this.genPhpExprsVisitorFactory = genPhpExprsVisitorFactory;
        this.translateToPhpExprVisitorFactory = translateToPhpExprVisitorFactory;
        this.genPhpCallExprVisitor = genPhpCallExprVisitor;
    }


    @Override public List<String> exec(SoyNode node) {
        phpFilesContents = new ArrayList<>();
        phpCodeBuilder = null;
        genPhpExprsVisitor = null;
        localVarExprs = null;
        delegateRegisterCalls = new ArrayList<>();
        visit(node);
        return phpFilesContents;
    }

    @VisibleForTesting void visitForTesting(SoyNode node) {
        visit(node);
    }

    /**
     * Visit all the children of a provided node and combine the results into one expression where
     * possible. This will let us avoid some {@code output.append} calls and save a bit of time.
     */
    @Override protected void visitChildren(ParentSoyNode<?> node) {
        // If the first child cannot be written as an expression, we need to init the output variable
        // first or face potential scoping issues with the output variable being initialized too late.
        if (node.numChildren() > 0 && !isComputableAsPhpExprVisitor.exec(node.getChild(0))) {
            phpCodeBuilder.initOutputVarIfNecessary();
        }

        List<PhpExpr> childPhpExprs = new ArrayList<>();

        for (SoyNode child : node.getChildren()) {
            if (isComputableAsPhpExprVisitor.exec(child)) {
                childPhpExprs.addAll(genPhpExprsVisitor.exec(child));
            } else {
                // We've reached a child that is not computable as a Python expression.
                // First add the PhpExprss from preceding consecutive siblings that are computable as Python
                // expressions (if any).
                if (!childPhpExprs.isEmpty()) {
                    phpCodeBuilder.addToOutputVar(childPhpExprs);
                    childPhpExprs.clear();
                }
                // Now append the code for this child.
                visit(child);
            }
        }

        // Add the PyExprs from the last few children (if any).
        if (!childPhpExprs.isEmpty()) {
            phpCodeBuilder.addToOutputVar(childPhpExprs);
            childPhpExprs.clear();
        }
    }


    // -----------------------------------------------------------------------------------------------
    // Implementations for specific nodes.


    @Override protected void visitSoyFileSetNode(SoyFileSetNode node) {
        for (SoyFileNode soyFile : node.getChildren()) {
            try {
                visit(soyFile);
            } catch (SoySyntaxException sse) {
                throw sse.associateMetaInfo(null, soyFile.getFilePath(), null);
            }
        }
    }

    /**
     * Visit a SoyFileNode and generate it's PHP output.
     *
     * <p>This visitor generates the necessary imports and configuration needed for all PHP output
     * files. This includes imports of runtime libraries, external templates called from within this
     * file, and namespacing configuration.
     *
     * <p>Template generation is deferred to other visitors.
     *
     * <p>Example Output:
     * <pre>
     * <?php
     * /**
     *  * This file was automatically generated from my-templates.soy.
     *  * Please don't edit this file by hand.
     *
     * ...
     * </pre>
     */
    @Override protected void visitSoyFileNode(SoyFileNode node) {

        if (node.getSoyFileKind() != SoyFileKind.SRC) {
            return;  // don't generate code for deps
        }

        phpCodeBuilder = new PhpCodeBuilder();

        phpCodeBuilder.appendLine("<?php");
        phpCodeBuilder.appendLine("/**");

        phpCodeBuilder.appendLine(" * This file was automatically generated from ",
                node.getFileName(), ".");
        phpCodeBuilder.appendLine(" * Please don't edit this file by hand.");

        // Output a section containing optionally-parsed compiler directives in comments.
        phpCodeBuilder.appendLine(" * ");
        if (node.getNamespace() != null) {
            phpCodeBuilder.appendLine(
                    " * Templates in namespace ", node.getNamespace(), ".");
        }
        phpCodeBuilder.appendLine(" */");

        // Add code to define PHP namespaces and add use calls for required classes.
        phpCodeBuilder.appendLine();
        phpCodeBuilder.appendLine("namespace ", getPhpNamespace(node), ";");
        addCodeToRequireGeneralDeps();

        phpCodeBuilder.appendLine("class ", getPhpClassName(node), " {");

        phpCodeBuilder.increaseIndent();

        // Add code for each template.
        for (TemplateNode template : node.getChildren()) {
            phpCodeBuilder.appendLine().appendLine();
            try {
                visit(template);
            } catch (SoySyntaxException sse) {
                throw sse.associateMetaInfo(null, null, template.getTemplateNameForUserMsgs());
            }
        }

        phpCodeBuilder.decreaseIndent();

        phpCodeBuilder.appendLine();
        phpCodeBuilder.appendLine("}");

        phpCodeBuilder.appendLine();
        for (String delegateRegisterCall : delegateRegisterCalls) {
            phpCodeBuilder.appendLineEnd(delegateRegisterCall);
        }

        phpFilesContents.add(phpCodeBuilder.getCode());
        phpCodeBuilder = null;
    }

    /**
     * Visit a TemplateNode and generate a corresponding function.
     *
     * <p>Example:
     * <pre>
     * public static function myfunc($opt_data = null, opt_ijData = null) {
     *   $output = '';
     *   ...
     *   ...
     *   return $output;
     * }
     * </pre>
     */
    @Override protected void visitTemplateNode(TemplateNode node) {
        localVarExprs = new LocalVariableStack();
        genPhpExprsVisitor = genPhpExprsVisitorFactory.create(localVarExprs);

        // PHP method visibility
        String visibility = node.getVisibility() == Visibility.PUBLIC ? "public" : "private";

        phpCodeBuilder.appendLine("/**");
        phpCodeBuilder.appendLine(" * @param array|null $opt_data");
        phpCodeBuilder.appendLine(" * @param array|null $opt_ijData");
        phpCodeBuilder.appendLine(" * @return \\Goog\\Soy\\SanitizedContent");
        phpCodeBuilder.appendLine(" */");
        phpCodeBuilder.appendLine(
                visibility, " static function ",
                PhpExprUtils.escapePhpMethodName(node.getPartialTemplateName().substring(1)),
                "($opt_data = null, $opt_ijData = null) {");
        phpCodeBuilder.increaseIndent();

        generateFunctionBody(node);

        phpCodeBuilder.decreaseIndent();

        phpCodeBuilder.appendLine("}");
    }

    /**
     * Visit a TemplateDelegateNode and generate the corresponding function along with the delegate
     * registration.
     *
     * <p>Example:
     * <pre>
     * function myfunc($opt_data = null, $opt_ijData = null) {
     *   ...
     * }
     * Runtime::registerDelegateFn('delname', 'delvariant', 0, ['myclass', 'myfunc'])
     * </pre>
     */
    @Override protected void visitTemplateDelegateNode(TemplateDelegateNode node) {
        // Generate the template first, before registering the delegate function.
        visitTemplateNode(node);

        // Register the function as a delegate function.
        String delTemplateIdExprText = "'" + node.getDelTemplateName() + "'";
        String delTemplateVariantExprText = "'" + node.getDelTemplateVariant() + "'";

        int lastDotIndex = node.getTemplateName().lastIndexOf('.');
        String templateCallableText = node.getTemplateName().substring(0, lastDotIndex).replace('.', '\\')
                + "::" + node.getTemplateName().substring(lastDotIndex + 1);

        String registerCall = "Runtime::registerDelegateFn("
                + delTemplateIdExprText + ", " + delTemplateVariantExprText + ", "
                + node.getDelPriority().toString() + ", '"
                + templateCallableText + "')";
        delegateRegisterCalls.add(registerCall);
    }

    @Override protected void visitPrintNode(PrintNode node) {
        phpCodeBuilder.addToOutputVar(genPhpExprsVisitor.exec(node));
    }

    /**
     * Visit an IfNode and generate a full conditional statement, or an inline ternary conditional
     * expression if all the children are computable as expressions.
     *
     * <p>Example:
     * <pre>
     *   {if $boo > 0}
     *     ...
     *   {/if}
     * </pre>
     * might generate
     * <pre>
     *   if ($opt_data['boo'] > 0) {
     *     ...
     *   }
     * </pre>
     */
    @Override protected void visitIfNode(IfNode node) {
        if (isComputableAsPhpExprVisitor.exec(node)) {
            phpCodeBuilder.addToOutputVar(genPhpExprsVisitor.exec(node));
            return;
        }

        // Not computable as PHP expressions, so generate full code.
        TranslateToPhpExprVisitor translator = translateToPhpExprVisitorFactory.create(localVarExprs);
        for (SoyNode child : node.getChildren()) {
            if (child instanceof IfCondNode) {
                IfCondNode icn = (IfCondNode) child;
                PhpExpr condPhpExpr = translator.exec(icn.getExprUnion().getExpr());

                if (icn.getCommandName().equals("if")) {
                    phpCodeBuilder.appendLine("if (", condPhpExpr.getText(), ") {");
                } else {
                    phpCodeBuilder.appendLine("else if (", condPhpExpr.getText(), ") {");
                }

                phpCodeBuilder.increaseIndent();
                visitChildren(icn);
                phpCodeBuilder.decreaseIndent();

                phpCodeBuilder.appendLine("}");

            } else if (child instanceof IfElseNode) {
                phpCodeBuilder.appendLine("else {");
                phpCodeBuilder.increaseIndent();
                visitChildren((IfElseNode) child);
                phpCodeBuilder.decreaseIndent();
                phpCodeBuilder.appendLine("}");
            } else {
                throw new AssertionError("Unexpected if child node type. Child: " + child);
            }
        }
    }


    /**
     * Example:
     * <pre>
     *   {switch $boo}
     *     {case 0}
     *       ...
     *     {case 1, 2}
     *       ...
     *     {default}
     *       ...
     *   {/switch}
     * </pre>
     * might generate
     * <pre>
     *   switch (opt_data.boo) {
     *     case 0:
     *       ...
     *       break;
     *     case 1:
     *     case 2:
     *       ...
     *       break;
     *     default:
     *       ...
     *   }
     * </pre>
     */
    @Override protected void visitSwitchNode(SwitchNode node) {

        // Run the switch value creation first to ensure side effects always occur.
        TranslateToPhpExprVisitor translator = translateToPhpExprVisitorFactory.create(localVarExprs);
        PhpExpr switchValuePhpExpr = translator.exec(node.getExpr());
        phpCodeBuilder.appendLine("switch (", switchValuePhpExpr.getText(), ") {");
        phpCodeBuilder.increaseIndent();

        for (SoyNode child : node.getChildren()) {

            if (child instanceof SwitchCaseNode) {
                SwitchCaseNode scn = (SwitchCaseNode) child;

                for (ExprNode caseExpr : scn.getExprList()) {
                    PhpExpr casePhpExpr = translator.exec(caseExpr);
                    phpCodeBuilder.appendLine("case ", casePhpExpr.getText(), ":");
                }

                phpCodeBuilder.increaseIndent();
                visitChildren(scn);
                phpCodeBuilder.appendLine("break;");
                phpCodeBuilder.decreaseIndent();

            } else if (child instanceof SwitchDefaultNode) {
                SwitchDefaultNode sdn = (SwitchDefaultNode) child;

                phpCodeBuilder.appendLine("default:");

                phpCodeBuilder.increaseIndent();
                visitChildren(sdn);
                phpCodeBuilder.decreaseIndent();

            } else {
                throw new AssertionError();
            }
        }

        phpCodeBuilder.decreaseIndent();
        phpCodeBuilder.appendLine("}");
    }

    /**
     * Visits a ForNode and generates a for loop over a given range.
     *
     * <p>Example:
     * <pre>
     *   {for $i in range(1, $boo)}
     *     ...
     *   {/for}
     * </pre>
     * might generate
     * <pre>
     *   foreach (range(1, $opt_data['boo']) as $i) {
     *     ...
     *   }
     * </pre>
     */
    @Override protected void visitForNode(ForNode node) {
        TranslateToPhpExprVisitor translator = translateToPhpExprVisitorFactory.create(localVarExprs);

        String varName = node.getVarName();
        String nodeId = Integer.toString(node.getId());

        RangeArgs range = node.getRangeArgs();
        String incrementPhpExprText = range.increment().isPresent()
                ? translator.exec(range.increment().get()).getText()
                : "1" /* default */;
        String initPhpExprText = range.start().isPresent()
                ? translator.exec(range.start().get()).getText()
                : "0" /* default */;
        String limitPhpExprText = translator.exec(range.limit()).getText();


        // If any of the PHP expressions for init/limit/increment isn't an integer, precompute its value.
        String initCode;
        if (INTEGER.matcher(initPhpExprText).matches()) {
            initCode = initPhpExprText;
        } else {
            initCode = "$" + varName + "Init" + nodeId;
            phpCodeBuilder.appendLine(initCode, " = ", initPhpExprText, ";");
        }

        String limitCode;
        if (INTEGER.matcher(limitPhpExprText).matches()) {
            limitCode = limitPhpExprText;
        } else {
            limitCode = "$" + varName + "Limit" + nodeId;
            phpCodeBuilder.appendLine(limitCode, " = ", limitPhpExprText, ";");
        }

        String incrementCode;
        if (INTEGER.matcher(incrementPhpExprText).matches()) {
            incrementCode = incrementPhpExprText;
        } else {
            incrementCode = "$" + varName + "Increment" + nodeId;
            phpCodeBuilder.appendLine(incrementCode, " = ", incrementPhpExprText, ";");
        }

        // The start of the JS 'for' loop.
        String incrementStmt = incrementCode.equals("1") ?
                "$" + varName + nodeId + "++" : "$" + varName + nodeId + " += " + incrementCode;
        phpCodeBuilder.appendLine(
                "for (",
                "$", varName, nodeId, " = ", initCode, "; ",
                "$", varName, nodeId, " < ", limitCode, "; ",
                incrementStmt,
                ") {");

        // Add a new localVarExprs frame and populate it with the translations from this node.
        localVarExprs.pushFrame();
        localVarExprs.addVariable(varName, new PhpExpr("$" + varName + nodeId, Integer.MAX_VALUE));

        // Generate the code for the loop body.
        phpCodeBuilder.increaseIndent();
        visitChildren(node);
        phpCodeBuilder.decreaseIndent();

        phpCodeBuilder.appendLine("}");

        // Remove the localVarTranslations frame that we added above.
        localVarExprs.popFrame();
    }

    /**
     * The top level ForeachNode primarily serves to test for the ifempty case. If present, the loop
     * is wrapped in an if statement which checks for data in the list before iterating.
     *
     * <p>Example:
     * <pre>
     *   {foreach $foo in $boo}
     *     ...
     *   {ifempty}
     *     ...
     *   {/foreach}
     * </pre>
     * might generate
     * <pre>
     *   $fooList2 = $opt_data['boo'];
     *   if (!empty(fooList2) {
     *     ...loop...
     *   } else {
     *     ...
     *   }
     * </pre>
     */
    @Override protected void visitForeachNode(ForeachNode node) {
        // Build the local variable names.
        ForeachNonemptyNode nonEmptyNode = (ForeachNonemptyNode) node.getChild(0);
        String baseVarName = nonEmptyNode.getVarName();
        String listVarName = String.format("$%sList%d", baseVarName, node.getId());

        // Define list variable
        TranslateToPhpExprVisitor translator = translateToPhpExprVisitorFactory.create(localVarExprs);
        PhpExpr dataRefPhpExpr = translator.exec(node.getExpr());
        phpCodeBuilder.appendLine(listVarName, " = ", dataRefPhpExpr.getText(), ";");

        // If has 'ifempty' node, add the wrapper 'if' statement.
        boolean hasIfemptyNode = node.numChildren() == 2;
        if (hasIfemptyNode) {
            // Empty lists are falsy in Python.
            phpCodeBuilder.appendLine("if (!empty(", listVarName, ")) {");
            phpCodeBuilder.increaseIndent();
        }

        // Generate code for nonempty case.
        visit(nonEmptyNode);

        // If has 'ifempty' node, add the 'else' block of the wrapper 'if' statement.
        if (hasIfemptyNode) {
            phpCodeBuilder.decreaseIndent();
            phpCodeBuilder.appendLine("} else {");
            phpCodeBuilder.increaseIndent();

            // Generate code for empty case.
            visit(node.getChild(1));

            phpCodeBuilder.decreaseIndent();

            phpCodeBuilder.appendLine("}");
        }


    }


    /**
     * The ForeachNonemptyNode performs the actual looping. We use a standard {@code for} loop, except
     * that instead of looping directly over the list, we loop over an enumeration to have easy access
     * to the index along with the data.
     *
     * <p>Example:
     * <pre>
     *   {foreach $foo in $boo}
     *     ...
     *   {/foreach}
     * </pre>
     * might generate
     * <pre>
     *   $fooList2 = $opt_data['boo'];
     *   foreach ($fooList2 as $fooIndex2 => $fooData2) {
     *     ...
     *   }
     * </pre>
     */
    @Override protected void visitForeachNonemptyNode(ForeachNonemptyNode node) {
        // Build the local variable names.
        String baseVarName = node.getVarName();
        String foreachNodeId = Integer.toString(node.getForeachNodeId());
        String listVarName = "$" + baseVarName + "List" + foreachNodeId;
        String indexVarName = "$" + baseVarName + "Index" + foreachNodeId;
        String dataVarName = "$" + baseVarName + "Data" + foreachNodeId;
        String firstKeyVarName = "$" + baseVarName + "FirstKey" + foreachNodeId;
        String lastKeyVarName = "$" + baseVarName + "LastKey" + foreachNodeId;

        localVarExprs.pushFrame();

        int eqPrecedence = PhpExprUtils.phpPrecedenceForOperator(Operator.EQUAL);

        phpCodeBuilder.appendLine("reset(", listVarName, ");");
        phpCodeBuilder.appendLine(firstKeyVarName, " = key(", listVarName, ");");
        phpCodeBuilder.appendLine("end(", listVarName, ");");
        phpCodeBuilder.appendLine(lastKeyVarName, " = key(", listVarName, ");");

        // Create the loop
        phpCodeBuilder.appendLine("foreach (", listVarName, " as ", indexVarName, " => ", dataVarName, ") {");
        phpCodeBuilder.increaseIndent();

        // Add a new localVarExprs frame and populate it with the translations from this loop.
        localVarExprs.pushFrame();
        localVarExprs.addVariable(baseVarName, new PhpExpr(dataVarName, Integer.MAX_VALUE))
                .addVariable(baseVarName + "__isFirst",
                        new PhpExpr(indexVarName + " === " + firstKeyVarName, eqPrecedence))
                .addVariable(baseVarName + "__isLast",
                        new PhpExpr(indexVarName + " === " + lastKeyVarName, eqPrecedence))
                .addVariable(baseVarName + "__index", new PhpExpr(indexVarName, Integer.MAX_VALUE));

        // Generate the code for the loop body.
        visitChildren(node);

        // Remove the localVarExprs frame that we added above.
        localVarExprs.popFrame();

        phpCodeBuilder.decreaseIndent();

        phpCodeBuilder.appendLine("}");

        localVarExprs.popFrame();
    }

    @Override protected void visitForeachIfemptyNode(ForeachIfemptyNode node) {
        visitChildren(node);
    }

    /**
     * Visits a let node which accepts a value and stores it as a unique variable. The unique variable
     * name is stored in the LocalVariableStack for use by any subsequent code.
     *
     * <p>Example:
     * <pre>
     *   {let $boo: $foo[$moo] /}
     * </pre>
     * might generate
     * <pre>
     *   $boo3 = $opt_data['foo']['moo'];
     * </pre>
     */
    @Override protected void visitLetValueNode(LetValueNode node) {
        String generatedVarName = "$" + node.getUniqueVarName();

        // Generate code to define the local var.
        TranslateToPhpExprVisitor translator = translateToPhpExprVisitorFactory.create(localVarExprs);
        PhpExpr valuePhpExpr = translator.exec(node.getValueExpr());
        phpCodeBuilder.appendLine(generatedVarName, " = ", valuePhpExpr.getText(), ";");

        // Add a mapping for generating future references to this local var.
        localVarExprs.addVariable(node.getVarName(), new PhpExpr(generatedVarName, Integer.MAX_VALUE));
    }


    /**
     * Visits a let node which contains a content section and stores it as a unique variable. The
     * unique variable name is stored in the LocalVariableStack for use by any subsequent code.
     *
     * <p>Note, this is one of the location where Strict mode is enforced in PHP templates. As
     * such, all LetContentNodes must have a contentKind specified.
     *
     * <p>Example:
     * <pre>
     *   {let $boo kind="html"}
     *     Hello {$name}
     *   {/let}
     * </pre>
     * might generate
     * <pre>
     *   $boo3 = new \Goog\Soy\SanitizedHtml('Hello ' . Sanitize::escapeHtml($opt_data['name'])]);
     * </pre>
     */
    @Override protected void visitLetContentNode(LetContentNode node) {
        if (node.getContentKind() == null) {
            throw SoySyntaxExceptionUtils.createWithNode(
                    "Let content node is missing a content kind. This may be due to using a non-strict "
                            + "template, which is unsupported in the PHP compiler.", node);
        }

        String generatedVarName = node.getUniqueVarName();

        // Traverse the children and push them onto the generated variable.
        localVarExprs.pushFrame();
        phpCodeBuilder.pushOutputVar(generatedVarName);

        visitChildren(node);

        PhpExpr generatedContent = phpCodeBuilder.getOutputAsString();
        phpCodeBuilder.popOutputVar();
        localVarExprs.popFrame();

        // Mark the result as being escaped to the appropriate kind (e.g., "\Goog\Soy\SanitizedHtml").
        phpCodeBuilder.appendLine(generatedVarName, " = ",
                PhpExprUtils.wrapAsSanitizedContent(node.getContentKind(), generatedContent).getText());

        // Add a mapping for generating future references to this local var.
        localVarExprs.addVariable(node.getVarName(), new PhpExpr(generatedVarName, Integer.MAX_VALUE));
    }

    /**
     * Visits a call node and generates the syntax needed to call another template. If all of the
     * children can be represented as expressions, this is built as an expression itself. If not, the
     * non-expression params are saved as {@code param<n>} variables before the function call.
     */
    @Override protected void visitCallNode(CallNode node) {
        // If this node has any param children whose contents are not computable as PHP expressions,
        // visit them to generate code to define their respective 'param<n>' variables.
        for (CallParamNode child : node.getChildren()) {
            if (child instanceof CallParamContentNode && !isComputableAsPhpExprVisitor.exec(child)) {
                visit(child);
            }
        }

        phpCodeBuilder.addToOutputVar(genPhpCallExprVisitor.exec(node, localVarExprs).toPhpString());
    }

    /**
     * Visits a call param content node which isn't computable as a PhpExpr and stores its content in
     * a variable with the name {@code param<n>} where n is the node's id.
     */
    @Override protected void visitCallParamContentNode(CallParamContentNode node) {
        // This node should only be visited when it's not computable as PHP expressions.
        Preconditions.checkArgument(!isComputableAsPhpExprVisitor.exec(node),
                "Should only define 'param<n>' when not computable as Python expressions.");

        phpCodeBuilder.pushOutputVar("$param" + node.getId());
        phpCodeBuilder.initOutputVarIfNecessary();
        visitChildren(node);
        phpCodeBuilder.popOutputVar();
    }


    // -----------------------------------------------------------------------------------------------
    // Fallback implementation.


    @Override protected void visitSoyNode(SoyNode node) {
        if (isComputableAsPhpExprVisitor.exec(node)) {
            // Generate PHP expressions for this node and add them to the current output var.
            phpCodeBuilder.addToOutputVar(genPhpExprsVisitor.exec(node));
        } else {
            // Need to implement visit*Node() for the specific case.
            throw new UnsupportedOperationException();
        }
    }


    // -----------------------------------------------------------------------------------------------
    // Utility methods.


    /**
     * Helper for visitSoyFileNode(SoyFileNode) to add code to require general dependencies.
     */
    private void addCodeToRequireGeneralDeps() {
        phpCodeBuilder.appendLine();
        phpCodeBuilder.appendLine("use Goog\\Soy\\Bidi;");
        phpCodeBuilder.appendLine("use Goog\\Soy\\Directives;");
        phpCodeBuilder.appendLine("use Goog\\Soy\\Sanitize;");
        phpCodeBuilder.appendLine("use Goog\\Soy\\Runtime;");

        // @todo bidi
//        if (!bidiIsRtlFn.isEmpty()) {
//            int dotIndex = bidiIsRtlFn.lastIndexOf('.');
//            // When importing the module, we'll use the constant name to avoid potential conflicts.
//            String bidiModulePath = bidiIsRtlFn.substring(0, dotIndex);
//            Pair<String, String> nameSpaceAndName = namespaceAndNameFromModule(bidiModulePath);
//            String bidiNamespace = nameSpaceAndName.first;
//            String bidiModuleName = nameSpaceAndName.second;
//            phpCodeBuilder.appendLine("from ", bidiNamespace, " import ", bidiModuleName, " as ",
//                    SoyBidiUtils.IS_RTL_MODULE_ALIAS);
//        }

        // Add import and instantiate statements for translator module
        // TODO(steveyang): remember the check when implementing MsgNode
        if (!translationClass.isEmpty()) {
            phpCodeBuilder.appendLine("use ", translationClass, " as ", PhpExprUtils.TRANSLATOR_NAME, ";");
        }

        phpCodeBuilder.appendLine();
    }


    /**
     * Helper to retrieve the namespace and name from a module name.
     * @param moduleName PHP module name in dot notation format.
     */
    private static Pair<String, String> namespaceAndNameFromModule(String moduleName) {
        String namespace = moduleName;
        String name = moduleName;
        int lastDotIndex = moduleName.lastIndexOf('.');
        if (lastDotIndex != -1) {
            namespace = moduleName.substring(0, lastDotIndex);
            name = moduleName.substring(lastDotIndex + 1);
        }
        namespace = namespace.replace(".", "\\");
        return Pair.of(namespace, name);
    }


    /**
     * Helper for visitTemplateNode which generates the function body.
     */
    private void generateFunctionBody(TemplateNode node) {
        // Add a new frame for local variable translations.
        localVarExprs.pushFrame();

        // Generate statement to ensure data exists as an object, if ever used.
        if (new ShouldEnsureDataIsDefinedVisitor(errorReporter).exec(node)) {
            phpCodeBuilder.appendLine("$opt_data = is_array($opt_data) ? $opt_data : [];");
        }

        // Type check parameters.
        genParamTypeChecks(node);

        phpCodeBuilder.pushOutputVar("$output");

        visitChildren(node);

        PhpExpr resultPhpExpr = phpCodeBuilder.getOutputAsString();
        phpCodeBuilder.popOutputVar();

        // Templates with autoescape="strict" return the SanitizedContent wrapper for its kind:
        // - Call sites are wrapped in an escaper. Returning SanitizedContent prevents re-escaping.
        // - The topmost call into Soy returns a SanitizedContent. This will make it easy to take
        // the result of one template and feed it to another, and also to confidently assign sanitized
        // HTML content to innerHTML. This does not use the internal-blocks variant.
        resultPhpExpr = PhpExprUtils.wrapAsSanitizedContent(node.getContentKind(), resultPhpExpr);

        phpCodeBuilder.appendLine("return ", resultPhpExpr.getText(), ";");

        localVarExprs.popFrame();
    }


    /**
     * Get PHP namespace for generated class
     * @param node
     * @return PHP namespace
     */
    private String getPhpNamespace(SoyFileNode node) {
        return namespaceAndNameFromModule(node.getNamespace()).first;
    }


    /**
     * Get base PHP class name without namespace for generated class
     * @param node
     * @return PHP class name
     */
    private String getPhpClassName(SoyFileNode node) {
        return namespaceAndNameFromModule(node.getNamespace()).second;
    }


    /**
     * Generate code to verify the runtime types of the input params. Also typecasts the
     * input parameters and assigns them to local variables for use in the template.
     * @param node the template node.
     */
    private void genParamTypeChecks(TemplateNode node) {
        for (TemplateParam param : node.getAllParams()) {
            if (param.declLoc() != TemplateParam.DeclLoc.HEADER) {
                continue;
            }
            String paramName = param.name();
            SoyType paramType = param.type();
            String paramVal = TranslateToPhpExprVisitor.genCodeForParamAccess(paramName);
            String paramAlias = genParamAlias(paramName);
            boolean isAliasedLocalVar = false;
            switch (paramType.getKind()) {
                case ANY:
                case UNKNOWN:
                    // Do nothing
                    break;

                case BOOL:
                    genParamTypeChecksUsingException(
                            paramName, paramAlias, "!!" + paramVal, param.isInjected(),
                            "is_bool({0}) || {0} === 1 || {0} === 0", "false");
                    phpCodeBuilder.appendLine(paramAlias + " = " + paramVal + ";");
                    isAliasedLocalVar = true;
                    break;

                case STRING:
                case INT:
                case FLOAT:
                case LIST:
                case RECORD:
                case MAP:
                case ENUM:
                case OBJECT:{
                    String typePredicate;
                    String defaultValue;
                    switch (param.type().getKind()) {
                        case STRING:
                            typePredicate = "is_string({0}) || ({0} instanceof \\Goog\\Soy\\SanitizedContent)";
                            defaultValue = "''";
                            break;

                        case INT:
                        case ENUM:
                            typePredicate = "is_int({0})";
                            defaultValue = "0";
                            break;

                        case FLOAT:
                            typePredicate = "is_float({0})";
                            defaultValue = "0.0";
                            break;

                        case LIST:
                        case RECORD:
                        case MAP:
                            typePredicate = "is_array({0})";
                            defaultValue = "[]";
                            break;

                        case OBJECT:
                            typePredicate = "is_object({0})";
                            defaultValue = "null";
                            break;

                        default:
                            throw new AssertionError();
                    }

                    genParamTypeChecksUsingException(
                            paramName, paramAlias, paramVal, param.isRequired(), typePredicate, defaultValue
                    );
                    isAliasedLocalVar = true;
                    break;
                }

                case UNION:
                    UnionType unionType = (UnionType) param.type();
                    Pair<String, String> unionTypeTests = genUnionTypeTests(unionType);
                    genParamTypeChecksUsingException(
                            paramName, paramAlias, paramVal, param.isRequired(),
                            unionTypeTests.first, unionTypeTests.second);
                    isAliasedLocalVar = true;
                    break;

                default:
                    if (param.type() instanceof SanitizedType) {
                        String typeName = NodeContentKinds.toPhpSanitizedContentOrdainer(
                                ((SanitizedType) param.type()).getContentKind()
                        );
                        // We allow string or unsanitized type to be passed where a
                        // sanitized type is specified - it just means that the text will
                        // be escaped.
                        genParamTypeChecksUsingException(
                                paramName, paramAlias, paramVal, param.isRequired(),
                                "({0} instanceof " + typeName +
                                        ") || ({0} instanceof \\Goog\\Soy\\UnsanitizedText) || is_string({0})",
                                "''"
                            );
                        isAliasedLocalVar = true;
                        break;
                    }

                    throw new AssertionError("Unsupported type: " + param.type());
            }

            if (isAliasedLocalVar) {
                localVarExprs.addVariable(paramName, new PhpExpr(paramAlias, Integer.MAX_VALUE));
            }
        }
    }


    /**
     * Generate a name for the local variable which will store the value of a
     * parameter, avoiding collision with JavaScript reserved words.
     */
    private String genParamAlias(String paramName) {

        // PHP variable cannot be named $this (see http://php.net/manual/en/language.variables.basics.php)
        if (paramName.equals("this"))
        {
            paramName = "this_";
        }

        return "$" + paramName;
    }

    /**
     * Generate code to check the type of a parameter and throw Goog\Soy\Exception if not valid
     * @param paramName The Soy name of the parameter.
     * @param paramAlias The name of the local variable which stores the value of the param.
     * @param paramVal The value expression of the parameter, which might be
     *     an expression in some cases but will usually be opt_params.somename.
     * @param isRequired True iff the parameter is required
     * @param typePredicate PHP which tests whether the parameter is the correct type.
     *     This is a format string - the {0} format field will be replaced with the
     *     parameter value.
     * @param paramDefault PHP expression for default value when the parameter is
     *     not required and is not present.
     */
    private void genParamTypeChecksUsingException(
            String paramName, String paramAlias, String paramVal, boolean isRequired,
            String typePredicate, String paramDefault) {

        String paramAccessVal = TranslateToPhpExprVisitor.genCodeForParamAccess(paramName);

        // throw a PHP exception if the parameter value does not pass the type predicate
        phpCodeBuilder.appendLine("if (!(",
                MessageFormat.format(typePredicate, paramAccessVal), ")) { " +
                "throw new \\Goog\\Soy\\Exception('Invalid type \"'.gettype(",
                paramAccessVal, ").'\" for parameter \"", paramName, "\"'); }");

        if (isRequired) {
            // required parameters does not need default value
            phpCodeBuilder.appendLine(paramAlias, " = ", paramVal, ";");
        } else {
            phpCodeBuilder.appendLine(paramAlias, " = ", "isset(", paramAccessVal, ") ? ",
                    paramVal, " : ", paramDefault, ";");
        }
    }


    /**
     * Generate code to test an input param against each of the member types of a union.
     */
    private Pair<String,String> genUnionTypeTests(UnionType unionType) {
        Set<String> typeTests = Sets.newTreeSet();
        String defaultVal = "null";
        for (SoyType memberType : unionType.getMembers()) {
            switch (memberType.getKind()) {
                case ANY:
                case UNKNOWN:
                    // Unions generally should not include 'any' as a member, but just in
                    // case they do we should handle it. Since 'any' does not include null,
                    // the test simply ensures that the value is not null.
                    typeTests.add("{0} !== null");
                    break;

                case NULL:
                    // Handled separately, see below.
                    break;

                case BOOL:
                    typeTests.add("is_bool({0}) || {0} === 1 || {0} === 0");
                    defaultVal = "false";
                    break;

                case STRING:
                    typeTests.add("is_string({0})");
                    typeTests.add("({0} instanceof \\Goog\\Soy\\SanitizedContent)");
                    defaultVal = "''";
                    break;

                case INT:
                case ENUM:
                    typeTests.add("is_int({0})");
                    defaultVal = "0";
                    break;

                case FLOAT:
                    typeTests.add("is_float({0})");
                    defaultVal = "0.0";
                    break;

                case LIST:
                case RECORD:
                case MAP:
                    typeTests.add("is_array({0})");
                    defaultVal = "[]";
                    break;

                case OBJECT:
                    typeTests.add("is_object({0}");
                    defaultVal = "null";
                    break;

                default:
                    if (memberType instanceof SanitizedType) {
                        // For sanitized kinds, an unwrapped string is also considered valid.
                        // (It will be auto-escaped.)  But we don't want to test for this multiple
                        // times if there are multiple sanitized kinds.
                        String typeName = NodeContentKinds.toPhpSanitizedContentOrdainer(
                                ((SanitizedType) memberType).getContentKind()
                        );
                        typeTests.add("({0} instanceof " + typeName + ")");
                        typeTests.add("({0} instanceof \\Goog\\Soy\\UnsanitizedText)");
                        typeTests.add("is_string({0})");
                        defaultVal = "''";
                        break;
                    }
                    throw new AssertionError("Unsupported union member type: " + memberType);
            }
        }

        String result = Joiner.on(" || ").join(typeTests);
        // Null test needs to come first which is why it's not included in the set.
        int maxSize = 1;
        if (unionType.isNullable()) {
            result = "!isset({0}) || " + result;
            maxSize++;
        }

        return Pair.of(
                result,
                // in case of nullable tyle, we can return correct default value
                typeTests.size() > maxSize ? "null" : defaultVal
        );
    }
}
