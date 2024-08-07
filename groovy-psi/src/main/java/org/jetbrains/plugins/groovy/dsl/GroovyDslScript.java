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
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.java.language.psi.PsiType;
import consulo.application.ApplicationManager;
import consulo.component.ProcessCanceledException;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.util.ProcessingContext;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import groovy.lang.Closure;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.jetbrains.plugins.groovy.dsl.holders.CustomMembersHolder;
import org.jetbrains.plugins.groovy.dsl.toplevel.ContextFilter;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ClassUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;

/**
 * @author peter
 */
public class GroovyDslScript {
  private static final Logger LOG = Logger.getInstance(GroovyDslScript.class);
  private final Project project;
  @Nullable
  private final VirtualFile file;
  private final GroovyDslExecutor executor;
  private final String myPath;
  private final FactorTree myFactorTree;

  public GroovyDslScript(final Project project,
                         @Nullable VirtualFile file,
                         @Nonnull GroovyDslExecutor executor,
                         String path) {
    this.project = project;
    this.file = file;
    this.executor = executor;
    myPath = path;
    myFactorTree = new FactorTree(project, executor);
  }


  public boolean processExecutor(PsiScopeProcessor processor,
                                 final PsiType psiType,
                                 final PsiElement place,
                                 final PsiFile placeFile,
                                 final String qname,
                                 ResolveState state) {
    CustomMembersHolder holder = myFactorTree.retrieve(place, placeFile, qname);
    GroovyClassDescriptor descriptor = new GroovyClassDescriptor(psiType, place, placeFile);
    try {
      if (holder == null) {
        holder = addGdslMembers(descriptor, qname, psiType);
        myFactorTree.cache(descriptor, holder);
      }

      return holder.processMembers(descriptor, processor, state);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      handleDslError(e);
      return true;
    }
  }

  private CustomMembersHolder addGdslMembers(GroovyClassDescriptor descriptor, String qname, final PsiType psiType) {
    final ProcessingContext ctx = new ProcessingContext();
    ctx.put(ClassUtil.getClassKey(qname), psiType);
    ctx.put(GdslUtil.INITIAL_CONTEXT, descriptor);
    try {
      if (!isApplicable(executor, descriptor, ctx)) {
        return CustomMembersHolder.EMPTY;
      }

      return executor.processVariants(descriptor, ctx, psiType);
    }
    catch (InvokerInvocationException e) {
      Throwable cause = e.getCause();
      if (cause instanceof ProcessCanceledException) {
        throw (ProcessCanceledException)cause;
      }
      if (cause instanceof OutOfMemoryError) {
        throw (OutOfMemoryError)cause;
      }
      handleDslError(e);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (OutOfMemoryError e) {
      throw e;
    }
    catch (Throwable e) { // To handle exceptions in definition script
      handleDslError(e);
    }
    return CustomMembersHolder.EMPTY;
  }

  private static boolean isApplicable(@Nonnull GroovyDslExecutor executor,
                                      GroovyClassDescriptor descriptor,
                                      final ProcessingContext ctx) {
    List<Pair<ContextFilter, Closure>> enhancers = executor.getEnhancers();
    if (enhancers == null) {
      LOG.error("null enhancers");
      return false;
    }
    for (Pair<ContextFilter, Closure> pair : enhancers) {
      if (pair.first.isApplicable(descriptor, ctx)) {
        return true;
      }
    }
    return false;
  }

  public boolean handleDslError(Throwable e) {
    if (project.isDisposed() || ApplicationManager.getApplication().isUnitTestMode()) {
      return true;
    }
    if (file != null) {
      DslErrorReporter.getInstance().invokeDslErrorPopup(e, project, file);
    }
    else {
      LOG.info("Error when executing internal GDSL " + myPath, e);
      GdslUtil.stopGdsl();
    }
    return false;
  }

  @Override
  public String toString() {
    return "GroovyDslScript: " + myPath;
  }

  @Nullable
  public MultiMap getStaticInfo() {
    return executor.getStaticInfo();
  }
}
