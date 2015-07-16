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

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.exprtree.*;
import com.google.template.soy.exprtree.ExprNode.OperatorNode;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.Operator.Operand;
import com.google.template.soy.exprtree.Operator.SyntaxElement;
import com.google.template.soy.exprtree.OperatorNodes.AndOpNode;
import com.google.template.soy.exprtree.OperatorNodes.OrOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NullCoalescingOpNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import com.google.template.soy.internal.targetexpr.ExprUtils;
import com.google.template.soy.phpsrc.restricted.*;
import com.google.template.soy.shared.internal.NonpluginFunction;
import com.google.template.soy.shared.internal.SharedModule;
import com.google.template.soy.types.SoyObjectType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Visitor for translating a Soy expression (in the form of an {@link ExprNode}) into an
 * equivalent PHP expression.
 *
 */
final class TranslateToPhpExprVisitor extends AbstractReturningExprNodeVisitor<PhpExpr> {

    private static final SoyError SOY_PHP_SRC_FUNCTION_NOT_FOUND =
            SoyError.of("Failed to find SoyPhpSrcFunction ''{0}''.");

    /**
     * Errors in this visitor generate Python source that immediately explodes.
     * Users of Soy are expected to check the error reporter before using the gencode;
     * if they don't, this should apprise them.
     * TODO(brndn): consider changing the visitor to return {@code Optional<PyExpr>}
     * and returning {@link Optional#absent()} on error.
     */
    private static final PhpExpr ERROR =
            new PhpExpr("throw new Exception('Soy compilation failed');", Integer.MAX_VALUE);

    /**
     * Injectable factory for creating an instance of this class.
     */
    interface TranslateToPhpExprVisitorFactory {
        TranslateToPhpExprVisitor create(LocalVariableStack localVarExprs);
    }


    private final LocalVariableStack localVarExprs;

    /** Map of all SoyPhpSrcFunctions (name to function). */
    private final ImmutableMap<String, SoyPhpSrcFunction> soyPhpSrcFunctionsMap;


    @AssistedInject
    TranslateToPhpExprVisitor(
            ImmutableMap<String, SoyPhpSrcFunction> soyPhpSrcFunctionsMap,
            @Assisted LocalVariableStack localVarExprs,
            ErrorReporter errorReporter) {
        super(errorReporter);
        this.localVarExprs = localVarExprs;
        this.soyPhpSrcFunctionsMap = soyPhpSrcFunctionsMap;
    }

    /**
     * Helper mapper to apply {@link #visit} to an iterable of ExprNode.
     */
    private final Function<ExprNode, PhpExpr> VISIT_MAPPER = new Function<ExprNode, PhpExpr>() {
        @Override
        public PhpExpr apply(ExprNode node) {
            return visit(node);
        }
    };


    /**
     * Method that returns code to access a named parameter.
     * @param paramName the name of the parameter.
     * @return The code to access the value of that parameter.
     */
    static String genCodeForParamAccess(String paramName) {
        return genCodeForLiteralKeyAccess("$opt_data", paramName);
    }


    // -----------------------------------------------------------------------------------------------
    // Implementation for a dummy root node.


    @Override protected PhpExpr visitExprRootNode(ExprRootNode node) {
        return visit(node.getRoot());
    }


    // -----------------------------------------------------------------------------------------------
    // Implementations for primitives.


    @Override protected PhpExpr visitPrimitiveNode(PrimitiveNode node) {
        // Note: ExprNode.toSourceString() technically returns a Soy expression. In the case of
        // primitives, the result is usually also the correct PHP expression.
        return new PhpExpr(node.toSourceString(), Integer.MAX_VALUE);
    }

    @Override protected PhpExpr visitStringNode(StringNode node) {
        return new PhpStringExpr(node.toSourceString());
    }

    @Override protected PhpExpr visitNullNode(NullNode node) {
        return new PhpExpr("null", Integer.MAX_VALUE);
    }

    @Override protected PhpExpr visitBooleanNode(BooleanNode node) {
        return new PhpExpr(node.getValue() ? "true" : "false", Integer.MAX_VALUE);
    }


    // -----------------------------------------------------------------------------------------------
    // Implementations for collections.


    @Override protected PhpExpr visitListLiteralNode(ListLiteralNode node) {
        return PhpExprUtils.convertIterableToPhpArrayExpr(
                Iterables.transform(node.getChildren(), VISIT_MAPPER));
    }

    @Override protected PhpExpr visitMapLiteralNode(MapLiteralNode node) {
        Preconditions.checkArgument(node.numChildren() % 2 == 0);
        Map<PhpExpr, PhpExpr> dict = new LinkedHashMap<>();

        for (int i = 0, n = node.numChildren(); i < n; i += 2) {
            ExprNode keyNode = node.getChild(i);
            ExprNode valueNode = node.getChild(i + 1);
            dict.put(visit(keyNode), visit(valueNode));
        }

        return PhpExprUtils.convertMapToPhpExpr(dict);
    }


    // -----------------------------------------------------------------------------------------------
    // Implementations for data references.


    @Override protected PhpExpr visitVarRefNode(VarRefNode node) {
        return visitNullSafeNode(node);
    }

    @Override protected PhpExpr visitDataAccessNode(DataAccessNode node) {
        return visitNullSafeNode(node);
    }

    private PhpExpr visitNullSafeNode(ExprNode node) {
        StringBuilder nullSafetyPrefix = new StringBuilder();
        String refText = visitNullSafeNodeRecurse(node, nullSafetyPrefix);

        if (nullSafetyPrefix.length() == 0) {
            return new PhpExpr(refText, Integer.MAX_VALUE);
        } else {
            return new PhpExpr(
                    nullSafetyPrefix + refText,
                    PhpExprUtils.phpPrecedenceForOperator(Operator.CONDITIONAL));
        }
    }

    private String visitNullSafeNodeRecurse(ExprNode node, StringBuilder nullSafetyPrefix) {
        switch (node.getKind()) {
            case VAR_REF_NODE: {
                VarRefNode varRef = (VarRefNode) node;
                if (varRef.isInjected()) {
                    // Case 1: Injected data reference.
                    if (varRef.isNullSafeInjected()) {
                        nullSafetyPrefix.append("$opt_ijData === null ? null : ");
                    }
                    return genCodeForLiteralKeyAccess("$opt_ijData", varRef.getName());
                } else {
                    PhpExpr translation = localVarExprs.getVariableExpression(varRef.getName());
                    if (translation != null) {
                        // Case 2: In-scope local var.
                        return translation.getText();
                    } else {
                        // Case 3: Data reference.
                        return genCodeForLiteralKeyAccess("$opt_data", varRef.getName());
                    }
                }
            }

            case FIELD_ACCESS_NODE:
            case ITEM_ACCESS_NODE: {
                DataAccessNode dataAccess = (DataAccessNode) node;
                // First recursively visit base expression.
                String refText = visitNullSafeNodeRecurse(dataAccess.getBaseExprChild(), nullSafetyPrefix);

                // Generate null safety check for base expression.
                if (dataAccess.isNullSafe()) {
                    nullSafetyPrefix.append(refText + " === null ? null : ");
                }

                // Generate access to field
                if (node.getKind() == ExprNode.Kind.FIELD_ACCESS_NODE) {
                    FieldAccessNode fieldAccess = (FieldAccessNode) node;
                    return genCodeForFieldAccess(
                            fieldAccess.getBaseExprChild().getType(), refText, fieldAccess.getFieldName());
                } else {
                    ItemAccessNode itemAccess = (ItemAccessNode) node;
                    Kind baseKind = itemAccess.getBaseExprChild().getType().getKind();
                    PhpExpr keyPhpExpr = visit(itemAccess.getKeyExprChild());
                    return genCodeForKeyAccess(refText, keyPhpExpr.getText());
//                    if (baseKind == Kind.MAP || baseKind == Kind.RECORD) {
//                        return genCodeForKeyAccess(refText, keyPhpExpr.getText());
//                    } else {
//                        return new PhpFunctionExprBuilder("Runtime::keySafeDataAccess")
//                                .addArg(new PhpExpr(refText, Integer.MAX_VALUE))
//                                .addArg(keyPhpExpr).build();
//                    }
                }
            }

            default: {
                PhpExpr value = visit(node);
                return PhpExprUtils.maybeProtect(value, Integer.MAX_VALUE).getText();
            }
        }
    }

    @Override protected PhpExpr visitGlobalNode(GlobalNode node) {
        return new PhpExpr("$GLOBALS['" + node.toSourceString() + "']", Integer.MAX_VALUE);
    }


    // -----------------------------------------------------------------------------------------------
    // Implementations for operators.


    @Override protected PhpExpr visitNotOpNode(NotOpNode node) {
        // Note: Since we're using Soy syntax for the 'not' operator, we'll end up generating code with
        // a space between the token '!' and the subexpression that it negates. This isn't the usual
        // style, but it should be fine (besides, it's more readable with the extra space).
        return genPhpExprUsingSoySyntaxWithNewToken(node, "!");
    }

    @Override protected PhpExpr visitAndOpNode(AndOpNode node) {
        return genPhpExprUsingSoySyntaxWithNewToken(node, "&&");
    }

    @Override protected PhpExpr visitOrOpNode(OrOpNode node) {
        return genPhpExprUsingSoySyntaxWithNewToken(node, "||");
    }

    @Override protected PhpExpr visitOperatorNode(OperatorNode node) {
        return genPhpExprUsingSoySyntax(node);
    }

    @Override protected PhpExpr visitNullCoalescingOpNode(NullCoalescingOpNode node) {
        List<PhpExpr> children = visitChildren(node);

        PhpExpr conditionalExpr = PhpExprUtils.genPhpNotNullCheck(children.get(0));
        PhpExpr trueExpr = children.get(0);
        PhpExpr falseExpr = children.get(1);

        return genTernaryConditional(conditionalExpr, trueExpr, falseExpr);
    }


    @Override protected PhpExpr visitPlusOpNode(PlusOpNode node) {
        // PHP has stricter type casting between strings and other primitives than Soy, so addition
        // must be sent through the typeSafeAdd utility to emulate that behavior.
        List<PhpExpr> operandPhpExprs = visitChildren(node);

        return new PhpExpr("Runtime::typeSafeAdd(" + operandPhpExprs.get(0).getText()
                + ", " + operandPhpExprs.get(1).getText() + ")", Integer.MAX_VALUE);
    }


    @Override protected PhpExpr visitConditionalOpNode(ConditionalOpNode node) {
        // Retrieve the operands.
        Operator op = Operator.CONDITIONAL;
        List<SyntaxElement> syntax = op.getSyntax();
        List<PhpExpr> operandExprs = visitChildren(node);

        Operand conditionalOperand = ((Operand) syntax.get(0));
        PhpExpr conditionalExpr = operandExprs.get(conditionalOperand.getIndex());
        Operand trueOperand = ((Operand) syntax.get(4));
        PhpExpr trueExpr = operandExprs.get(trueOperand.getIndex());
        Operand falseOperand = ((Operand) syntax.get(8));
        PhpExpr falseExpr = operandExprs.get(falseOperand.getIndex());

        return genTernaryConditional(conditionalExpr, trueExpr, falseExpr);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The source of available functions is a look-up map provided by Guice in
     * {@link SharedModule#provideSoyFunctionsMap}.
     *
     * @see NonpluginFunction
     * @see SoyPhpSrcFunction
     */
    @Override protected PhpExpr visitFunctionNode(FunctionNode node)  {
        String fnName = node.getFunctionName();

        // Handle nonplugin functions.
        NonpluginFunction nonpluginFn = NonpluginFunction.forFunctionName(fnName);
        if (nonpluginFn != null) {
            return visitNonPluginFunction(node, nonpluginFn);
        }

        // Handle plugin functions.
        SoyPhpSrcFunction pluginFn = soyPhpSrcFunctionsMap.get(fnName);
        if (pluginFn == null) {
            errorReporter.report(node.getSourceLocation(), SOY_PHP_SRC_FUNCTION_NOT_FOUND, fnName);
            return ERROR;
        }
        List<PhpExpr> args = visitChildren(node);
        return pluginFn.computeForPhpSrc(args);
    }

    private PhpExpr visitNonPluginFunction(FunctionNode node, NonpluginFunction nonpluginFn) {
        switch (nonpluginFn) {
            case IS_FIRST:
                return visitForEachFunction(node, "__isFirst");
            case IS_LAST:
                return visitForEachFunction(node, "__isLast");
            case INDEX:
                return visitForEachFunction(node, "__index");
            case QUOTE_KEYS_IF_JS:
                // 'quoteKeysIfJs' is ignored in PHP.
                return visitMapLiteralNode((MapLiteralNode) node.getChild(0));
            case CHECK_NOT_NULL:
                return visitCheckNotNull(node);
            default:
                throw new AssertionError();
        }
    }

    private PhpExpr visitCheckNotNull(FunctionNode node) {
        PhpExpr childExpr = visit(node.getChild(0));
        return new PhpFunctionExprBuilder("Runtime::checkNotNull").addArg(childExpr).asPhpExpr();
    }

    private PhpExpr visitForEachFunction(FunctionNode node, String suffix) {
        String varName = ((VarRefNode) node.getChild(0)).getName();
        return localVarExprs.getVariableExpression(varName + suffix);
    }

    /**
     * Generates the code for key access given a key literal, e.g. {@code ['key']}.
     *
     * @param key the String literal value to be used as a key
     */
    private static String genCodeForLiteralKeyAccess(String containerExpr, String key) {
        return genCodeForKeyAccess(containerExpr, "'" + key + "'");
    }

    /**
     * Generates the code for key access given the name of a variable to be used as a key,
     * e.g. {@code [key]}.
     *
     * @param keyName the variable name to be used as a key
     */
    private static String genCodeForKeyAccess(String containerExpr, String keyName) {
        return containerExpr + "[" + keyName + "]";
    }

    /**
     * Generates the code for a field name access, e.g. "['bar']". If the base type is an
     * object type, then it delegates the generation of the PHP code to the type object.
     *
     * @param baseType the type of the object that contains the field
     * @param containerExpr an expression that evaluates to the container of the named field.
     *     This expression may have any operator precedence that binds more tightly than
     *     exponentiation.
     * @param fieldName the field name
     */
    private static String genCodeForFieldAccess(
            SoyType baseType, String containerExpr, String fieldName) {
        if (baseType != null && baseType.getKind() == SoyType.Kind.OBJECT) {
            SoyObjectType objType = (SoyObjectType) baseType;
            String accessExpr = objType.getFieldAccessExpr(
                    containerExpr, fieldName, SoyBackendKind.PHP_SRC);
            if (accessExpr != null) {
                return accessExpr;
            }
        }
        return genCodeForLiteralKeyAccess(containerExpr, fieldName);
    }

    /**
     * Generates a PHP expression for the given OperatorNode's subtree assuming that the PHP
     * expression for the operator uses the same syntax format as the Soy operator.
     *
     * @param opNode the OperatorNode whose subtree to generate a PHP expression for
     * @return the generated PHP expression
     */
    private PhpExpr genPhpExprUsingSoySyntax(OperatorNode opNode) {
        List<PhpExpr> operandPhpExprs = visitChildren(opNode);
        String newExpr = ExprUtils.genExprWithNewToken(opNode.getOperator(), operandPhpExprs, null);

        return new PhpExpr(newExpr, PhpExprUtils.phpPrecedenceForOperator(opNode.getOperator()));
    }

    /**
     * Generates a PHP expression for the given OperatorNode's subtree assuming that the PHP expression
     * for the operator uses the same syntax format as the Soy operator, with the exception that the
     * PHP operator uses a different token (e.g. "!" instead of "not").
     * @param opNode The OperatorNode whose subtree to generate a PHP expression for.
     * @param newToken The equivalent PHP operator's token.
     * @return The generated PHP expression.
     */
    private PhpExpr genPhpExprUsingSoySyntaxWithNewToken(OperatorNode opNode, String newToken) {
        List<PhpExpr> operandPhpExprs = visitChildren(opNode);
        String newExpr = ExprUtils.genExprWithNewToken(opNode.getOperator(), operandPhpExprs, newToken);

        return new PhpExpr(newExpr, PhpExprUtils.phpPrecedenceForOperator(opNode.getOperator()));
    }

    /**
     * Generates a ternary conditional PHP expression given the conditional and true/false
     * expressions.
     *
     * @param conditionalExpr the conditional expression
     * @param trueExpr the expression to execute if the conditional executes to true
     * @param falseExpr the expression to execute if the conditional executes to false
     * @return a ternary conditional expression
     */
    private PhpExpr genTernaryConditional(PhpExpr conditionalExpr, PhpExpr trueExpr, PhpExpr falseExpr) {
        int conditionalPrecedence = PhpExprUtils.phpPrecedenceForOperator(Operator.CONDITIONAL);
        StringBuilder exprSb = new StringBuilder()
                .append(PhpExprUtils.maybeProtect(conditionalExpr, conditionalPrecedence).getText())
                .append(" ? ")
                .append(PhpExprUtils.maybeProtect(trueExpr, conditionalPrecedence).getText())
                .append(" : ")
                .append(PhpExprUtils.maybeProtect(falseExpr, conditionalPrecedence).getText());

        return new PhpExpr(exprSb.toString(), conditionalPrecedence);
    }

}
