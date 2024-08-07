/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.impl.refactoring.move;

import com.intellij.java.impl.refactoring.move.moveMembers.MoveMemberHandler;
import com.intellij.java.impl.refactoring.move.moveMembers.MoveMembersOptions;
import com.intellij.java.impl.refactoring.move.moveMembers.MoveMembersProcessor;
import com.intellij.java.impl.refactoring.util.ConflictsUtil;
import com.intellij.java.impl.refactoring.util.EnumConstantsUtil;
import com.intellij.java.impl.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.language.impl.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiUtilCore;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyChangeContextUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstantList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;

/**
 * @author Maxim.Medvedev
 */
@ExtensionImpl
public class MoveGroovyMemberHandler implements MoveMemberHandler {
  public boolean changeExternalUsage(@Nonnull MoveMembersOptions options, @Nonnull MoveMembersProcessor.MoveMembersUsageInfo usage) {
    final PsiElement element = usage.getElement();
    if (element == null || !element.isValid()) return true;

    if (usage.reference instanceof GrReferenceExpression) {
      GrReferenceExpression refExpr = (GrReferenceExpression)usage.reference;
      GrExpression qualifier = refExpr.getQualifierExpression();
      if (qualifier != null) {
        if (usage.qualifierClass != null) {
          changeQualifier(refExpr, usage.qualifierClass, usage.member);
        }
        else {
          refExpr.setQualifier(null);
        }
      }
      else { // no qualifier
        if (usage.qualifierClass != null) {
          changeQualifier(refExpr, usage.qualifierClass, usage.member);
        }
      }
      return true;
    }
    return false;
  }

  @Override
  @Nonnull
  public PsiMember doMove(@Nonnull MoveMembersOptions options,
                          @Nonnull PsiMember member,
                          PsiElement anchor,
                          @Nonnull PsiClass targetClass) {
    GroovyChangeContextUtil.encodeContextInfo(member);

    final PsiDocComment docComment;
    if (member instanceof PsiDocCommentOwner) {
      docComment = ((PsiDocCommentOwner)member).getDocComment();
    }
    else {
      docComment = null;
    }

    PsiMember moved;
    if (options.makeEnumConstant() &&
      member instanceof GrVariable &&
      EnumConstantsUtil.isSuitableForEnumConstant(((PsiVariable)member).getType(), targetClass)) {
      final GrEnumConstant prototype =
        createEnumConstant(member.getName(), ((GrVariable)member).getInitializerGroovy(), member.getProject());
      moved = (PsiMember)addEnumConstant(targetClass, prototype, anchor);
      member.delete();
    }
    else if (member instanceof GrEnumConstant) {
      moved = (PsiMember)addEnumConstant(targetClass, (GrEnumConstant)member, null);
    }
    else if (member instanceof GrField) {
      if (anchor != null) anchor = anchor.getParent();

      final GrVariableDeclaration parent = (GrVariableDeclaration)member.getParent();
      GrVariableDeclaration movedDeclaration = (GrVariableDeclaration)targetClass.addAfter(parent, anchor);

      int number = ArrayUtil.find(parent.getMembers(), member);
      final GrMember[] members = movedDeclaration.getMembers();
      for (int i = 0; i < number; i++) {
        members[i].delete();
      }
      for (int i = number + 1; i < members.length; i++) {
        members[i].delete();
      }

      if (member.getContainingClass().isInterface() && !targetClass.isInterface()) {
        //might need to make modifiers explicit, see IDEADEV-11416
        final PsiModifierList list = movedDeclaration.getModifierList();
        VisibilityUtil.setVisibility(list, VisibilityUtil.getVisibilityModifier(member.getModifierList()));
        list.setModifierProperty(PsiModifier.STATIC, member.hasModifierProperty(PsiModifier.STATIC));
        list.setModifierProperty(PsiModifier.FINAL, member.hasModifierProperty(PsiModifier.FINAL));
      }

      moved = movedDeclaration.getMembers()[0];
    }
    else if (member instanceof GrMethod) {
      moved = (PsiMember)targetClass.addAfter(member, anchor);
      if (member.getContainingClass().isInterface() && !targetClass.isInterface()) {
        //might need to make modifiers explicit, see IDEADEV-11416
        final PsiModifierList list = moved.getModifierList();
        assert list != null;
        list.setModifierProperty(PsiModifier.STATIC, member.hasModifierProperty(PsiModifier.STATIC));
        list.setModifierProperty(PsiModifier.FINAL, member.hasModifierProperty(PsiModifier.FINAL));
        VisibilityUtil.setVisibility(list, VisibilityUtil.getVisibilityModifier(member.getModifierList()));
      }

    }
    else {
      moved = (PsiMember)targetClass.addAfter(member, anchor);
    }

    if (docComment != null) {
      PsiElement prevSibling = moved.getPrevSibling();
      targetClass.addBefore(docComment, moved);
      addLineFeedIfNeeded(prevSibling);
      docComment.delete();
    }
    member.delete();
    return moved;
  }

  private static void addLineFeedIfNeeded(PsiElement prevSibling) {
    if (prevSibling == null) return;
    ASTNode node = prevSibling.getNode();
    IElementType type = node.getElementType();
    if (type == GroovyTokenTypes.mLCURLY) return;

    if (type == GroovyTokenTypes.mNLS) {
      String text = prevSibling.getText();
      int lfCount = StringUtil.countChars(text, '\n');
      if (lfCount < 2) {
        ASTNode parent = node.getTreeParent();
        parent.addLeaf(GroovyTokenTypes.mNLS, text + "\n", node);
        parent.removeChild(node);
      }
    }
    else {
      node.getTreeParent().addLeaf(GroovyTokenTypes.mNLS, "\n\n", node.getTreeNext());
    }
  }

  public void decodeContextInfo(@Nonnull PsiElement scope) {
    GroovyChangeContextUtil.decodeContextInfo(scope, null, null);
  }

  private static void changeQualifier(GrReferenceExpression refExpr, PsiClass aClass, PsiMember member) throws IncorrectOperationException {
    if (hasOnDemandStaticImport(refExpr, aClass)) {
      refExpr.setQualifier(null);
    }
    else if (!hasStaticImport(refExpr, member)) {
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(refExpr.getProject());

      refExpr.setQualifier(factory.createReferenceExpressionFromText(aClass.getName()));
      ((GrReferenceExpression)refExpr.getQualifierExpression()).bindToElement(aClass);
    }
  }

  private static boolean hasStaticImport(GrReferenceExpression refExpr, PsiMember member) {
    if (!(refExpr.getContainingFile() instanceof GroovyFile)) return false;

    final GrImportStatement[] imports = ((GroovyFile)refExpr.getContainingFile()).getImportStatements();
    for (GrImportStatement stmt : imports) {
      if (!stmt.isOnDemand() && stmt.resolveTargetClass() == member.getContainingClass() &&
        Comparing.strEqual(stmt.getImportReference().getReferenceName(), member.getName())) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasOnDemandStaticImport(final PsiElement element, final PsiClass aClass) {
    if (element.getContainingFile() instanceof GroovyFile) {
      final GrImportStatement[] importStatements = ((GroovyFile)element.getContainingFile()).getImportStatements();
      for (GrImportStatement stmt : importStatements) {
        final GrCodeReferenceElement ref = stmt.getImportReference();
        if (ref != null && stmt.isStatic() && stmt.isOnDemand() && ref.resolve() == aClass) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  @Nullable
  public PsiElement getAnchor(@Nonnull final PsiMember member, @Nonnull final PsiClass targetClass, Set<PsiMember> membersToMove) {
    if (member instanceof GrField && member.hasModifierProperty(PsiModifier.STATIC)) {
      final List<PsiField> referencedFields = new ArrayList<PsiField>();
      final GrExpression psiExpression = ((GrField)member).getInitializerGroovy();
      if (psiExpression != null) {
        psiExpression.accept(new GroovyRecursiveElementVisitor() {
          @Override
          public void visitReferenceExpression(final GrReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            final PsiElement psiElement = expression.resolve();
            if (psiElement instanceof GrField) {
              final GrField grField = (GrField)psiElement;
              if (grField.getContainingClass() == targetClass && !referencedFields.contains(grField)) {
                referencedFields.add(grField);
              }
            }
          }
        });
      }
      if (!referencedFields.isEmpty()) {
        Collections.sort(referencedFields, new Comparator<PsiField>() {
          public int compare(final PsiField o1, final PsiField o2) {
            return -PsiUtilCore.compareElementsByPosition(o1, o2);
          }
        });
        return referencedFields.get(0);
      }
    }
    return null;
  }

  private static GrEnumConstant createEnumConstant(String constantName, GrExpression initializerExpr, Project project)
    throws IncorrectOperationException {
    final GroovyPsiElementFactory elementFactory = GroovyPsiElementFactory.getInstance(project);
    final String enumConstantText = initializerExpr != null ? constantName + "(" + initializerExpr.getText() + ")" : constantName;
    return elementFactory.createEnumConstantFromText(enumConstantText);
  }

  private static PsiElement addEnumConstant(PsiClass targetClass, GrEnumConstant constant, @Nullable PsiElement anchor) {
    if (targetClass instanceof GrEnumTypeDefinition) {
      final GrEnumTypeDefinition enumeration = (GrEnumTypeDefinition)targetClass;
      final GrEnumConstantList constantList = enumeration.getEnumConstantList();
      if (constantList != null) {
        ASTNode node = constantList.getNode();
        node.addLeaf(GroovyTokenTypes.mCOMMA, ",", node.getFirstChildNode());
        return constantList.addBefore(constant, constantList.getFirstChild());
      }
      else {
        final PsiElement parent = constant.getParent();
        assert parent instanceof GrEnumConstantList;
        final GrEnumConstantList constListCopy = ((GrEnumConstantList)targetClass.add(parent));
        return constListCopy.getEnumConstants()[0];
      }
    }
    return (anchor != null ? targetClass.addAfter(constant, anchor) : targetClass.add(constant));
  }

  @Override
  public MoveMembersProcessor.MoveMembersUsageInfo getUsage(@Nonnull PsiMember member,
                                                            @Nonnull PsiReference psiReference,
                                                            @Nonnull Set<PsiMember> membersToMove,
                                                            @Nonnull PsiClass targetClass) {
    PsiElement ref = psiReference.getElement();
    if (ref instanceof GrReferenceExpression) {
      GrReferenceExpression refExpr = (GrReferenceExpression)ref;
      GrExpression qualifier = refExpr.getQualifier();
      if (RefactoringHierarchyUtil.willBeInTargetClass(refExpr, membersToMove, targetClass, true)) {
        // both member and the reference to it will be in target class
        if (!RefactoringUtil.isInMovedElement(refExpr, membersToMove)) {
          if (qualifier != null) {
            return new MoveMembersProcessor.MoveMembersUsageInfo(member, refExpr, null, qualifier, psiReference);  // remove qualifier
          }
        }
        else if (qualifier instanceof GrReferenceExpression && ((GrReferenceExpression)qualifier).isReferenceTo(member.getContainingClass())) {
          return new MoveMembersProcessor.MoveMembersUsageInfo(member, refExpr, null, qualifier, psiReference);  // change qualifier
        }
      }
      else {
        // member in target class, the reference will be outside target class
        if (qualifier == null) {
          return new MoveMembersProcessor.MoveMembersUsageInfo(member, refExpr, targetClass, refExpr, psiReference); // add qualifier
        }
        else {
          return new MoveMembersProcessor.MoveMembersUsageInfo(member, refExpr, targetClass, qualifier, psiReference); // change qualifier
        }
      }
    }
    return null;
  }

  @Override
  public void checkConflictsOnUsage(@Nonnull MoveMembersProcessor.MoveMembersUsageInfo usageInfo,
                                    @Nullable String newVisibility,
                                    @Nullable PsiModifierList modifierListCopy,
                                    @Nonnull PsiClass targetClass,
                                    @Nonnull Set<PsiMember> membersToMove,
                                    @Nonnull MultiMap<PsiElement, String> conflicts) {
    final PsiElement element = usageInfo.getElement();
    if (element == null) return;

    final PsiMember member = usageInfo.member;
    if (element instanceof GrReferenceExpression) {
      GrExpression qualifier = ((GrReferenceExpression)element).getQualifier();
      PsiClass accessObjectClass = null;
      if (qualifier != null) {
        accessObjectClass = (PsiClass)PsiUtil.getAccessObjectClass(qualifier).getElement();
      }

      if (!JavaResolveUtil.isAccessible(member, targetClass, modifierListCopy, element, accessObjectClass, null)) {
        String visibility = newVisibility != null ? newVisibility : VisibilityUtil.getVisibilityStringToDisplay(member);
        String message = RefactoringBundle.message("0.with.1.visibility.is.not.accessible.from.2",
                                                   RefactoringUIUtil.getDescription(member, false),
                                                   visibility,
                                                   RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(element), true));
        conflicts.putValue(member, CommonRefactoringUtil.capitalize(message));
      }
    }
  }

  @Override
  public void checkConflictsOnMember(@Nonnull PsiMember member,
                                     @Nullable String newVisibility,
                                     @Nullable PsiModifierList modifierListCopy,
                                     @Nonnull PsiClass targetClass,
                                     @Nonnull Set<PsiMember> membersToMove,
                                     @Nonnull MultiMap<PsiElement, String> conflicts) {
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }
}
