/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.impl.intentions.declaration;

import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiType;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.groovy.impl.localize.GroovyIntentionLocalize;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.editor.template.TemplateManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.impl.template.expressions.ChooseTypeExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SupertypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.ClosureParameterEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrSetStrongTypeIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return GroovyIntentionLocalize.grSetStrongTypeIntentionName();
    }

    @Override
    @RequiredWriteAction
    protected void processIntention(@Nonnull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
        PsiElement parent = element.getParent();

        PsiElement elementToBuildTemplate;
        GrVariable[] variables;
        if (parent instanceof GrVariable variable && variable.getParent() instanceof GrVariableDeclaration varDecl) {
            variables = varDecl.getVariables();
            elementToBuildTemplate = varDecl;
        }
        else if (parent instanceof GrVariable variable && variable.getParent() instanceof GrForInClause forInClause) {
            variables = new GrVariable[]{variable};
            elementToBuildTemplate = forInClause.getParent();
        }
        else if (parent instanceof GrVariableDeclaration varDecl) {
            variables = varDecl.getVariables();
            elementToBuildTemplate = varDecl;
        }
        else if (parent instanceof GrParameter param && param.getParent() instanceof GrParameterList paramList) {
            variables = new GrVariable[]{param};
            elementToBuildTemplate = paramList.getParent();
        }
        else if (parent instanceof GrVariable variable) {
            variables = new GrVariable[]{variable};
            elementToBuildTemplate = variable;
        }
        else {
            return;
        }

        List<TypeConstraint> types = new ArrayList<>();

        if (parent.getParent() instanceof GrForInClause) {
            types.add(SupertypeConstraint.create(PsiUtil.extractIteratedType((GrForInClause) parent.getParent())));
        }
        else {
            for (GrVariable variable : variables) {
                GrExpression initializer = variable.getInitializerGroovy();
                if (initializer != null) {
                    PsiType type = initializer.getType();
                    if (type != null) {
                        types.add(SupertypeConstraint.create(type));
                    }
                }
                if (variable instanceof GrParameter parameter) {
                    PsiType type = getClosureParameterType(parameter);
                    if (type != null) {
                        types.add(SupertypeConstraint.create(type));
                    }
                }
            }
        }

        TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(elementToBuildTemplate);
        PsiManager manager = element.getManager();

        PsiElement replaceElement = setType(element, parent, elementToBuildTemplate);
        assert replaceElement != null;

        TypeConstraint[] constraints = types.toArray(new TypeConstraint[types.size()]);
        ChooseTypeExpression chooseTypeExpression = new ChooseTypeExpression(constraints, manager, replaceElement.getResolveScope());
        builder.replaceElement(replaceElement, chooseTypeExpression);


        PsiElement afterPostprocess = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(elementToBuildTemplate);
        Template template = builder.buildTemplate();
        TextRange range = afterPostprocess.getTextRange();
        Document document = editor.getDocument();
        document.deleteString(range.getStartOffset(), range.getEndOffset());

        TemplateManager templateManager = TemplateManager.getInstance(project);
        templateManager.startTemplate(editor, template);
    }

    @Nullable
    private static PsiType getClosureParameterType(@Nonnull PsiParameter parameter) {
        if (parameter.getDeclarationScope() instanceof GrClosableBlock closableBlock) {
            return ClosureParameterEnhancer.inferType(
                closableBlock,
                ((GrParameterList) parameter.getParent()).getParameterIndex(parameter)
            );
        }
        return null;
    }

    @Nullable
    private static PsiElement setType(PsiElement element, PsiElement parent, PsiElement elementToBuildTemplate) {
        GrModifierList modifierList = getModifierList(parent);

        if (modifierList != null && modifierList.hasModifierProperty(GrModifier.DEF) && modifierList.getModifiers().length == 1) {
            return PsiUtil.findModifierInList(modifierList, GrModifier.DEF);
        }
        else {
            PsiClassType typeToUse = TypesUtil.createType("Abc", element);
            if (elementToBuildTemplate instanceof GrVariableDeclaration) {
                ((GrVariableDeclaration) elementToBuildTemplate).setType(typeToUse);
            }
            else {
                ((GrVariable) parent).setType(typeToUse);
            }

            return getTypeElement(parent);
        }
    }

    @Nullable
    private static GrTypeElement getTypeElement(PsiElement parent) {
        if (parent instanceof GrVariable variable) {
            return variable.getTypeElementGroovy();
        }
        else {
            return ((GrVariableDeclaration) parent).getTypeElementGroovy();
        }
    }

    @Nullable
    private static GrModifierList getModifierList(PsiElement parent) {
        GrModifierList modifierList;

        if (parent instanceof GrVariable variable) {
            modifierList = variable.getModifierList();
        }
        else {
            modifierList = ((GrVariableDeclaration) parent).getModifierList();
        }
        return modifierList;
    }

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new PsiElementPredicate() {
            @Override
            @RequiredReadAction
            public boolean satisfiedBy(PsiElement element) {
                PsiElement parent = element.getParent();

                PsiElement pparent;
                if (isNameIdentifierOfVariable(element, parent) || isModifierListOfVar(element, parent)) {
                    pparent = parent.getParent();
                }
                else if (isModifierListOfVarDecl(element, parent)) {
                    pparent = parent;
                }
                else {
                    return false;
                }

                if (pparent instanceof GrVariableDeclaration varDecl) {
                    if (varDecl.getTypeElementGroovy() != null) {
                        return false;
                    }

                    GrVariable[] variables = varDecl.getVariables();
                    for (GrVariable variable : variables) {
                        if (isVarDeclaredWithInitializer(variable)) {
                            return true;
                        }
                    }
                }
                else if (pparent instanceof GrForInClause forInClause) {
                    GrVariable variable = forInClause.getDeclaredVariable();
                    return variable != null && variable.getTypeElementGroovy() == null && PsiUtil.extractIteratedType(forInClause) != null;
                }
                else if (parent instanceof GrParameter param && pparent instanceof GrParameterList) {
                    return param.getTypeElementGroovy() == null && getClosureParameterType(param) != null;
                }
                else {
                    GrVariable variable = (GrVariable) parent;
                    return variable.getTypeElementGroovy() == null && isVarDeclaredWithInitializer(variable);
                }

                return false;
            }

            private boolean isModifierListOfVarDecl(PsiElement element, PsiElement parent) {
                return parent instanceof GrVariableDeclaration varDecl && varDecl.getModifierList() == element;
            }

            private boolean isModifierListOfVar(PsiElement element, PsiElement parent) {
                return parent instanceof GrVariable variable && variable.getModifierList() == element;
            }

            private boolean isNameIdentifierOfVariable(PsiElement element, PsiElement parent) {
                return parent instanceof GrVariable variable
                    && variable.getTypeElementGroovy() == null
                    && element == variable.getNameIdentifierGroovy();
            }
        };
    }

    @RequiredReadAction
    private static boolean isVarDeclaredWithInitializer(GrVariable variable) {
        GrExpression initializer = variable.getInitializerGroovy();
        return initializer != null && initializer.getType() != null;
    }
}
