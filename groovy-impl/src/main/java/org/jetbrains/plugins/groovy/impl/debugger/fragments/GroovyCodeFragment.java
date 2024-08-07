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
package org.jetbrains.plugins.groovy.impl.debugger.fragments;

import com.intellij.java.language.impl.psi.scope.NameHint;
import com.intellij.java.language.psi.*;
import consulo.language.editor.intention.IntentionFilterOwner;
import consulo.language.file.FileViewProvider;
import consulo.language.file.light.LightVirtualFile;
import consulo.language.impl.ast.FileElement;
import consulo.language.impl.file.SingleRootFileViewProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.impl.lang.psi.api.statements.expressions.GrUnAmbiguousClosureContainer;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author ven
 */
public class GroovyCodeFragment extends GroovyFileImpl implements JavaCodeFragment, IntentionFilterOwner, GrUnAmbiguousClosureContainer {
  private PsiType myThisType;
  private PsiType mySuperType;
  private ExceptionHandler myExceptionChecker;
  private IntentionFilterOwner.IntentionActionsFilter myFilter;
  private GlobalSearchScope myResolveScope;

  /**
   * map from a class's imported name (e.g. its short name or alias) to its qualified name
   */
  private final LinkedHashMap<String, GrImportStatement> myPseudoImports = new LinkedHashMap<>();
  private final ArrayList<GrImportStatement> myOnDemandImports = ContainerUtil.newArrayList();
  private FileViewProvider myViewProvider = null;

  public GroovyCodeFragment(Project project, CharSequence text) {
    this(project, new LightVirtualFile("Dummy.groovy", GroovyFileType.GROOVY_FILE_TYPE, text));
  }

  public GroovyCodeFragment(Project project, LightVirtualFile virtualFile) {
    super(new SingleRootFileViewProvider(PsiManager.getInstance(project), virtualFile, true));
    ((SingleRootFileViewProvider)getViewProvider()).forceCachedPsi(this);
  }

  public void setThisType(PsiType thisType) {
    myThisType = thisType;
  }

  public PsiType getSuperType() {
    return mySuperType;
  }

  public void setSuperType(PsiType superType) {
    mySuperType = superType;
  }

  @Override
  @Nonnull
  public FileViewProvider getViewProvider() {
    if (myViewProvider != null) return myViewProvider;
    return super.getViewProvider();
  }

  /**
   * @return list of imports in format "qname[:imported_name](,qname[:imported_name])*"
   */
  public String importsToString() {
    if (myPseudoImports.isEmpty()) return "";

    StringBuilder buffer = new StringBuilder();
    for (Map.Entry<String, GrImportStatement> entry : myPseudoImports.entrySet()) {
      final String importedName = entry.getKey();
      final GrImportStatement anImport = entry.getValue();


      //buffer.append(anImport.isStatic() ? "+" : "-");
      final String qname = anImport.getImportReference().getClassNameText();

      buffer.append(qname);
      buffer.append(':').append(importedName);
      buffer.append(',');
    }

    for (GrImportStatement anImport : myOnDemandImports) {
      //buffer.append(anImport.isStatic() ? "+" : "-");

      String packName = anImport.getImportReference().getClassNameText();
      buffer.append(packName);
      buffer.append(',');
    }
    buffer.deleteCharAt(buffer.length() - 1);
    return buffer.toString();
  }

  public void addImportsFromString(String imports) {
    for (String anImport : imports.split(",")) {
      int colon = anImport.indexOf(':');

      if (colon >= 0) {
        String qname = anImport.substring(0, colon);
        String importedName = anImport.substring(colon + 1);
        myPseudoImports.put(importedName, createSingleImport(qname, importedName));
      }
      else {
        myOnDemandImports.add(createImportOnDemand(anImport));
      }
    }
  }

  public void setVisibilityChecker(JavaCodeFragment.VisibilityChecker checker) {
  }

  public VisibilityChecker getVisibilityChecker() {
    return VisibilityChecker.EVERYTHING_VISIBLE;
  }

  public void setExceptionHandler(ExceptionHandler checker) {
    myExceptionChecker = checker;
  }

  public ExceptionHandler getExceptionHandler() {
    return myExceptionChecker;
  }

  public void setIntentionActionsFilter(IntentionActionsFilter filter) {
    myFilter = filter;
  }

  public IntentionActionsFilter getIntentionActionsFilter() {
    return myFilter;
  }

  public void forceResolveScope(GlobalSearchScope scope) {
    myResolveScope = scope;
  }

  public GlobalSearchScope getForcedResolveScope() {
    return myResolveScope;
  }

  public boolean importClass(PsiClass aClass) {
    return false;
  }

  public PsiType getThisType() {
    return myThisType;
  }

  @Override
  protected boolean processImports(PsiScopeProcessor processor,
                                   ResolveState state,
                                   PsiElement lastParent,
                                   PsiElement place,
                                   GrImportStatement[] importStatements,
                                   boolean onDemand) {
    if (!super.processImports(processor, state, lastParent, place, importStatements, onDemand)) {
      return false;
    }
    if (!processPseudoImports(processor, state, lastParent, place, onDemand)) {
      return false;
    }

    return true;
  }

  @Override
  protected GroovyCodeFragment clone() {
    final GroovyCodeFragment clone = (GroovyCodeFragment)cloneImpl((FileElement)calcTreeElement().clone());
    clone.myOriginalFile = this;
    clone.myPseudoImports.putAll(myPseudoImports);
    SingleRootFileViewProvider cloneViewProvider = new SingleRootFileViewProvider(getManager(), new LightVirtualFile(
      getName(),
      getLanguage(),
      getText()), false);
    cloneViewProvider.forceCachedPsi(clone);
    clone.myViewProvider = cloneViewProvider;
    return clone;
  }

  protected boolean processPseudoImports(PsiScopeProcessor processor,
                                         ResolveState state,
                                         PsiElement lastParent,
                                         PsiElement place,
                                         boolean onDemand) {
    if (onDemand) {
      if (!processImportsOnDemand(processor, state, lastParent, place)) {
        return false;
      }
    }
    else {
      if (!processSingleImports(processor, state, lastParent, place)) {
        return false;
      }
    }
    return true;
  }

  private boolean processImportsOnDemand(PsiScopeProcessor processor, ResolveState state, PsiElement parent, PsiElement place) {
    for (GrImportStatement anImport : myOnDemandImports) {
      if (!anImport.processDeclarations(processor, state, parent, place)) {
        return false;
      }
    }
    return true;
  }

  private boolean processSingleImports(PsiScopeProcessor processor, ResolveState state, PsiElement lastParent, PsiElement place) {
    NameHint nameHint = processor.getHint(NameHint.KEY);
    String name = nameHint != null ? nameHint.getName(state) : null;

    if (name != null) {
      final GrImportStatement anImport = myPseudoImports.get(name);
      if (anImport != null) {
        if (!anImport.processDeclarations(processor, state, lastParent, place)) {
          return false;
        }
      }
    }
    else {
      for (GrImportStatement anImport : myPseudoImports.values()) {
        if (!anImport.processDeclarations(processor, state, lastParent, place)) {
          return false;
        }
      }
    }
    return true;
  }

  @Nullable
  private GrImportStatement createImportOnDemand(@Nonnull String qname) {
    final PsiClass aClass = JavaPsiFacade.getInstance(getProject()).findClass(qname, getResolveScope());
    final boolean isStatic = aClass != null;

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());
    try {
      return factory.createImportStatement(qname, isStatic, true, null, this);
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  @Nullable
  private GrImportStatement createSingleImport(@Nonnull String qname, @Nullable String importedName) {
    final PsiClass aClass = JavaPsiFacade.getInstance(getProject()).findClass(qname, getResolveScope());
    final boolean isStatic = aClass == null;

    final String className = PsiNameHelper.getShortClassName(qname);
    final String alias = importedName == null || className.equals(importedName) ? null : importedName;
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());
    try {
      return factory.createImportStatement(qname, isStatic, false, alias, this);
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  public void clearImports() {
    myPseudoImports.clear();
    myOnDemandImports.clear();
  }
}
