/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments;

import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.impl.psi.LeafPsiElement;
import consulo.language.psi.*;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ArrayUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.extensions.GroovyNamedArgumentProvider;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Map;

/**
 * @author ilyas
 */
public class GrArgumentLabelImpl extends GroovyPsiElementImpl implements GrArgumentLabel {

  public GrArgumentLabelImpl(@Nonnull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitArgumentLabel(this);
  }

  @Override
  public String toString() {
    return "Argument label";
  }

  @Nullable
  @RequiredReadAction
  private PsiPolyVariantReference getReferenceFromNamedArgumentProviders() {
    PsiElement namedArgument = getParent();
    if (!(namedArgument instanceof GrNamedArgument)) return null;

    PsiElement nameElement = getNameElement();
    if (!(nameElement instanceof LeafPsiElement)) return null;

    IElementType elementType = ((LeafPsiElement)nameElement).getElementType();
    if (elementType != GroovyTokenTypes.mIDENT && !CommonClassNames.JAVA_LANG_STRING.equals(TypesUtil.getBoxedTypeName(elementType))) {
      return null;
    }

    GrCall call = PsiUtil.getCallByNamedParameter((GrNamedArgument)namedArgument);
    if (call == null) return null;

    String labelName = getName();

    Map<String,NamedArgumentDescriptor> providers = GroovyNamedArgumentProvider.getNamedArgumentsFromAllProviders(call, labelName, false);
    if (providers != null) {
      NamedArgumentDescriptor descr = providers.get(labelName);
      if (descr != null) {
        PsiPolyVariantReference res = descr.createReference(this);
        if (res != null) {
          return res;
        }
      }
    }

    return null;
  }

  @Nonnull
  @RequiredReadAction
  private PsiPolyVariantReference getRealReference() {
    PsiReference[] otherReferences = ReferenceProvidersRegistry.getReferencesFromProviders(this, PsiReferenceService.Hints.NO_HINTS);
    PsiPolyVariantReference reference = getReferenceFromNamedArgumentProviders();

    if (otherReferences.length == 0) {
      if (reference != null) {
        return reference;
      }
      else {
        return new PsiPolyVariantReferenceBase<PsiElement>(this) {
          @Nonnull
          @Override
          @RequiredReadAction
          public Object[] getVariants() {
            return ArrayUtil.EMPTY_OBJECT_ARRAY;
          }

          @Nonnull
          @Override
          @RequiredReadAction
          public ResolveResult[] multiResolve(boolean incompleteCode) {
            return ResolveResult.EMPTY_ARRAY;
          }
        };
      }
    }
    else {
      if (reference != null) {
        PsiReference[] refs = new PsiReference[otherReferences.length + 1];
        refs[0] = reference;
        //noinspection ManualArrayCopy
        for (int i = 0; i < otherReferences.length; i++) {
          refs[i + 1] = otherReferences[i];
        }

        otherReferences = refs;
      }

      return new PsiMultiReference(otherReferences, this);
    }
  }

  @Override
  @RequiredReadAction
  public PsiReference getReference() {
    PsiElement name = getNameElement();
    return name instanceof GrLiteral || name instanceof LeafPsiElement ? this : null;
  }

  @Override
  @Nullable
  @RequiredReadAction
  public String getName() {
    PsiElement element = getNameElement();
    if (element instanceof GrLiteral) {
      return convertToString(((GrLiteral)element).getValue());
    }
    if (element instanceof GrExpression) {
      Object value = JavaPsiFacade.getInstance(getProject()).getConstantEvaluationHelper().computeConstantExpression(element);
      if (value instanceof String) {
        return (String)value;
      }
    }

    IElementType elemType = element.getNode().getElementType();
    if (GroovyTokenTypes.mIDENT == elemType || TokenSets.KEYWORDS.contains(elemType)) {
      return element.getText();
    }

    return convertToString(GrLiteralImpl.getLiteralValue(element));
  }

  private static String convertToString(Object value) {
    if (value instanceof String) {
      return (String)value;
    }

    if (value instanceof Number) {
      return value.toString();
    }
    return null;
  }

  @Override
  @RequiredReadAction
  public PsiElement getElement() {
    return this;
  }

  @Override
  @RequiredReadAction
  public TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }

  @Override
  @Nullable
  @RequiredReadAction
  public PsiElement resolve() {
    return advancedResolve().getElement();
  }

  @Nonnull
  @Override
  @RequiredReadAction
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    ResolveResult[] results = getRealReference().multiResolve(incompleteCode);
    if (results instanceof GroovyResolveResult[]) {
      return (GroovyResolveResult[])results;
    }
    else {
      GroovyResolveResult[] results1 = new GroovyResolveResult[results.length];
      for (int i = 0; i < results.length; i++) {
        ResolveResult result = results[i];
        PsiElement element = result.getElement();
        if (element == null) {
          results1[i] = GroovyResolveResult.EMPTY_RESULT;
        }
        else {
          results1[i] = new GroovyResolveResultImpl(element, true);
        }
      }
      return results1;
    }
  }

  @Nonnull
  @Override
  @RequiredReadAction
  public GroovyResolveResult advancedResolve() {
    return PsiImplUtil.extractUniqueResult(multiResolve(false));
  }

  @Override
  @Nonnull
  @RequiredReadAction
  public String getCanonicalText() {
    if (resolve() instanceof PsiMember member && member instanceof PsiNamedElement) {
      PsiClass clazz = member.getContainingClass();
      if (clazz != null) {
        String qName = clazz.getQualifiedName();
        if (qName != null) {
          return qName + "." + member.getName();
        }
      }
    }

    return getText();
  }

  @Override
  @RequiredWriteAction
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return getRealReference().handleElementRename(newElementName);
  }

  @Override
  @RequiredWriteAction
  public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException {
    return getRealReference().bindToElement(element);
  }

  @Override
  @RequiredReadAction
  public boolean isReferenceTo(PsiElement element) {
    return getRealReference().isReferenceTo(element);
  }

  @Override
  @Nonnull
  @RequiredReadAction
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  @RequiredReadAction
  public boolean isSoft() {
    return false;
  }

  @Override
  @Nonnull
  @RequiredReadAction
  public PsiElement getNameElement() {
    PsiElement element = getFirstChild();
    assert element != null;
    return element;
  }

  @Override
  @RequiredReadAction
  public GrExpression getExpression() {
    PsiElement nameElement = getNameElement();
    if (nameElement instanceof GrParenthesizedExpression parenthesizedExpr) return parenthesizedExpr.getOperand();
    return null;
  }

  @Override
  @Nullable
  public PsiType getExpectedArgumentType() { // TODO use GroovyNamedArgumentProvider to determinate expected argument type.
    return null;
  }

  @Override
  @RequiredReadAction
  public PsiType getLabelType() {
    PsiElement el = getNameElement();
    if (el instanceof GrParenthesizedExpression parenthesizedExpr) {
      return parenthesizedExpr.getType();
    }

    ASTNode node = el.getNode();
    if (node == null) {
      return null;
    }

    PsiType nodeType = TypesUtil.getPsiType(el, node.getElementType());
    if (nodeType != null) {
      return nodeType;
    }
    return TypesUtil.createType(CommonClassNames.JAVA_LANG_STRING, this);
  }

  @Override
  @RequiredReadAction
  public GrNamedArgument getNamedArgument() {
    PsiElement parent = getParent();
    assert parent instanceof GrNamedArgument;
    return (GrNamedArgument)parent;
  }

  @Override
  @RequiredWriteAction
  public PsiElement setName(@Nonnull String newName) {
    PsiImplUtil.setName(newName, getNameElement());
    return this;
  }
}
