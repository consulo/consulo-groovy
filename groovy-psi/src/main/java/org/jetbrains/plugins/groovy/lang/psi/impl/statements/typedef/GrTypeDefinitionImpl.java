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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.java.language.impl.psi.impl.*;
import com.intellij.java.language.psi.*;
import consulo.application.util.CachedValueProvider;
import consulo.language.ast.ASTNode;
import consulo.language.impl.psi.CheckUtil;
import consulo.language.impl.psi.LeafPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.StubBasedPsiElement;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.navigation.ItemPresentation;
import consulo.navigation.ItemPresentationProvider;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl.GrDocCommentUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyCodeStyleSettingsFacade;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author ilyas
 */
public abstract class GrTypeDefinitionImpl extends GrStubElementBase<GrTypeDefinitionStub> implements GrTypeDefinition, StubBasedPsiElement<GrTypeDefinitionStub> {

  private final GrTypeDefinitionMembersCache myCache = new GrTypeDefinitionMembersCache(this);

  public GrTypeDefinitionImpl(@Nonnull ASTNode node) {
    super(node);
  }

  protected GrTypeDefinitionImpl(GrTypeDefinitionStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  @Override
  public PsiElement getParent() {
    return getDefinitionParent();
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitTypeDefinition(this);
  }

  @Override
  public int getTextOffset() {
    return getNameIdentifierGroovy().getTextRange().getStartOffset();
  }

  @Nullable
  @Override
  public String getQualifiedName() {
    final GrTypeDefinitionStub stub = getStub();
    if (stub != null) {
      return stub.getQualifiedName();
    }

    PsiElement parent = getParent();
    if (parent instanceof GroovyFile) {
      String packageName = ((GroovyFile)parent).getPackageName();
      return !packageName.isEmpty() ? packageName + "." + getName() : getName();
    }

    final PsiClass containingClass = getContainingClass();
    if (containingClass != null && containingClass.getQualifiedName() != null) {
      return containingClass.getQualifiedName() + "." + getName();
    }

    return null;
  }

  @Nullable
  @Override
  public GrTypeDefinitionBody getBody() {
    return getStubOrPsiChild(GroovyElementTypes.CLASS_BODY);
  }

  @Nonnull
  @Override
  public GrMembersDeclaration[] getMemberDeclarations() {
    GrTypeDefinitionBody body = getBody();
    if (body == null) return GrMembersDeclaration.EMPTY_ARRAY;
    return body.getMemberDeclarations();
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProvider.getItemPresentation(this);
  }

  @Nullable
  @Override
  public GrExtendsClause getExtendsClause() {
    return getStubOrPsiChild(GroovyElementTypes.EXTENDS_CLAUSE);
  }

  @Nullable
  @Override
  public GrImplementsClause getImplementsClause() {
    return getStubOrPsiChild(GroovyElementTypes.IMPLEMENTS_CLAUSE);
  }

  @Override
  public String[] getSuperClassNames() {
    final GrTypeDefinitionStub stub = getStub();
    if (stub != null) {
      return stub.getSuperClassNames();
    }
    return ArrayUtil.mergeArrays(getExtendsNames(), getImplementsNames());
  }

  protected String[] getImplementsNames() {
    GrImplementsClause implementsClause = getImplementsClause();
    GrCodeReferenceElement[] implementsRefs =
      implementsClause != null ? implementsClause.getReferenceElementsGroovy() : GrCodeReferenceElement.EMPTY_ARRAY;
    ArrayList<String> implementsNames = new ArrayList<String>(implementsRefs.length);
    for (GrCodeReferenceElement ref : implementsRefs) {
      String name = ref.getReferenceName();
      if (name != null) implementsNames.add(name);
    }

    return ArrayUtil.toStringArray(implementsNames);
  }

  protected String[] getExtendsNames() {
    GrExtendsClause extendsClause = getExtendsClause();
    GrCodeReferenceElement[] extendsRefs =
      extendsClause != null ? extendsClause.getReferenceElementsGroovy() : GrCodeReferenceElement.EMPTY_ARRAY;
    ArrayList<String> extendsNames = new ArrayList<String>(extendsRefs.length);
    for (GrCodeReferenceElement ref : extendsRefs) {
      String name = ref.getReferenceName();
      if (name != null) extendsNames.add(name);
    }
    return ArrayUtil.toStringArray(extendsNames);
  }

  @Override
  @Nonnull
  public PsiElement getNameIdentifierGroovy() {
    PsiElement result = findChildByType(TokenSets.PROPERTY_NAMES);
    assert result != null;
    return result;
  }

  @Override
  public void checkDelete() throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
  }

  @Override
  public void delete() throws IncorrectOperationException {
    PsiElement parent = getParent();
    if (parent instanceof GroovyFileImpl) {
      GroovyFileImpl file = (GroovyFileImpl)parent;
      if (file.getTypeDefinitions().length == 1 && !file.isScript()) {
        file.delete();
        return;
      }
    }

    super.delete();
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor,
                                     @Nonnull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @Nonnull PsiElement place) {
    return GrClassImplUtil.processDeclarations(this, processor, state, lastParent, place);
  }

  @Override
  public String getName() {
    final GrTypeDefinitionStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    return org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil.getName(this);
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return GrClassImplUtil.isClassEquivalentTo(this, another);
  }

  @Override
  public boolean isInterface() {
    return false;
  }

  @Override
  public boolean isAnnotationType() {
    return false;
  }

  @Override
  public boolean isEnum() {
    return false;
  }

  @Override
  public boolean isTrait() {
    return false;
  }

  @Nullable
  @Override
  public PsiReferenceList getExtendsList() {
    //return PsiImplUtil.getOrCreatePsiReferenceList(getExtendsClause(), PsiReferenceList.Role.EXTENDS_LIST);
    return getExtendsClause();
  }

  @Nullable
  @Override
  public PsiReferenceList getImplementsList() {
    //return PsiImplUtil.getOrCreatePsiReferenceList(getImplementsClause(), PsiReferenceList.Role.IMPLEMENTS_LIST);
    return getImplementsClause();
  }

  @Nonnull
  @Override
  public PsiClassType[] getExtendsListTypes() {
    return LanguageCachedValueUtil.getCachedValue(this,
                                                  () -> CachedValueProvider.Result.create(GrClassImplUtil.getExtendsListTypes(
                                                    GrTypeDefinitionImpl.this), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT));
  }

  @Nonnull
  @Override
  public PsiClassType[] getImplementsListTypes() {
    return LanguageCachedValueUtil.getCachedValue(this, new CachedValueProvider<PsiClassType[]>() {
      @Override
      public Result<PsiClassType[]> compute() {
        return Result.create(GrClassImplUtil.getImplementsListTypes(GrTypeDefinitionImpl.this),
                             PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
      }
    });
  }

  @Nullable
  @Override
  public PsiClass getSuperClass() {
    return GrClassImplUtil.getSuperClass(this);
  }

  @Override
  public PsiClass[] getInterfaces() {
    return LanguageCachedValueUtil.getCachedValue(this, new CachedValueProvider<PsiClass[]>() {
      @Override
      public Result<PsiClass[]> compute() {
        return Result
          .create(GrClassImplUtil.getInterfaces(GrTypeDefinitionImpl.this), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
      }
    });
  }

  @Nonnull
  @Override
  public final PsiClass[] getSupers() {
    return GrClassImplUtil.getSupers(this);
  }

  @Nonnull
  @Override
  public PsiClassType[] getSuperTypes() {
    return GrClassImplUtil.getSuperTypes(this);
  }

  @Nonnull
  @Override
  public GrField[] getCodeFields() {
    GrTypeDefinitionBody body = getBody();
    if (body != null) {
      return body.getFields();
    }

    return GrField.EMPTY_ARRAY;
  }

  @Override
  public PsiField findCodeFieldByName(String name, boolean checkBases) {
    return GrClassImplUtil.findFieldByName(this, name, checkBases, false);
  }

  @Nonnull
  @Override
  public GrField[] getFields() {
    return myCache.getFields();
  }

  @Nonnull
  @Override
  public PsiMethod[] getMethods() {
    return myCache.getMethods();
  }

  @Nonnull
  @Override
  public GrMethod[] getCodeMethods() {
    return myCache.getCodeMethods();
  }

  @Override
  public void subtreeChanged() {
    myCache.dropCaches();
    super.subtreeChanged();
  }

  @Nonnull
  @Override
  public PsiMethod[] getConstructors() {
    return myCache.getConstructors();
  }

  @Nonnull
  @Override
  public GrMethod[] getCodeConstructors() {
    return myCache.getCodeConstructors();
  }

  @Nonnull
  @Override
  public PsiClass[] getInnerClasses() {
    return myCache.getInnerClasses();
  }

  @Nonnull
  @Override
  public GrClassInitializer[] getInitializers() {
    GrTypeDefinitionBody body = getBody();
    return body != null ? body.getInitializers() : GrClassInitializer.EMPTY_ARRAY;
  }

  @Nonnull
  @Override
  public PsiField[] getAllFields() {
    return GrClassImplUtil.getAllFields(this);
  }

  @Nonnull
  @Override
  public PsiMethod[] getAllMethods() {
    return GrClassImplUtil.getAllMethods(this);
  }

  @Nonnull
  @Override
  public PsiClass[] getAllInnerClasses() {
    return PsiClassImplUtil.getAllInnerClasses(this);
  }

  @Nullable
  @Override
  public PsiField findFieldByName(String name, boolean checkBases) {
    return GrClassImplUtil.findFieldByName(this, name, checkBases, true);
  }

  @Nullable
  @Override
  public PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases) {
    return GrClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
  }

  @Nonnull
  @Override
  public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return GrClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases);
  }

  @Nonnull
  @Override
  public PsiMethod[] findCodeMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return GrClassImplUtil.findCodeMethodsBySignature(this, patternMethod, checkBases);
  }

  @Nonnull
  @Override
  public PsiMethod[] findMethodsByName(@NonNls String name, boolean checkBases) {
    return GrClassImplUtil.findMethodsByName(this, name, checkBases);
  }

  @Nonnull
  @Override
  public PsiMethod[] findCodeMethodsByName(@NonNls String name, boolean checkBases) {
    return GrClassImplUtil.findCodeMethodsByName(this, name, checkBases);
  }

  @Nonnull
  @Override
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(String name, boolean checkBases) {
    return GrClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

  @Nonnull
  @Override
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return GrClassImplUtil.getAllMethodsAndTheirSubstitutors(this);
  }

  @Nullable
  @Override
  public PsiClass findInnerClassByName(String name, boolean checkBases) {
    return GrClassImplUtil.findInnerClassByName(this, name, checkBases);
  }

  @Nullable
  @Override
  public PsiElement getLBrace() {
    final GrTypeDefinitionBody body = getBody();
    return body == null ? null : body.getLBrace();
  }

  @Nullable
  @Override
  public PsiElement getRBrace() {
    final GrTypeDefinitionBody body = getBody();
    return body == null ? null : body.getRBrace();
  }

  @Override
  public boolean isAnonymous() {
    return false;
  }

  @Nullable
  @Override
  public PsiIdentifier getNameIdentifier() {
    return PsiUtil.getJavaNameIdentifier(this);
  }

  @Nullable
  @Override
  public PsiElement getScope() {
    final GrTypeDefinitionStub stub = getStub();
    if (stub != null) {
      return stub.getParentStub().getPsi();
    }

    ASTNode treeElement = getNode();
    ASTNode parent = treeElement.getTreeParent();

    while (parent != null) {
      if (parent.getElementType() instanceof IStubElementType && !(parent.getElementType() == GroovyElementTypes.CLASS_BODY)) {
        return parent.getPsi();
      }
      parent = parent.getTreeParent();
    }

    return getContainingFile();
  }

  @Override
  public boolean isInheritor(@Nonnull PsiClass baseClass, boolean checkDeep) {
    if (isTrait() && baseClass.isInterface() && !checkDeep) {
      for (PsiClassType superType : getImplementsListTypes()) {
        if (getManager().areElementsEquivalent(superType.resolve(), baseClass)) {
          return true;
        }
      }
    }
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  @Override
  public boolean isInheritorDeep(PsiClass baseClass, @Nullable PsiClass classToByPass) {
    return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass);
  }

  @Nullable
  @Override
  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    if (parent instanceof GrTypeDefinitionBody) {
      final PsiElement pparent = parent.getParent();
      if (pparent instanceof PsiClass) {
        return (PsiClass)pparent;
      }
    }

    return null;
  }

  @Nonnull
  @Override
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return PsiSuperMethodImplUtil.getVisibleSignatures(this);
  }

  @Override
  public PsiElement setName(@NonNls @Nonnull String name) throws IncorrectOperationException {
    boolean renameFile = isRenameFileOnClassRenaming();

    final String oldName = getName();
    org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil.setName(name, getNameIdentifierGroovy());

    final GrTypeDefinitionBody body = getBody();
    if (body != null) {
      for (PsiMethod method : body.getMethods()) {
        if (method.isConstructor() && method.getName().equals(oldName)) method.setName(name);
      }
    }

    if (renameFile) {
      final PsiFile file = getContainingFile();
      final VirtualFile virtualFile = file.getVirtualFile();
      final String ext;
      if (virtualFile != null) {
        ext = virtualFile.getExtension();
      }
      else {
        ext = GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension();
      }
      file.setName(name + "." + ext);
    }

    return this;
  }

  @Nullable
  @Override
  public GrModifierList getModifierList() {
    return getStubOrPsiChild(GroovyElementTypes.MODIFIERS);
  }

  @Override
  public boolean hasModifierProperty(@NonNls @Nonnull String name) {
    PsiModifierList modifierList = getModifierList();
    return modifierList != null && modifierList.hasModifierProperty(name);
  }

  @Nullable
  @Override
  public GrDocComment getDocComment() {
    return GrDocCommentUtil.findDocComment(this);
  }

  @Override
  public boolean isDeprecated() {
    final GrTypeDefinitionStub stub = getStub();
    if (stub != null) {
      return stub.isDeprecatedByDoc() || PsiImplUtil.isDeprecatedByAnnotation(this);
    }
    return PsiImplUtil.isDeprecatedByDocTag(this) || PsiImplUtil.isDeprecatedByAnnotation(this);
  }

  @Override
  public boolean hasTypeParameters() {
    return getTypeParameters().length > 0;
  }

  @Nullable
  @Override
  public GrTypeParameterList getTypeParameterList() {
    return getStubOrPsiChild(GroovyElementTypes.TYPE_PARAMETER_LIST);
  }

  @Nonnull
  @Override
  public GrTypeParameter[] getTypeParameters() {
    final GrTypeParameterList list = getTypeParameterList();
    if (list != null) {
      return list.getTypeParameters();
    }

    return GrTypeParameter.EMPTY_ARRAY;
  }

  private boolean isRenameFileOnClassRenaming() {
    final PsiFile file = getContainingFile();
    if (!(file instanceof GroovyFile)) return false;
    final GroovyFile groovyFile = (GroovyFile)file;
    if (groovyFile.isScript()) return false;
    final String name = getName();
    final VirtualFile vFile = groovyFile.getVirtualFile();
    return vFile != null && name != null && name.equals(vFile.getNameWithoutExtension());
  }

  @Nullable
  @Override
  public PsiElement getOriginalElement() {
    return JavaPsiImplementationHelper.getInstance(getProject()).getOriginalClass(this);
  }

  @Override
  public PsiElement addAfter(@Nonnull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    if (anchor == null) {
      return add(element);
    }
    final GrTypeDefinitionBody body = getBody();
    if (anchor.getParent() == body) {

      final PsiElement nextChild = anchor.getNextSibling();
      if (nextChild == null) {
        return add(element);
      }

      if (body == null) throw new IncorrectOperationException("Class must have body");
      return body.addBefore(element, nextChild);
    }
    else {
      return super.addAfter(element, anchor);
    }
  }

  @Override
  public PsiElement addBefore(@Nonnull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    if (anchor == null) {
      return add(element);
    }

    final GrTypeDefinitionBody body = getBody();
    if (anchor.getParent() != body) {
      return super.addBefore(element, anchor);
    }

    if (body == null) throw new IncorrectOperationException("Class must have body");
    return body.addBefore(element, anchor);
  }

  @Override
  public PsiElement add(@Nonnull PsiElement psiElement) throws IncorrectOperationException {
    final GrTypeDefinitionBody body = getBody();

    if (body == null) throw new IncorrectOperationException("Class must have body");

    final PsiElement lBrace = body.getLBrace();

    if (lBrace == null) throw new IncorrectOperationException("No left brace");

    PsiMember member = getAnyMember(psiElement);
    PsiElement anchor = member != null ? getDefaultAnchor(body, member) : null;
    if (anchor == null) {
      anchor = lBrace.getNextSibling();
    }

    if (anchor != null) {
      ASTNode node = anchor.getNode();
      assert node != null;
      if (GroovyTokenTypes.mSEMI.equals(node.getElementType())) {
        anchor = anchor.getNextSibling();
      }
      if (psiElement instanceof GrField) {
        //add field with modifiers which are in its parent
        int i = ArrayUtil.find(((GrVariableDeclaration)psiElement.getParent()).getVariables(), psiElement);
        psiElement = body.addBefore(psiElement.getParent(), anchor);
        GrVariable[] vars = ((GrVariableDeclaration)psiElement).getVariables();
        for (int j = 0; j < vars.length; j++) {
          if (i != j) vars[i].delete();
        }
        psiElement = vars[i];
      }
      else {
        psiElement = body.addBefore(psiElement, anchor);
      }
    }
    else {
      psiElement = body.add(psiElement);
    }

    return psiElement;
  }

  @Nullable
  private static PsiMember getAnyMember(@Nullable PsiElement psiElement) {
    if (psiElement instanceof PsiMember) {
      return (PsiMember)psiElement;
    }
    if (psiElement instanceof GrVariableDeclaration) {
      final GrMember[] members = ((GrVariableDeclaration)psiElement).getMembers();
      if (members.length > 0) {
        return members[0];
      }
    }
    return null;
  }

  // TODO remove as soon as an arrangement sub-system is provided for groovy.
  public static int getMemberOrderWeight(PsiElement member, GroovyCodeStyleSettingsFacade settings) {
    if (member instanceof PsiField) {
      if (member instanceof PsiEnumConstant) {
        return 1;
      }
      return ((PsiField)member).hasModifierProperty(PsiModifier.STATIC) ? settings.staticFieldsOrderWeight() + 1
        : settings.fieldsOrderWeight() + 1;
    }
    if (member instanceof PsiMethod) {
      if (((PsiMethod)member).isConstructor()) {
        return settings.constructorsOrderWeight() + 1;
      }
      return ((PsiMethod)member).hasModifierProperty(PsiModifier.STATIC) ? settings.staticMethodsOrderWeight() + 1
        : settings.methodsOrderWeight() + 1;
    }
    if (member instanceof PsiClass) {
      return ((PsiClass)member).hasModifierProperty(PsiModifier.STATIC) ? settings.staticInnerClassesOrderWeight() + 1
        : settings.innerClassesOrderWeight() + 1;
    }
    return -1;
  }

  @Nullable
  private PsiElement getDefaultAnchor(GrTypeDefinitionBody body, PsiMember member) {
    GroovyCodeStyleSettingsFacade settings = GroovyCodeStyleSettingsFacade.getInstance(getProject());

    int order = getMemberOrderWeight(member, settings);
    if (order < 0) return null;

    PsiElement lastMember = null;
    for (PsiElement child = body.getFirstChild(); child != null; child = child.getNextSibling()) {
      int order1 = getMemberOrderWeight(getAnyMember(child), settings);
      if (order1 < 0) continue;
      if (order1 > order) {
        final PsiElement lBrace = body.getLBrace();
        if (lastMember != null) {
          PsiElement nextSibling = lastMember.getNextSibling();
          while (nextSibling instanceof LeafPsiElement && (nextSibling.getText().equals(",") || nextSibling.getText().equals(";"))) {
            nextSibling = nextSibling.getNextSibling();
          }
          return nextSibling == null && lBrace != null ? PsiUtil.skipWhitespacesAndComments(lBrace.getNextSibling(), true) : nextSibling;
        }
        else if (lBrace != null) {
          return PsiUtil.skipWhitespacesAndComments(lBrace.getNextSibling(), true);
        }
      }
      lastMember = child;
    }
    return body.getRBrace();
  }
}
