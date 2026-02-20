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
package org.jetbrains.plugins.groovy.impl.lang.completion;

import com.intellij.java.language.patterns.PsiJavaPatterns;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.editor.completion.*;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.ArrayUtil;
import org.jetbrains.plugins.groovy.impl.lang.completion.handlers.GroovyMethodSignatureInsertHandler;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMemberReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTagValueToken;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.CompletionProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessorImpl;

import jakarta.annotation.Nonnull;

/**
 * @author Max Medvedev
 */
public class GroovyDocCompletionProvider implements CompletionProvider
{
  public static void register(CompletionContributor contributor) {
    GroovyDocCompletionProvider provider = new GroovyDocCompletionProvider();
    contributor.extend(CompletionType.BASIC, PsiJavaPatterns.psiElement().inside(GrDocTagValueToken.class), provider);
  }

  @Override
  public void addCompletions(@Nonnull CompletionParameters parameters,
                                ProcessingContext context,
                                @Nonnull CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    GrDocMemberReference reference = PsiTreeUtil.getParentOfType(position, GrDocMemberReference.class);
    if (reference == null) return;

    GrDocReferenceElement holder = reference.getReferenceHolder();
    PsiElement resolved;
    if (holder != null) {
      GrCodeReferenceElement referenceElement = holder.getReferenceElement();
      resolved = referenceElement != null ? referenceElement.resolve() : null;
    }
    else {
      resolved = PsiUtil.getContextClass(reference);
    }
    if (resolved instanceof PsiClass) {
      ResolverProcessorImpl propertyProcessor = CompletionProcessor.createPropertyCompletionProcessor(reference);
      resolved.processDeclarations(propertyProcessor, ResolveState.initial(), null, reference);
      PsiElement[] propertyCandidates = ResolveUtil.mapToElements(propertyProcessor.getCandidates());
      ResolverProcessorImpl methodProcessor = CompletionProcessor.createPropertyCompletionProcessor(reference);

      resolved.processDeclarations(methodProcessor, ResolveState.initial(), null, reference);

      PsiElement[] methodCandidates = ResolveUtil.mapToElements(methodProcessor.getCandidates());

      PsiElement[] elements = ArrayUtil.mergeArrays(propertyCandidates, methodCandidates);

      for (PsiElement psiElement : elements) {
        LookupElement element = GroovyCompletionUtil.createLookupElement((PsiNamedElement)psiElement);
        if (psiElement instanceof PsiMethod) {
          element = ((LookupElementBuilder)element).withInsertHandler(new GroovyMethodSignatureInsertHandler());
        }
        result.addElement(element);
      }
    }
  }
}
