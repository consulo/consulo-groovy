/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.refactoring.rename.inplace;

import consulo.language.editor.refactoring.ResolveSnapshotProvider;
import consulo.language.psi.*;
import consulo.logging.Logger;
import consulo.document.Document;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.editor.refactoring.ResolveSnapshotProvider;
import java.util.HashMap;

import consulo.logging.Logger;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Map;

/**
 * @author Maxim.Medvedev
 */
public class GroovyResolveSnapshot extends ResolveSnapshotProvider.ResolveSnapshot {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.rename.inplace.GroovyResolveSnapshot");

  private final Map<SmartPsiElementPointer, SmartPsiElementPointer> myReferencesMap =
    new HashMap<SmartPsiElementPointer, SmartPsiElementPointer>();
  private final Project myProject;
  private final Document myDocument;

  public GroovyResolveSnapshot(final PsiElement scope) {
    myProject = scope.getProject();
    myDocument = PsiDocumentManager.getInstance(myProject).getDocument(scope.getContainingFile());
    final SmartPointerManager pointerManager = SmartPointerManager.getInstance(myProject);
    final Map<PsiElement, SmartPsiElementPointer> pointers = new HashMap<PsiElement, SmartPsiElementPointer>();
    scope.accept(new GroovyPsiElementVisitor(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(GrReferenceExpression refExpr) {
        if (!refExpr.isQualified()) {
          PsiElement resolved = refExpr.resolve();
          if (resolved instanceof GrMember) {
            SmartPsiElementPointer key = pointerManager.createSmartPsiElementPointer(refExpr);
            SmartPsiElementPointer value = pointers.get(resolved);
            if (value == null) {
              value = pointerManager.createSmartPsiElementPointer(resolved);
              pointers.put(resolved, value);
            }
            myReferencesMap.put(key, value);
          }
        }
        super.visitReferenceExpression(refExpr);
      }
    }));
  }

  public void apply(String hidingLocalName) {
    PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
    for (Map.Entry<SmartPsiElementPointer, SmartPsiElementPointer> entry : myReferencesMap.entrySet()) {
      qualify(entry.getKey().getElement(), entry.getValue().getElement(), hidingLocalName);
    }
  }

  private static void qualify(PsiElement referent, PsiElement referee, String hidingLocalName) {
    if (referent instanceof GrReferenceExpression && referee instanceof GrMember) {
      GrReferenceExpression ref = ((GrReferenceExpression)referent);
      if (!ref.isQualified() && hidingLocalName.equals(ref.getReferenceName())) {
        PsiUtil.qualifyMemberReference(ref, (GrMember)referee, hidingLocalName);
      }
    }
  }
}
