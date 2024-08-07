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
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.editor.template.TemplateManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;

/**
 * @author Max Medvedev
 */
public class GrSetStrongTypeIntention extends Intention {

  @Override
  protected void processIntention(@Nonnull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    PsiElement parent = element.getParent();

    PsiElement elementToBuildTemplate;
    GrVariable[] variables;
    if (parent instanceof GrVariable && parent.getParent() instanceof GrVariableDeclaration) {
      variables = ((GrVariableDeclaration)parent.getParent()).getVariables();
      elementToBuildTemplate = parent.getParent();
    }
    else if (parent instanceof GrVariable && parent.getParent() instanceof GrForInClause) {
      variables = new GrVariable[]{(GrVariable)parent};
      elementToBuildTemplate = parent.getParent().getParent();
    }
    else if (parent instanceof GrVariableDeclaration) {
      variables = ((GrVariableDeclaration)parent).getVariables();
      elementToBuildTemplate = parent;
    }
    else if (parent instanceof GrParameter && parent.getParent() instanceof GrParameterList) {
      variables = new GrVariable[]{(GrVariable)parent};
      elementToBuildTemplate = parent.getParent().getParent();
    }
    else if (parent instanceof GrVariable) {
      variables = new GrVariable[]{((GrVariable)parent)};
      elementToBuildTemplate = parent;
    }
    else {
      return;
    }

    ArrayList<TypeConstraint> types = new ArrayList<TypeConstraint>();

    if (parent.getParent() instanceof GrForInClause) {
      types.add(SupertypeConstraint.create(PsiUtil.extractIteratedType((GrForInClause)parent.getParent())));
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
        if (variable instanceof GrParameter) {
          final PsiParameter parameter = (PsiParameter)variable;
          final PsiType type = getClosureParameterType(parameter);
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


    final PsiElement afterPostprocess = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(elementToBuildTemplate);
    final Template template = builder.buildTemplate();
    TextRange range = afterPostprocess.getTextRange();
    Document document = editor.getDocument();
    document.deleteString(range.getStartOffset(), range.getEndOffset());

    TemplateManager templateManager = TemplateManager.getInstance(project);
    templateManager.startTemplate(editor, template);
  }

  @Nullable
  private static PsiType getClosureParameterType(@Nonnull PsiParameter parameter) {
    final PsiElement scope = parameter.getDeclarationScope();
    final PsiType type;
    if (scope instanceof GrClosableBlock) {
      type =
        ClosureParameterEnhancer.inferType((GrClosableBlock)scope, ((GrParameterList)parameter.getParent()).getParameterIndex(parameter));
    }
    else {
      type = null;
    }
    return type;
  }

  @Nullable
  private static PsiElement setType(PsiElement element, PsiElement parent, PsiElement elementToBuildTemplate) {
    GrModifierList modifierList = getModifierList(parent);

    if (modifierList != null && modifierList.hasModifierProperty(GrModifier.DEF) && modifierList.getModifiers().length == 1) {
      return PsiUtil.findModifierInList(modifierList, GrModifier.DEF);
    }
    else {
      final PsiClassType typeToUse = TypesUtil.createType("Abc", element);
      if (elementToBuildTemplate instanceof GrVariableDeclaration) {
        ((GrVariableDeclaration)elementToBuildTemplate).setType(typeToUse);
      }
      else {
        ((GrVariable)parent).setType(typeToUse);
      }

      return getTypeElement(parent);
    }
  }

  @Nullable
  private static GrTypeElement getTypeElement(PsiElement parent) {
    if (parent instanceof GrVariable) {
      return ((GrVariable)parent).getTypeElementGroovy();
    }
    else {
      return ((GrVariableDeclaration)parent).getTypeElementGroovy();
    }
  }

  @Nullable
  private static GrModifierList getModifierList(PsiElement parent) {
    GrModifierList modifierList;

    if (parent instanceof GrVariable) {
      modifierList = ((GrVariable)parent).getModifierList();
    }
    else {
      modifierList = ((GrVariableDeclaration)parent).getModifierList();
    }
    return modifierList;
  }

  @Nonnull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
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

        if (pparent instanceof GrVariableDeclaration) {
          if (((GrVariableDeclaration)pparent).getTypeElementGroovy() != null) return false;

          GrVariable[] variables = ((GrVariableDeclaration)pparent).getVariables();
          for (GrVariable variable : variables) {
            if (isVarDeclaredWithInitializer(variable)) return true;
          }
        }
        else if (pparent instanceof GrForInClause) {
          final GrVariable variable = ((GrForInClause)pparent).getDeclaredVariable();
          return variable != null && variable.getTypeElementGroovy() == null && PsiUtil.extractIteratedType((GrForInClause)pparent) != null;
        }
        else if (parent instanceof GrParameter && pparent instanceof GrParameterList) {
          return ((GrParameter)parent).getTypeElementGroovy() == null && getClosureParameterType((PsiParameter)parent) != null;
        }
        else {
          final GrVariable variable = (GrVariable)parent;
          return variable.getTypeElementGroovy() == null && isVarDeclaredWithInitializer(variable);
        }

        return false;
      }

      private boolean isModifierListOfVarDecl(PsiElement element, PsiElement parent) {
        return parent instanceof GrVariableDeclaration && ((GrVariableDeclaration)parent).getModifierList() == element;
      }

      private boolean isModifierListOfVar(PsiElement element, PsiElement parent) {
        return parent instanceof GrVariable && ((GrVariable)parent).getModifierList() == element;
      }


      private boolean isNameIdentifierOfVariable(PsiElement element, PsiElement parent) {
        return parent instanceof GrVariable &&
          ((GrVariable)parent).getTypeElementGroovy() == null &&
          element == ((GrVariable)parent).getNameIdentifierGroovy();
      }
    };
  }

  private static boolean isVarDeclaredWithInitializer(GrVariable variable) {
    GrExpression initializer = variable.getInitializerGroovy();
    return initializer != null && initializer.getType() != null;
  }
}
