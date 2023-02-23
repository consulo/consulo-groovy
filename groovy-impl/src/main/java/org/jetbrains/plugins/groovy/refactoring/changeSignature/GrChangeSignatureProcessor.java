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
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.java.impl.refactoring.changeSignature.ChangeSignatureViewDescriptor;
import com.intellij.java.language.psi.PsiMethod;
import consulo.application.ApplicationManager;
import consulo.language.editor.refactoring.changeSignature.ChangeSignatureProcessorBase;
import consulo.language.editor.refactoring.changeSignature.ChangeSignatureUsageProcessor;
import consulo.language.editor.refactoring.rename.RenameUtil;
import consulo.language.editor.refactoring.ui.ConflictsDialog;
import consulo.language.psi.PsiElement;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.util.collection.MultiMap;
import consulo.util.lang.ref.Ref;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class GrChangeSignatureProcessor extends ChangeSignatureProcessorBase {
  public static final Logger LOG =   Logger.getInstance(GrChangeSignatureProcessor.class);

  public GrChangeSignatureProcessor(Project project, GrChangeInfoImpl changeInfo) {
    super(project, changeInfo);
  }

  @Override
  public GrChangeInfoImpl getChangeInfo() {
    return (GrChangeInfoImpl)super.getChangeInfo();
  }

  @Nonnull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new ChangeSignatureViewDescriptor(getChangeInfo().getMethod());
  }

  @Override
  protected void refreshElements(PsiElement[] elements) {
    boolean condition = elements.length == 1 && elements[0] instanceof PsiMethod;
    LOG.assertTrue(condition);
    getChangeInfo().updateMethod((PsiMethod)elements[0]);
  }

  @Override
  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    MultiMap<PsiElement, String> conflictDescriptions = new MultiMap<PsiElement, String>();
    for (ChangeSignatureUsageProcessor usageProcessor : ChangeSignatureUsageProcessor.EP_NAME.getExtensions()) {
      final MultiMap<PsiElement, String> conflicts = usageProcessor.findConflicts(myChangeInfo, refUsages);
      for (PsiElement key : conflicts.keySet()) {
        Collection<String> collection = conflictDescriptions.get(key);
        if (collection.size() == 0) collection = new HashSet<String>();
        collection.addAll(conflicts.get(key));
        conflictDescriptions.put(key, collection);
      }
    }

    final UsageInfo[] usagesIn = refUsages.get();
    RenameUtil.addConflictDescriptions(usagesIn, conflictDescriptions);
    Set<UsageInfo> usagesSet = new HashSet<UsageInfo>(Arrays.asList(usagesIn));
    RenameUtil.removeConflictUsages(usagesSet);
    if (!conflictDescriptions.isEmpty()) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        throw new ConflictsInTestsException(conflictDescriptions.values());
      }

      ConflictsDialog dialog = prepareConflictsDialog(conflictDescriptions, usagesIn);
      dialog.show();
      if (!dialog.isOK()) {
        if (dialog.isShowConflicts()) prepareSuccessful();
        return false;
      }
    }
    refUsages.set(usagesSet.toArray(new UsageInfo[usagesSet.size()]));
    prepareSuccessful();
    return true;
  }
}
