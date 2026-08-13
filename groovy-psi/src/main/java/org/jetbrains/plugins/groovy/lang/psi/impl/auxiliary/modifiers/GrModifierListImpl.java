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
package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiModifierList;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.util.CachedValueProvider;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.StubBasedPsiElement;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ArrayFactory;
import consulo.util.collection.primitive.objects.ObjectIntMap;
import consulo.util.collection.primitive.objects.ObjectMaps;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrModifierListStub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry.Krasilschikov
 * @since 2007-03-18
 */
@SuppressWarnings({"StaticFieldReferencedViaSubclass"})
public class GrModifierListImpl extends GrStubElementBase<GrModifierListStub> implements GrModifierList, StubBasedPsiElement<GrModifierListStub> {
  public static final ObjectIntMap<String> NAME_TO_MODIFIER_FLAG_MAP = ObjectMaps.newObjectIntHashMap();
  public static final Map<String, IElementType> NAME_TO_MODIFIER_ELEMENT_TYPE = new HashMap<>();
  private static final ArrayFactory<GrAnnotation> ARRAY_FACTORY = new ArrayFactory<>() {
    @Nonnull
    @Override
    public GrAnnotation[] create(int count) {
      return new GrAnnotation[count];
    }
  };

  private static final ObjectIntMap<String> PRIORITY = ObjectMaps.newObjectIntHashMap(16);

  static {
    NAME_TO_MODIFIER_FLAG_MAP.putInt(GrModifier.PUBLIC, GrModifierFlags.PUBLIC_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.putInt(GrModifier.PROTECTED, GrModifierFlags.PROTECTED_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.putInt(GrModifier.PRIVATE, GrModifierFlags.PRIVATE_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.putInt(GrModifier.PACKAGE_LOCAL, GrModifierFlags.PACKAGE_LOCAL_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.putInt(GrModifier.STATIC, GrModifierFlags.STATIC_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.putInt(GrModifier.ABSTRACT, GrModifierFlags.ABSTRACT_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.putInt(GrModifier.FINAL, GrModifierFlags.FINAL_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.putInt(GrModifier.NATIVE, GrModifierFlags.NATIVE_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.putInt(GrModifier.SYNCHRONIZED, GrModifierFlags.SYNCHRONIZED_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.putInt(GrModifier.STRICTFP, GrModifierFlags.STRICTFP_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.putInt(GrModifier.TRANSIENT, GrModifierFlags.TRANSIENT_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.putInt(GrModifier.VOLATILE, GrModifierFlags.VOLATILE_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.putInt(GrModifier.DEF, GrModifierFlags.DEF_MASK);


    PRIORITY.putInt(GrModifier.PUBLIC, 0);
    PRIORITY.putInt(GrModifier.PROTECTED, 0);
    PRIORITY.putInt(GrModifier.PRIVATE, 0);
    PRIORITY.putInt(GrModifier.PACKAGE_LOCAL, 0);
    PRIORITY.putInt(GrModifier.STATIC, 1);
    PRIORITY.putInt(GrModifier.ABSTRACT, 1);
    PRIORITY.putInt(GrModifier.FINAL, 2);
    PRIORITY.putInt(GrModifier.NATIVE, 3);
    PRIORITY.putInt(GrModifier.SYNCHRONIZED, 3);
    PRIORITY.putInt(GrModifier.STRICTFP, 3);
    PRIORITY.putInt(GrModifier.TRANSIENT, 3);
    PRIORITY.putInt(GrModifier.VOLATILE, 3);
    PRIORITY.putInt(GrModifier.DEF, 4);

    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.PUBLIC, GroovyTokenTypes.kPUBLIC);
    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.ABSTRACT, GroovyTokenTypes.kABSTRACT);
    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.NATIVE, GroovyTokenTypes.kNATIVE);
    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.PRIVATE, GroovyTokenTypes.kPRIVATE);
    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.PROTECTED, GroovyTokenTypes.kPROTECTED);
    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.SYNCHRONIZED, GroovyTokenTypes.kSYNCHRONIZED);
    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.STRICTFP, GroovyTokenTypes.kSTRICTFP);
    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.STATIC, GroovyTokenTypes.kSTATIC);
    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.FINAL, GroovyTokenTypes.kFINAL);
    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.TRANSIENT, GroovyTokenTypes.kTRANSIENT);
    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.NATIVE, GroovyTokenTypes.kNATIVE);
    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.DEF, GroovyTokenTypes.kDEF);
    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.VOLATILE, GroovyTokenTypes.kVOLATILE);
  }

  private static final String[] VISIBILITY_MODIFIERS = {
    GrModifier.PUBLIC,
    GrModifier.PROTECTED,
    GrModifier.PRIVATE
  };

  public GrModifierListImpl(@Nonnull ASTNode node) {
    super(node);
  }

  @Override
  @RequiredReadAction
  public PsiElement getParent() {
    return getParentByStub();
  }

  public GrModifierListImpl(GrModifierListStub stub) {
    this(stub, GroovyElementTypes.MODIFIERS);
  }

  public GrModifierListImpl(GrModifierListStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitModifierList(this);
  }

  @Override
  public String toString() {
    return "Modifiers";
  }

  @Override
  @Nonnull
  @RequiredReadAction
  public PsiElement[] getModifiers() {
    List<PsiElement> result = new ArrayList<>();
    for (PsiElement cur = getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (cur instanceof GrAnnotation || TokenSets.MODIFIERS.contains(cur.getNode().getElementType())) {
        result.add(cur);
      }
    }

    return result.toArray(new PsiElement[result.size()]);
  }

  @Override
  @RequiredReadAction
  public boolean hasExplicitVisibilityModifiers() {
    GrModifierListStub stub = getStub();
    if (stub != null) {
      return (stub.getModifiersFlags() & (GrModifierFlags.PUBLIC_MASK | GrModifierFlags.PROTECTED_MASK | GrModifierFlags.PRIVATE_MASK)) != 0;
    }

    for (@GrModifier.GrModifierConstant String type : VISIBILITY_MODIFIERS) {
      if (hasExplicitModifier(type)) {
        return true;
      }
    }
    return false;
  }

  public static boolean checkModifierProperty(@Nonnull GrModifierList modifierList,
                                              @GrModifier.GrModifierConstant @Nonnull String modifier) {
    PsiElement owner = modifierList.getParent();
    if (owner instanceof GrVariableDeclaration varDecl && varDecl.getParent() instanceof GrTypeDefinitionBody typeDefBody) {
      PsiElement pParent = typeDefBody.getParent();
      if (!modifierList.hasExplicitVisibilityModifiers()) { //properties are backed by private fields
        if (!(pParent instanceof GrTypeDefinition typeDef && isInterface(typeDef))) {
          if (modifier.equals(GrModifier.PRIVATE)) {
            return true;
          }
          if (modifier.equals(GrModifier.PROTECTED)) {
            return false;
          }
          if (modifier.equals(GrModifier.PUBLIC)) {
            return false;
          }
        }
      }

      if (pParent instanceof GrTypeDefinition typeDef && isInterface(typeDef)) {
        if (modifier.equals(GrModifier.STATIC)) {
          return true;
        }
        if (modifier.equals(GrModifier.FINAL)) {
          return true;
        }
      }
      if (pParent instanceof GrTypeDefinition typeDef && modifier.equals(GrModifier.FINAL) && !modifierList.hasExplicitVisibilityModifiers()) {
        PsiModifierList pModifierList = typeDef.getModifierList();
        if (pModifierList != null && PsiImplUtil.hasImmutableAnnotation(pModifierList)) {
          return true;
        }
      }
    }

    if (owner instanceof GrMethod method
        && method.getParent() instanceof GrTypeDefinitionBody typeDefBody
        && typeDefBody.getParent() instanceof GrTypeDefinition typeDef
        && typeDef.isInterface()) {
        if (GrModifier.ABSTRACT.equals(modifier)) {
          return true;
        }
        if (!typeDef.isTrait() && GrModifier.PUBLIC.equals(modifier)) {
          return true;
        }
      }

    if (modifierList.hasExplicitModifier(modifier)) {
      return true;
    }

    if (modifier.equals(GrModifier.PUBLIC)) {
      if (owner instanceof GrPackageDefinition) {
        return false;
      }
      if (owner instanceof GrVariableDeclaration && !(owner.getParent() instanceof GrTypeDefinitionBody) || owner instanceof GrVariable) {
        return false;
      }
      //groovy type definitions and methods are public by default
      return !modifierList.hasExplicitModifier(GrModifier.PRIVATE) && !modifierList.hasExplicitModifier(GrModifier.PROTECTED);
    }

    if (owner instanceof GrTypeDefinition clazz) {
      if (modifier.equals(GrModifier.STATIC)) {
        PsiClass containingClass = clazz.getContainingClass();
        return containingClass != null && containingClass.isInterface();
      }
      if (modifier.equals(GrModifier.ABSTRACT)) {
        if (clazz.isInterface()) {
          return true;
        }
        if (clazz.isEnum() &&
          GroovyConfigUtils.getInstance().isVersionAtLeast(modifierList, GroovyConfigUtils.GROOVY2_0)) {
          for (GrMethod method : clazz.getCodeMethods()) {
            if (method.isAbstract()) {
              return true;
            }
          }
        }
      }
      if (modifier.equals(GrModifier.FINAL)) {
        if (clazz.isEnum()) {
          GrField[] fields = clazz.getFields();
          for (GrField field : fields) {
            if (field instanceof GrEnumConstant enumConst && enumConst.getInitializingClass() != null) {
              return false;
            }
          }
          return true;
        }
      }
    }

    return false;
  }

  private static boolean isInterface(GrTypeDefinition pParent) {
    return pParent.isInterface() && !pParent.isTrait();
  }

  @Override
  @RequiredReadAction
  public boolean hasModifierProperty(@Nonnull String modifier) {
    return checkModifierProperty(this, modifier);
  }

  @Override
  @RequiredReadAction
  public boolean hasExplicitModifier(@Nonnull String name) {
    GrModifierListStub stub = getStub();
    if (stub != null) {
      return hasMaskExplicitModifier(name, stub.getModifiersFlags());
    }

    IElementType type = NAME_TO_MODIFIER_ELEMENT_TYPE.get(name);
    return type != null && findChildByType(type) != null;
  }

  public static boolean hasMaskExplicitModifier(String name, int mask) {
    int flag = NAME_TO_MODIFIER_FLAG_MAP.getInt(name);
    return (mask & flag) != 0;
  }

  @Override
  @RequiredWriteAction
  public void setModifierProperty(@Nonnull String name, boolean doSet) throws IncorrectOperationException {
    if (hasModifierProperty(name) == doSet) {
      return;
    }

    if (doSet) {
      if (GrModifier.PRIVATE.equals(name) ||
        GrModifier.PROTECTED.equals(name) ||
        GrModifier.PUBLIC.equals(name) ||
        GrModifier.PACKAGE_LOCAL.equals(name)) {
        setModifierPropertyInternal(GrModifier.PUBLIC, false);
        setModifierPropertyInternal(GrModifier.PROTECTED, false);
        setModifierPropertyInternal(GrModifier.PRIVATE, false);
      }
    }
    if (GrModifier.PACKAGE_LOCAL.equals(name) /*|| GrModifier.PUBLIC.equals(name)*/) {
      if (getModifiers().length == 0) {
        setModifierProperty(GrModifier.DEF, true);
      }
    }
    else {
      setModifierPropertyInternal(name, doSet);
    }
  }

  @Nonnull
  @Override
  @RequiredReadAction
  public GrAnnotation[] getRawAnnotations() {
    return getStubOrPsiChildren(GroovyElementTypes.ANNOTATION, ARRAY_FACTORY);
  }

  @RequiredWriteAction
  private void setModifierPropertyInternal(String name, boolean doSet) {
    if (doSet) {
      if (isEmptyModifierList()) {
        PsiElement nextSibling = getNextSibling();
        if (nextSibling != null && !PsiImplUtil.isWhiteSpaceOrNls(nextSibling)) {
          getNode().getTreeParent().addLeaf(TokenType.WHITE_SPACE, " ", nextSibling.getNode());
        }
      }

      PsiElement modifier = GroovyPsiElementFactory.getInstance(getProject()).createModifierFromText(name);
      PsiElement anchor = findAnchor(name);
      addAfter(modifier, anchor);
    }
    else {
      PsiElement[] modifiers = findChildrenByType(TokenSets.MODIFIERS, PsiElement.class);
      for (PsiElement modifier : modifiers) {
        if (name.equals(modifier.getText())) {
          deleteChildRange(modifier, modifier);
          break;
        }
      }

      if (isEmptyModifierList()) {
        PsiElement nextSibling = getNextSibling();
        if (nextSibling != null && PsiImplUtil.isWhiteSpaceOrNls(nextSibling)) {
          nextSibling.delete();
        }
      }
    }
  }

  @Override
  @RequiredWriteAction
  public ASTNode addInternal(ASTNode first, ASTNode last, ASTNode anchor, Boolean before) {
    ASTNode node = super.addInternal(first, last, anchor, before);
    PsiElement sibling = getNextSibling();
    if (sibling != null && sibling.getText().contains("\n")) {
      sibling.replace(GroovyPsiElementFactory.getInstance(getProject()).createWhiteSpace());
    }
    return node;
  }

  @RequiredReadAction
  private boolean isEmptyModifierList() {
    return getTextLength() == 0 || getModifiers().length == 0 && getRawAnnotations().length == 0;
  }

  @Nullable
  @RequiredReadAction
  private PsiElement findAnchor(String name) {
    int myPriority = PRIORITY.getInt(name);
    PsiElement anchor = null;

    for (PsiElement modifier : getModifiers()) {
      int otherPriority = PRIORITY.getInt(modifier.getText());
      if (otherPriority <= myPriority) {
        anchor = modifier;
      }
      else if (otherPriority > myPriority && anchor != null) {
        break;
      }
    }
    return anchor;
  }

  @Override
  public void checkSetModifierProperty(@Nonnull String name, boolean value) throws IncorrectOperationException {
  }

  @Override
  @Nonnull
  public GrAnnotation[] getAnnotations() {
    return LanguageCachedValueUtil.getCachedValue(this, new CachedValueProvider<GrAnnotation[]>() {
      @Nullable
      @Override
      public Result<GrAnnotation[]> compute() {
        return Result.create(GrAnnotationCollector.getResolvedAnnotations(GrModifierListImpl.this),
                             PsiModificationTracker.MODIFICATION_COUNT);
      }
    });
  }

  @Override
  @Nonnull
  public PsiAnnotation[] getApplicableAnnotations() {
    //todo[medvedev]
    return getAnnotations();
  }

  @Override
  @Nullable
  public PsiAnnotation findAnnotation(@Nonnull String qualifiedName) {
    for (GrAnnotation annotation : getAnnotations()) {
      if (qualifiedName.equals(annotation.getQualifiedName())) {
        return annotation;
      }
    }
    return null;
  }

  @Override
  @Nonnull
  @RequiredWriteAction
  public GrAnnotation addAnnotation(@Nonnull String qualifiedName) {
    PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass(qualifiedName, getResolveScope());
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());
    GrAnnotation annotation;
    if (psiClass != null && psiClass.isAnnotationType()) {
      annotation = (GrAnnotation)addAfter(factory.createModifierFromText("@xxx"), null);
      annotation.getClassReference().bindToElement(psiClass);
    }
    else {
      annotation = (GrAnnotation)addAfter(factory.createModifierFromText("@" + qualifiedName), null);
    }

    PsiElement parent = getParent();
    if (!(parent instanceof GrParameter)) {
      ASTNode node = annotation.getNode();
      ASTNode treeNext = node.getTreeNext();
      if (treeNext != null) {
        getNode().addLeaf(TokenType.WHITE_SPACE, "\n", treeNext);
      }
      else {
        parent.getNode().addLeaf(TokenType.WHITE_SPACE, "\n", getNode().getTreeNext());
      }
    }

    return annotation;
  }
}
