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
package org.jetbrains.plugins.groovy.impl.lang.completion;

import com.intellij.java.impl.codeInsight.completion.JavaGlobalMemberLookupElement;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMember;
import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.dataholder.Key;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

import jakarta.annotation.Nonnull;

/**
* Created by Max Medvedev on 14/05/14
*/
class StaticImportInsertHandler implements InsertHandler<JavaGlobalMemberLookupElement> {
  public static final InsertHandler<JavaGlobalMemberLookupElement> INSTANCE = new StaticImportInsertHandler();

  private StaticImportInsertHandler() {
  }

  private static boolean importAlreadyExists(final PsiMember member, final GroovyFile file, final PsiElement place) {
    final PsiManager manager = file.getManager();
    PsiScopeProcessor processor = new PsiScopeProcessor() {
      @Override
      public boolean execute(@Nonnull PsiElement element, @Nonnull ResolveState state) {
        return !manager.areElementsEquivalent(element, member);
      }

      @Override
      public <T> T getHint(@Nonnull Key<T> hintKey) {
        return null;
      }

      @Override
      public void handleEvent(@Nonnull Event event, Object associated) {
      }
    };

    boolean skipStaticImports = member instanceof PsiClass;
    final GrImportStatement[] imports = file.getImportStatements();
    final ResolveState initial = ResolveState.initial();
    for (GrImportStatement anImport : imports) {
      if (skipStaticImports == anImport.isStatic()) continue;
      if (!anImport.processDeclarations(processor, initial, null, place)) return true;
    }
    return false;
  }

  @Override
  public void handleInsert(InsertionContext context, JavaGlobalMemberLookupElement item) {
    GroovyInsertHandler.INSTANCE.handleInsert(context, item);
    final PsiMember member = item.getObject();
    PsiDocumentManager.getInstance(member.getProject()).commitDocument(context.getDocument());
    final GrReferenceExpression ref = PsiTreeUtil.
      findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), GrReferenceExpression.class, false);

    if (ref != null &&
        ref.getQualifier() == null &&
        context.getFile() instanceof GroovyFile &&
        !importAlreadyExists(member, ((GroovyFile)context.getFile()), ref) &&
        !PsiManager.getInstance(context.getProject()).areElementsEquivalent(ref.resolve(), member)) {
      ref.bindToElementViaStaticImport(member);
    }

  }
}
