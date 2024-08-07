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

package org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports;

import com.intellij.java.language.impl.psi.scope.NameHint;
import com.intellij.java.language.psi.*;
import consulo.application.util.CachedValueProvider;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.StubBasedPsiElement;
import consulo.language.psi.resolve.DelegatingScopeProcessor;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrImportStatementStub;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;
import org.jetbrains.plugins.groovy.lang.resolve.processors.GrDelegatingScopeProcessorWithHints;

import jakarta.annotation.Nullable;

/**
 * @author ilyas
 */
public class GrImportStatementImpl extends GrStubElementBase<GrImportStatementStub> implements GrImportStatement,
  StubBasedPsiElement<GrImportStatementStub> {

  public GrImportStatementImpl(@Nonnull ASTNode node) {
    super(node);
  }

  public GrImportStatementImpl(GrImportStatementStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }


  @Override
  public PsiElement getParent() {
    return getParentByStub();
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitImportStatement(this);
  }

  public String toString() {
    return "Import statement";
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor,
                                     @Nonnull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @Nonnull PsiElement place) {
    if (isAncestor(place)) {
      return true;
    }
    if (isStatic() && lastParent instanceof GrImportStatement) {
      return true;
    }

    if (isOnDemand()) {
      if (!processDeclarationsForMultipleElements(processor, lastParent, place, state)) {
        return false;
      }
    }
    else {
      if (!processDeclarationsForSingleElement(processor, lastParent, place, state)) {
        return false;
      }
    }

    return true;
  }

  private boolean isAncestor(@Nullable PsiElement place) {
    while (place instanceof GrCodeReferenceElement) {
      PsiElement parent = place.getParent();
      if (parent == this) {
        return true;
      }
      place = parent;
    }
    return false;
  }

  private boolean processDeclarationsForSingleElement(@Nonnull PsiScopeProcessor processor,
                                                      @Nullable PsiElement lastParent,
                                                      @Nonnull PsiElement place,
                                                      @Nonnull ResolveState state) {
    String name = getImportedName();
    if (name == null) {
      return true;
    }

    if (isStatic()) {
      return processSingleStaticImport(processor, state, name, lastParent, place);
    }

    NameHint nameHint = processor.getHint(NameHint.KEY);
    if (nameHint == null || name.equals(nameHint.getName(state))) {
      return processSingleClassImport(processor, state);
    }
    return true;
  }

  @Nullable
  private PsiClass resolveQualifier() {
    return LanguageCachedValueUtil.getCachedValue(this, new CachedValueProvider<PsiClass>() {
      @Nullable
      @Override
      public Result<PsiClass> compute() {
        GrCodeReferenceElement reference = getImportReference();
        GrCodeReferenceElement qualifier = reference == null ? null : reference.getQualifier();
        PsiElement target = qualifier == null ? null : qualifier.resolve();
        PsiClass clazz = target instanceof PsiClass ? (PsiClass)target : null;
        return Result.create(clazz, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT,
                             GrImportStatementImpl.this);
      }
    });
  }

  private boolean processSingleStaticImport(@Nonnull final PsiScopeProcessor processor,
                                            @Nonnull ResolveState state,
                                            @Nonnull String importedName,
                                            @Nullable PsiElement lastParent,
                                            @Nonnull PsiElement place) {
    final GrCodeReferenceElement ref = getImportReference();
    if (ref == null) {
      return true;
    }

    PsiClass clazz = resolveQualifier();
    if (clazz == null) {
      return true;
    }

    state = state.put(ClassHint.RESOLVE_CONTEXT, this);

    final String refName = ref.getReferenceName();

    final NameHint nameHint = processor.getHint(NameHint.KEY);
    final String hintName = nameHint == null ? null : nameHint.getName(state);

    if (hintName == null || importedName.equals(hintName)) {
      if (!clazz.processDeclarations(new GrDelegatingScopeProcessorWithHints(processor, refName, null), state,
                                     lastParent, place)) {
        return false;
      }
    }

    if (ResolveUtil.shouldProcessMethods(processor.getHint(ClassHint.KEY))) {
      if (hintName == null || importedName.equals(GroovyPropertyUtils.getPropertyNameByGetterName(hintName,
                                                                                                  true))) {
        if (!clazz.processDeclarations(new StaticGetterProcessor(refName, processor), state, lastParent, place)) {
          return false;
        }
      }

      if (hintName == null || importedName.equals(GroovyPropertyUtils.getPropertyNameBySetterName(hintName))) {
        if (!clazz.processDeclarations(new StaticSetterProcessor(refName, processor), state, lastParent, place)) {
          return false;
        }
      }
    }

    return true;
  }

  private boolean processSingleClassImport(@Nonnull PsiScopeProcessor processor, @Nonnull ResolveState state) {
    if (!ResolveUtil.shouldProcessClasses(processor.getHint(ClassHint.KEY))) {
      return true;
    }

    GrCodeReferenceElement ref = getImportReference();
    if (ref == null) {
      return true;
    }

    final PsiElement resolved = ref.resolve();
    if (!(resolved instanceof PsiClass)) {
      return true;
    }

    if (!isAliasedImport() && isFromSamePackage((PsiClass)resolved)) {
      return true; //don't process classes from the same package because such import statements are ignored by
      // compiler
    }

    return processor.execute(resolved, state.put(ClassHint.RESOLVE_CONTEXT, this));
  }

  private boolean isFromSamePackage(@Nonnull PsiClass resolved) {
    final String qualifiedName = resolved.getQualifiedName();
    final String packageName = ((GroovyFile)getContainingFile()).getPackageName();
    final String assumed = packageName + '.' + resolved.getName();
    return !packageName.isEmpty() && assumed.equals(qualifiedName);
  }

  private boolean processDeclarationsForMultipleElements(@Nonnull final PsiScopeProcessor processor,
                                                         @Nullable PsiElement lastParent,
                                                         @Nonnull PsiElement place,
                                                         @Nonnull ResolveState state) {
    GrCodeReferenceElement ref = getImportReference();
    if (ref == null) {
      return true;
    }

    if (isStatic()) {
      final PsiElement resolved = ref.resolve();
      if (resolved instanceof PsiClass) {
        state = state.put(ClassHint.RESOLVE_CONTEXT, this);
        final PsiClass clazz = (PsiClass)resolved;
        if (!clazz.processDeclarations(new DelegatingScopeProcessor(processor) {
          @Override
          public boolean execute(@Nonnull PsiElement element, @Nonnull ResolveState state) {
            if (element instanceof PsiMember && ((PsiMember)element).hasModifierProperty(PsiModifier.STATIC)) {
              return super.execute(element, state);
            }
            return true;
          }
        }, state, lastParent, place)) {
          return false;
        }
      }
    }
    else {
      if (ResolveUtil.shouldProcessClasses(processor.getHint(ClassHint.KEY))) {
        String qName = PsiUtil.getQualifiedReferenceText(ref);
        if (qName != null) {
          PsiPackage aPackage = JavaPsiFacade.getInstance(getProject()).findPackage(qName);
          if (aPackage != null && !((GroovyFile)getContainingFile()).getPackageName().equals(aPackage
                                                                                               .getQualifiedName())) {
            state = state.put(ClassHint.RESOLVE_CONTEXT, this);
            if (!aPackage.processDeclarations(processor, state, lastParent, place)) {
              return false;
            }
          }
        }
      }
    }
    return true;
  }

  @Override
  public GrCodeReferenceElement getImportReference() {
    GrImportStatementStub stub = getStub();
    if (stub != null) {
      String referenceText = stub.getReferenceText();
      if (referenceText == null) {
        return null;
      }

      return GroovyPsiElementFactory.getInstance(getProject()).createCodeReferenceElementFromText(referenceText);
    }

    return (GrCodeReferenceElement)findChildByType(GroovyElementTypes.REFERENCE_ELEMENT);
  }

  @Override
  @Nullable
  public String getImportedName() {
    if (isOnDemand()) {
      return null;
    }

    GrImportStatementStub stub = getStub();
    if (stub != null) {
      String name = stub.getAliasName();
      if (name != null) {
        return name;
      }

      String referenceText = stub.getReferenceText();
      if (referenceText == null) {
        return null;
      }

      return StringUtil.getShortName(referenceText);
    }


    PsiElement aliasNameElement = getAliasNameElement();
    if (aliasNameElement != null) {
      return aliasNameElement.getText();
    }

    GrCodeReferenceElement ref = getImportReference();
    return ref == null ? null : ref.getReferenceName();
  }

  @Override
  public boolean isStatic() {
    GrImportStatementStub stub = getStub();
    if (stub != null) {
      return stub.isStatic();
    }

    return findChildByType(GroovyTokenTypes.kSTATIC) != null;
  }

  @Override
  public boolean isAliasedImport() {
    GrImportStatementStub stub = getStub();
    if (stub != null) {
      return stub.getAliasName() != null;
    }
    return getAliasNameElement() != null;
  }

  @Override
  public boolean isOnDemand() {
    GrImportStatementStub stub = getStub();
    if (stub != null) {
      return stub.isOnDemand();
    }
    return findChildByType(GroovyTokenTypes.mSTAR) != null;
  }

  @Override
  @Nonnull
  public GrModifierList getAnnotationList() {
    GrImportStatementStub stub = getStub();
    if (stub != null) {
      return ObjectUtil.assertNotNull(getStubOrPsiChild(GroovyElementTypes.MODIFIERS));
    }
    return findNotNullChildByClass(GrModifierList.class);
  }

  @Nullable
  @Override
  public PsiClass resolveTargetClass() {
    final GrCodeReferenceElement ref = getImportReference();
    if (ref == null) {
      return null;
    }

    final PsiElement resolved;
    if (!isStatic() || isOnDemand()) {
      resolved = ref.resolve();
    }
    else {
      resolved = resolveQualifier();
    }

    return resolved instanceof PsiClass ? (PsiClass)resolved : null;
  }

  @Nullable
  @Override
  public PsiElement getAliasNameElement() {
    GrImportStatementStub stub = getStub();
    if (stub != null) {
      String alias = stub.getAliasName();
      if (alias == null) {
        return null;
      }

      GrImportStatement imp = GroovyPsiElementFactory.getInstance(getProject()).createImportStatementFromText
        ("import A as " + alias);
      return imp.getAliasNameElement();
    }

    return findChildByType(GroovyTokenTypes.mIDENT);
  }

  private static class StaticSetterProcessor extends StaticAccessorProcessor {

    public StaticSetterProcessor(String refName, PsiScopeProcessor processor) {
      super(refName, processor);
    }

    @Override
    protected boolean isAccessor(@Nonnull PsiMethod method) {
      return GroovyPropertyUtils.isSimplePropertySetter(method, getPropertyName());
    }
  }

  private static class StaticGetterProcessor extends StaticAccessorProcessor {

    public StaticGetterProcessor(String refName, PsiScopeProcessor processor) {
      super(refName, processor);
    }

    @Override
    protected boolean isAccessor(@Nonnull PsiMethod method) {
      return GroovyPropertyUtils.isSimplePropertyGetter(method, getPropertyName());
    }
  }

  /**
   * Created by Max Medvedev on 26/03/14
   */
  private abstract static class StaticAccessorProcessor extends GrDelegatingScopeProcessorWithHints {
    private final String myPropertyName;

    public StaticAccessorProcessor(@Nonnull String propertyName, @Nonnull PsiScopeProcessor processor) {
      super(processor, null, RESOLVE_KINDS_METHOD);
      myPropertyName = propertyName;
    }

    @Override
    public boolean execute(@Nonnull PsiElement element, @Nonnull ResolveState state) {
      if (element instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)element;
        if (method.hasModifierProperty(PsiModifier.STATIC) && isAccessor(method)) {
          return super.execute(method, state);
        }
      }

      return true;
    }

    protected abstract boolean isAccessor(@Nonnull PsiMethod method);

    public String getPropertyName() {
      return myPropertyName;
    }

    @Override
    public <T> T getHint(@Nonnull Key<T> hintKey) {
      if (hintKey == NameHint.KEY) {
        return null;
      }

      return super.getHint(hintKey);
    }
  }
}
