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
package org.jetbrains.plugins.groovy.impl.annotator;

import com.intellij.java.language.psi.*;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.colorScheme.TextAttributesKey;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.resolve.PsiElementProcessor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.project.DumbService;
import org.jetbrains.plugins.groovy.impl.highlighter.GroovySyntaxHighlighter;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Max Medvedev
 */
public class GrHighlightUtil {
  private static final Logger LOG = Logger.getInstance(GrHighlightUtil.class);

  private static Set<String> getReassignedNames(final PsiElement scope) {
    return CachedValuesManager.getManager(scope.getProject()).getCachedValue(scope, new CachedValueProvider<Set<String>>() {
      @Nullable
      @Override
      public Result<Set<String>> compute() {
        return Result.create(collectReassignedNames(scope), scope);
      }
    });
  }

  private static Set<String> collectReassignedNames(PsiElement scope) {
    final Set<String> result = new HashSet<>();
    PsiTreeUtil.processElements(scope, new PsiElementProcessor() {
      @Override
      public boolean execute(@Nonnull PsiElement element) {
        if (!(element instanceof GrReferenceExpression) || !((GrReferenceExpression)element).isQualified()) {
          return true;
        }

        GrReferenceExpression ref = (GrReferenceExpression)element;
        if (isWriteAccess(ref)) {
          String varName = ref.getReferenceName();
          if (!result.contains(varName)) {
            PsiElement target = ref.resolve();
            if (target instanceof GrVariable && ((GrVariable)target).getInitializerGroovy() != null ||
              target instanceof GrParameter) {
              result.add(varName);
            }
          }
        }
        return true;
      }
    });
    return result;
  }

  private static boolean isWriteAccess(GrReferenceExpression element) {
    return PsiUtil.isLValue(element) ||
      element.getParent() instanceof GrUnaryExpression && ((GrUnaryExpression)element.getParent()).isPostfix();
  }

  static boolean isReassigned(final GrVariable var) {
    LOG.assertTrue(!DumbService.getInstance(var.getProject()).isDumb());

    PsiMethod method = PsiTreeUtil.getParentOfType(var, PsiMethod.class);
    PsiNamedElement scope = method == null ? var.getContainingFile() : method;
    return scope != null && getReassignedNames(scope).contains(var.getName());
  }

  /**
   * @param resolved   declaration element
   * @param refElement reference to highlight. if null, 'resolved' is highlighted and no resolve is allowed.
   * @return
   */
  @Nullable
  static TextAttributesKey getDeclarationHighlightingAttribute(PsiElement resolved, @Nullable PsiElement refElement) {
    if (refElement != null && isReferenceWithLiteralName(refElement)) return null; //don't highlight literal references

    if (resolved instanceof PsiField || resolved instanceof GrVariable && ResolveUtil.isScriptField((GrVariable)resolved)) {
      boolean isStatic = ((PsiVariable)resolved).hasModifierProperty(PsiModifier.STATIC);
      return isStatic ? GroovySyntaxHighlighter.STATIC_FIELD : GroovySyntaxHighlighter.INSTANCE_FIELD;
    }
    else if (resolved instanceof GrAccessorMethod) {
      boolean isStatic = ((GrAccessorMethod)resolved).hasModifierProperty(PsiModifier.STATIC);
      return isStatic ? GroovySyntaxHighlighter.STATIC_PROPERTY_REFERENCE : GroovySyntaxHighlighter.INSTANCE_PROPERTY_REFERENCE;
    }
    else if (resolved instanceof PsiMethod) {

      if (isMethodWithLiteralName((PsiMethod)resolved)) return null; //don't highlight method with literal name

      if (((PsiMethod)resolved).isConstructor()) {
        if (refElement != null) {
          if (refElement.getNode().getElementType() == GroovyTokenTypes.kTHIS || //don't highlight this() or super()
            refElement.getNode().getElementType() == GroovyTokenTypes.kSUPER) {
            return null;
          }
          else {
            return GroovySyntaxHighlighter.CONSTRUCTOR_CALL;
          }
        }
        else {
          return GroovySyntaxHighlighter.CONSTRUCTOR_DECLARATION;
        }
      }
      else {
        boolean isStatic = ((PsiMethod)resolved).hasModifierProperty(PsiModifier.STATIC);
        if (GroovyPropertyUtils.isSimplePropertyAccessor((PsiMethod)resolved)) {
          return isStatic ? GroovySyntaxHighlighter.STATIC_PROPERTY_REFERENCE : GroovySyntaxHighlighter.INSTANCE_PROPERTY_REFERENCE;
        }
        else {
          if (refElement != null) {
            return isStatic ? GroovySyntaxHighlighter.STATIC_METHOD_ACCESS : GroovySyntaxHighlighter.METHOD_CALL;
          }
          else {
            return GroovySyntaxHighlighter.METHOD_DECLARATION;
          }
        }
      }
    }
    else if (resolved instanceof PsiTypeParameter) {
      return GroovySyntaxHighlighter.TYPE_PARAMETER;
    }
    else if (resolved instanceof PsiClass) {
      if (((PsiClass)resolved).isAnnotationType()) {
        return GroovySyntaxHighlighter.ANNOTATION;
      }
      else {
        return GroovySyntaxHighlighter.CLASS_REFERENCE;
      }
    }
    else if (resolved instanceof GrParameter) {
      boolean reassigned = isReassigned((GrParameter)resolved);
      return reassigned ? GroovySyntaxHighlighter.REASSIGNED_PARAMETER : GroovySyntaxHighlighter.PARAMETER;
    }
    else if (resolved instanceof GrVariable) {
      boolean reassigned = isReassigned((GrVariable)resolved);
      return reassigned ? GroovySyntaxHighlighter.REASSIGNED_LOCAL_VARIABLE : GroovySyntaxHighlighter.LOCAL_VARIABLE;
    }
    return null;
  }

  private static boolean isMethodWithLiteralName(@Nullable PsiMethod method) {
    if (method instanceof GrMethod) {
      final PsiElement nameIdentifier = ((GrMethod)method).getNameIdentifierGroovy();
      if (isStringNameElement(nameIdentifier)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isReferenceWithLiteralName(@Nullable PsiElement ref) {
    if (ref instanceof GrReferenceExpression) {
      final PsiElement nameIdentifier = ((GrReferenceExpression)ref).getReferenceNameElement();
      if (nameIdentifier != null && isStringNameElement(nameIdentifier)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isStringNameElement(@Nonnull PsiElement nameIdentifier) {
    final ASTNode node = nameIdentifier.getNode();
    if (node == null) return false;

    final IElementType nameElementType = node.getElementType();
    return TokenSets.STRING_LITERAL_SET.contains(nameElementType);
  }

  public static boolean isDeclarationAssignment(GrReferenceExpression refExpr) {
    return isAssignmentLhs(refExpr) && isScriptPropertyAccess(refExpr);
  }

  private static boolean isAssignmentLhs(GrReferenceExpression refExpr) {
    return refExpr.getParent() instanceof GrAssignmentExpression &&
      refExpr.equals(((GrAssignmentExpression)refExpr.getParent()).getLValue());
  }

  private static boolean isScriptPropertyAccess(GrReferenceExpression refExpr) {
    final GrExpression qualifier = refExpr.getQualifierExpression();
    if (qualifier == null) {
      final PsiClass clazz = PsiTreeUtil.getParentOfType(refExpr, PsiClass.class);
      if (clazz == null) { //script
        return true;
      }
      return false; //in class, a property should normally be defined, so it's not a declaration
    }

    final PsiType type = qualifier.getType();
    if (type instanceof PsiClassType &&
      !(qualifier instanceof GrReferenceExpression && ((GrReferenceExpression)qualifier).resolve() instanceof GroovyScriptClass)) {
      final PsiClassType classType = (PsiClassType)type;
      final PsiClass psiClass = classType.resolve();
      if (psiClass instanceof GroovyScriptClass) {
        return true;
      }
    }
    return false;
  }

  public static TextRange getMethodHeaderTextRange(PsiMethod method) {
    final PsiModifierList modifierList = method.getModifierList();
    final PsiParameterList parameterList = method.getParameterList();

    final TextRange textRange = modifierList.getTextRange();
    LOG.assertTrue(textRange != null, method.getClass() + ":" + method.getText());
    int startOffset = textRange.getStartOffset();
    int endOffset = parameterList.getTextRange().getEndOffset() + 1;

    return new TextRange(startOffset, endOffset);
  }

  @Nonnull
  public static PsiElement getElementToHighlight(@Nonnull GrReferenceElement refElement) {
    final PsiElement refNameElement = refElement.getReferenceNameElement();
    return refNameElement != null ? refNameElement : refElement;
  }

  public static TextRange getClassHeaderTextRange(GrTypeDefinition clazz) {
    final GrModifierList modifierList = clazz.getModifierList();
    final int startOffset = modifierList != null ? modifierList.getTextOffset() : clazz.getTextOffset();
    final GrImplementsClause implementsClause = clazz.getImplementsClause();

    final int endOffset;
    if (implementsClause != null) {
      endOffset = implementsClause.getTextRange().getEndOffset();
    }
    else {
      final GrExtendsClause extendsClause = clazz.getExtendsClause();
      if (extendsClause != null) {
        endOffset = extendsClause.getTextRange().getEndOffset();
      }
      else {
        endOffset = clazz.getNameIdentifierGroovy().getTextRange().getEndOffset();
      }
    }
    return new TextRange(startOffset, endOffset);
  }

  public static TextRange getInitializerHeaderTextRange(GrClassInitializer initializer) {
    final PsiModifierList modifierList = initializer.getModifierList();
    final GrOpenBlock block = initializer.getBlock();

    final TextRange textRange = modifierList.getTextRange();
    LOG.assertTrue(textRange != null, initializer.getClass() + ":" + initializer.getText());
    int startOffset = textRange.getStartOffset();
    int endOffset = block.getLBrace().getTextRange().getEndOffset() + 1;

    return new TextRange(startOffset, endOffset);

  }

  @Nullable
  public static GrMember findClassMemberContainer(@Nonnull GrReferenceExpression ref, @Nonnull PsiClass aClass) {
    for (PsiElement parent = ref.getParent(); parent != null && parent != aClass; parent = parent.getParent()) {
      if (parent instanceof GrMember && ((GrMember)parent).getContainingClass() == aClass) {
        return (GrMember)parent;
      }
    }
    return null;
  }
}
