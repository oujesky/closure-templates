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
import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.msgs.internal.IcuSyntaxUtils;
import com.google.template.soy.msgs.internal.MsgUtils;
import com.google.template.soy.msgs.internal.MsgUtils.MsgPartsAndIds;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPart.Case;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralCaseSpec;
import com.google.template.soy.msgs.restricted.SoyMsgPluralPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import com.google.template.soy.phpsrc.internal.GenPhpExprsVisitor.GenPhpExprsVisitorFactory;
import com.google.template.soy.phpsrc.internal.TranslateToPhpExprVisitor.TranslateToPhpExprVisitorFactory;
import com.google.template.soy.phpsrc.restricted.PhpExpr;
import com.google.template.soy.phpsrc.restricted.PhpExprUtils;
import com.google.template.soy.phpsrc.restricted.PhpFunctionExprBuilder;
import com.google.template.soy.phpsrc.restricted.PhpStringExpr;
import com.google.template.soy.soytree.AbstractParentSoyNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.MsgPluralNode;
import com.google.template.soy.soytree.MsgSelectNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyNode.MsgPlaceholderInitialNode;
import com.google.template.soy.soytree.SoyNode.MsgSubstUnitNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Class to generate PHP code for one {@link MsgNode}.
 *
 */
final class MsgFuncGenerator {

    static interface MsgFuncGeneratorFactory {
        public MsgFuncGenerator create(MsgNode node, LocalVariableStack localVarExprs);
    }

    /** The msg node to generate the function calls from. */
    private final MsgNode msgNode;

    /** The generated msg id with the same algorithm for translation service. */
    private final long msgId;

    private final ImmutableList<SoyMsgPart> msgParts;

    private final GenPhpExprsVisitor genPhpExprsVisitor;

    /** The function builder for the prepare_*() method **/
    private final PhpFunctionExprBuilder prepareFunc;

    /** The function builder for the render_*() method **/
    private final PhpFunctionExprBuilder renderFunc;

    private final TranslateToPhpExprVisitor translateToPhpExprVisitor;


    @AssistedInject
    MsgFuncGenerator(GenPhpExprsVisitorFactory genPhpExprsVisitorFactory,
                     TranslateToPhpExprVisitorFactory translateToPhpExprVisitorFactory,
                     @Assisted MsgNode msgNode,
                     @Assisted LocalVariableStack localVarExprs) {
        this.msgNode = msgNode;
        this.genPhpExprsVisitor = genPhpExprsVisitorFactory.create(localVarExprs);
        this.translateToPhpExprVisitor = translateToPhpExprVisitorFactory.create(localVarExprs);
        String translator = PhpExprUtils.TRANSLATOR_NAME;

        if (this.msgNode.isPlrselMsg()) {
            if (this.msgNode.isPluralMsg()) {
                this.prepareFunc = new PhpFunctionExprBuilder(translator + "::preparePlural");
                this.renderFunc = new PhpFunctionExprBuilder(translator + "::renderPlural");
            } else {
                this.prepareFunc = new PhpFunctionExprBuilder(translator + "::prepareIcu");
                this.renderFunc = new PhpFunctionExprBuilder(translator + "::renderIcu");
            }
        } else if (this.msgNode.isRawTextMsg()) {
            this.prepareFunc = new PhpFunctionExprBuilder(translator + "::prepareLiteral");
            this.renderFunc = new PhpFunctionExprBuilder(translator + "::renderLiteral");
        } else {
            this.prepareFunc = new PhpFunctionExprBuilder(translator + "::prepare");
            this.renderFunc = new PhpFunctionExprBuilder(translator + "::render");
        }

        MsgPartsAndIds msgPartsAndIds = MsgUtils.buildMsgPartsAndComputeMsgIdForDualFormat(msgNode);
        Preconditions.checkNotNull(msgPartsAndIds);

        this.msgId = msgPartsAndIds.id;
        this.msgParts = msgPartsAndIds.parts;

        Preconditions.checkState(!msgParts.isEmpty());
    }

    /**
     * Return the PhpStringExpr for the render function call, because we know render always return a
     * string in PHP runtime.
     */

    PhpStringExpr getPhpExpr() {
        PhpStringExpr msgFunc;

        if (this.msgNode.isPlrselMsg()) {
            msgFunc = this.msgNode.isPluralMsg() ? phpFuncForPluralMsg() : phpFuncForSelectMsg();
        } else {
            msgFunc = this.msgNode.isRawTextMsg() ? phpFuncForRawTextMsg() : phpFuncForGeneralMsg();
        }

        return msgFunc;
    }

    private PhpStringExpr phpFuncForRawTextMsg() {
        String phpMsgText = processMsgPartsHelper(msgParts, nullEscaper);

        prepareFunc.addArg(msgId)
                .addArg(phpMsgText)
                .addArg(getPhpMsgDesc())
                .addArg(getPhpMsgMeaning());
        return renderFunc.addArg(prepareFunc.asPhpExpr())
                .asPhpStringExpr();
    }

    private PhpStringExpr phpFuncForGeneralMsg() {
        String phpMsgText = processMsgPartsHelper(msgParts, nullEscaper);
        Map<PhpExpr, PhpExpr> nodePhpVarToPhpExprMap = collectVarNameListAndToPhpExprMap();

        prepareFunc.addArg(msgId)
                .addArg(phpMsgText)
                .addArg(PhpExprUtils.convertIterableToPhpArrayExpr(nodePhpVarToPhpExprMap.keySet()))
                .addArg(getPhpMsgDesc())
                .addArg(getPhpMsgMeaning());

        return renderFunc.addArg(prepareFunc.asPhpExpr())
                .addArg(PhpExprUtils.convertMapToPhpExpr(nodePhpVarToPhpExprMap))
                .asPhpStringExpr();
    }

    private PhpStringExpr phpFuncForPluralMsg() {
        SoyMsgPluralPart pluralPart = (SoyMsgPluralPart) msgParts.get(0);
        MsgPluralNode pluralNode = msgNode.getRepPluralNode(pluralPart.getPluralVarName());
        Map<PhpExpr, PhpExpr> nodePhpVarToPhpExprMap = collectVarNameListAndToPhpExprMap();
        Map<PhpExpr, PhpExpr> caseSpecStrToMsgTexts = new LinkedHashMap<>();

        for (Case<SoyMsgPluralCaseSpec> pluralCase : pluralPart.getCases()) {
            caseSpecStrToMsgTexts.put(
                    new PhpStringExpr("'" + pluralCase.spec() + "'"),
                    new PhpStringExpr("'" + processMsgPartsHelper(pluralCase.parts(), nullEscaper) + "'"));
        }

        prepareFunc.addArg(msgId)
                .addArg(PhpExprUtils.convertMapToPhpExpr(caseSpecStrToMsgTexts))
                .addArg(PhpExprUtils.convertIterableToPhpArrayExpr(nodePhpVarToPhpExprMap.keySet()))
                .addArg(getPhpMsgDesc())
                .addArg(getPhpMsgMeaning());

        // Translates {@link MsgPluralNode#pluralExpr} into a Python lookup expression.
        // Note that pluralExpr represent the Soy expression inside the attributes of a plural tag.
        PhpExpr pluralPhpExpr = translateToPhpExprVisitor.exec(pluralNode.getExpr());

        return renderFunc.addArg(prepareFunc.asPhpExpr())
                .addArg(pluralPhpExpr)
                .addArg(PhpExprUtils.convertMapToPhpExpr(nodePhpVarToPhpExprMap))
                .asPhpStringExpr();
    }

    private PhpStringExpr phpFuncForSelectMsg() {
        Map<PhpExpr, PhpExpr> nodePhpVarToPhpExprMap = collectVarNameListAndToPhpExprMap();

        ImmutableList<SoyMsgPart> msgPartsInIcuSyntax =
                IcuSyntaxUtils.convertMsgPartsToEmbeddedIcuSyntax(msgParts, true);
        String phpMsgText = processMsgPartsHelper(msgPartsInIcuSyntax, nullEscaper);

        prepareFunc.addArg(msgId)
                .addArg(phpMsgText)
                .addArg(PhpExprUtils.convertIterableToPhpArrayExpr(nodePhpVarToPhpExprMap.keySet()))
                .addArg(getPhpMsgDesc())
                .addArg(getPhpMsgMeaning());

        return renderFunc.addArg(prepareFunc.asPhpExpr())
                .addArg(PhpExprUtils.convertMapToPhpExpr(nodePhpVarToPhpExprMap))
                .asPhpStringExpr();
    }

    private PhpExpr getNullOrString(String text)
    {
        if (text == null)
        {
            return new PhpExpr("null", Integer.MAX_VALUE);
        }

        String exprText = BaseUtils.escapeToSoyString(text, false);
        return new PhpStringExpr(exprText);
    }

    private PhpExpr getPhpMsgMeaning() {
        return getNullOrString(msgNode.getMeaning());
    }

    private PhpExpr getPhpMsgDesc() {
        return getNullOrString(msgNode.getDesc());
    }

    /**
     * Private helper to process and collect all variables used within this msg node for code
     * generation.
     *
     * @return A Map populated with all the variables used with in this message node, using
     *         {@link MsgPlaceholderInitialNode#genBasePhName}.
     */
    private Map<PhpExpr, PhpExpr> collectVarNameListAndToPhpExprMap() {
        Map<PhpExpr, PhpExpr> nodePhpVarToPhpExprMap = new LinkedHashMap<>();
        for (Map.Entry<String, MsgSubstUnitNode> entry : msgNode.getVarNameToRepNodeMap().entrySet()) {
            MsgSubstUnitNode substUnitNode = entry.getValue();
            PhpExpr substPhpExpr = null;

            if (substUnitNode instanceof MsgPlaceholderNode) {
                MsgPlaceholderInitialNode phInitialNode =
                        (MsgPlaceholderInitialNode) ((AbstractParentSoyNode<?>) substUnitNode).getChild(0);

                if (phInitialNode instanceof PrintNode || phInitialNode instanceof CallNode) {
                    substPhpExpr = PhpExprUtils.concatPhpExprs(genPhpExprsVisitor.exec(phInitialNode))
                            .toPhpString();
                }

                // when the placeholder is generated by HTML tags
                if (phInitialNode instanceof MsgHtmlTagNode) {
                    substPhpExpr = PhpExprUtils.concatPhpExprs(
                            genPhpExprsVisitor.execOnChildren((ParentSoyNode<?>) phInitialNode))
                            .toPhpString();
                }
            } else if (substUnitNode instanceof MsgPluralNode) {
                // Translates {@link MsgPluralNode#pluralExpr} into a PHP lookup expression.
                // Note that {@code pluralExpr} represents the soy expression of the {@code plural} attr,
                // i.e. the {@code $numDrafts} in {@code {plural $numDrafts}...{/plural}}.
                substPhpExpr = translateToPhpExprVisitor.exec(((MsgPluralNode) substUnitNode).getExpr());
            } else if (substUnitNode instanceof MsgSelectNode) {
                substPhpExpr = translateToPhpExprVisitor.exec(((MsgSelectNode) substUnitNode).getExpr());
            }

            if (substPhpExpr != null) {
                nodePhpVarToPhpExprMap.put(new PhpStringExpr("'" + entry.getKey() + "'"), substPhpExpr);
            }
        }

        return nodePhpVarToPhpExprMap;
    }

    /**
     * Private helper to build valid PHP string for a list of {@link SoyMsgPart}s.
     *
     * <p>It only processes {@link SoyMsgRawTextPart} and {@link SoyMsgPlaceholderPart} and ignores
     * others, because we didn't generate a direct string for plural and select nodes.
     *
     * <p>For {@link SoyMsgRawTextPart}, it appends the raw text and applies necessary escaping; For
     * {@link SoyMsgPlaceholderPart}, it turns the placeholder's variable name into PHP replace
     * format.
     *
     * @param parts The SoyMsgPart parts to convert.
     * @param escaper A Function which provides escaping for raw text.
     *
     * @return A String representing all the {@code parts} in PHP.
     */
    private static String processMsgPartsHelper(ImmutableList<SoyMsgPart> parts,
                                                Function<String, String> escaper) {
        StringBuilder rawMsgTextSb = new StringBuilder();
        for (SoyMsgPart part : parts) {
            if (part instanceof SoyMsgRawTextPart) {
                rawMsgTextSb.append(escaper.apply(
                        ((SoyMsgRawTextPart) part).getRawText()));
            }

            if (part instanceof SoyMsgPlaceholderPart) {
                String phName = ((SoyMsgPlaceholderPart) part).getPlaceholderName();
                rawMsgTextSb.append("{" + phName + "}");
            }

        }
        return rawMsgTextSb.toString();
    }

    /**
     * A mapper which does nothing.
     */
    private static Function<String, String> nullEscaper = new Function<String, String>() {
        @Override
        public String apply(String str) {
            return str;
        }
    };
}
