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
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.light.LightClassReference;
import com.intellij.java.language.psi.*;
import consulo.language.Language;
import consulo.language.impl.psi.LightElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.meta.PsiMetaData;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.AnnotationArgConverter;

import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrLightAnnotation extends LightElement implements GrAnnotation {
  private final GrLightAnnotationArgumentList myAnnotationArgList;

  private final String myQualifiedName;
  private final PsiAnnotationOwner myOwner;
  private final GrLightClassReferenceElement myRef;

  public GrLightAnnotation(@Nonnull PsiManager manager,
                           @Nonnull Language language,
                           @Nonnull String qualifiedName,
                           @Nonnull PsiAnnotationOwner owner) {
    super(manager, language);
    myQualifiedName = qualifiedName;
    myOwner = owner;

    myAnnotationArgList = new GrLightAnnotationArgumentList(manager, language);
    myRef = new GrLightClassReferenceElement(qualifiedName, qualifiedName, this);
  }

  @Nonnull
  @Override
  public GrCodeReferenceElement getClassReference() {
    return myRef;
  }

  @Nonnull
  @Override
  public String getShortName() {
    return StringUtil.getShortName(myQualifiedName);
  }

  @Nonnull
  @Override
  public GrAnnotationArgumentList getParameterList() {
    return myAnnotationArgList;
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitAnnotation(this);
  }

  @Override
  public void acceptChildren(GroovyElementVisitor visitor) {
    //todo
  }

  @Override
  public String toString() {
    return "light groovy annotation";
  }

  @Override
  public String getText() {
    return "@" + myQualifiedName + myAnnotationArgList.getText();
  }

  @Override
  public String getQualifiedName() {
    return myQualifiedName;
  }

  @Override
  public PsiJavaCodeReferenceElement getNameReferenceElement() {
    final GroovyResolveResult resolveResult = myRef.advancedResolve();
    final PsiElement resolved = resolveResult.getElement();

    if (resolved instanceof PsiClass) {
      return new LightClassReference(getManager(), getClassReference().getText(), (PsiClass)resolved, resolveResult.getSubstitutor());
    }
    else {
      return null;
    }
  }

  @Override
  public PsiAnnotationMemberValue findAttributeValue(@NonNls String attributeName) {
    return PsiImplUtil.findAttributeValue(this, attributeName);
  }

  @Override
  public PsiAnnotationMemberValue findDeclaredAttributeValue(@NonNls String attributeName) {
    return PsiImplUtil.findDeclaredAttributeValue(this, attributeName);
  }

  @Override
  public <T extends PsiAnnotationMemberValue> T setDeclaredAttributeValue(@NonNls String attributeName, @Nullable T value) {
    throw new UnsupportedOperationException("light annotation does not support changes");
  }

  @Override
  public PsiAnnotationOwner getOwner() {
    return myOwner;
  }

  @Override
  public PsiMetaData getMetaData() {
    return null;
  }

  public void addAttribute(PsiNameValuePair pair) {
    if (pair instanceof GrAnnotationNameValuePair) {
      myAnnotationArgList.addAttribute((GrAnnotationNameValuePair)pair);
    }
    else {
      GrAnnotationMemberValue newValue = new AnnotationArgConverter().convert(pair.getValue());
      if (newValue == null) return;

      String name = pair.getName();
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(pair.getProject());
      String annotationText;
      annotationText = name != null ? "@A(" + name + "=" + newValue.getText() + ")"
                                    : "@A(" + newValue.getText() + ")";
      GrAnnotation annotation = factory.createAnnotationFromText(annotationText);
      myAnnotationArgList.addAttribute(annotation.getParameterList().getAttributes()[0]);
    }
  }


  private class GrLightAnnotationArgumentList extends LightElement implements GrAnnotationArgumentList {
    private List<GrAnnotationNameValuePair> myAttributes = null;
    private GrAnnotationNameValuePair[] myCachedAttributes = GrAnnotationNameValuePair.EMPTY_ARRAY;


    @Override
    public PsiElement getContext() {
      return GrLightAnnotation.this;
    }

    private GrLightAnnotationArgumentList(@Nonnull PsiManager manager, @Nonnull Language language) {
      super(manager, language);
    }

    @Nonnull
    @Override
    public GrAnnotationNameValuePair[] getAttributes() {
      if (myCachedAttributes == null) {
        assert myAttributes != null;
        myCachedAttributes = myAttributes.toArray(new GrAnnotationNameValuePair[myAttributes.size()]);
      }
      return myCachedAttributes;
    }

    public void addAttribute(@Nonnull GrAnnotationNameValuePair attribute) {
      if (myAttributes == null) myAttributes = new ArrayList<>();
      myAttributes.add(attribute);
      myCachedAttributes = null;
    }

    @Override
    public void accept(GroovyElementVisitor visitor) {
      visitor.visitAnnotationArgumentList(this);
    }

    @Override
    public void acceptChildren(GroovyElementVisitor visitor) {
      if (myAttributes != null) {
        for (GrAnnotationNameValuePair attribute : myAttributes) {
          attribute.accept(visitor);
        }
      }
    }

    @Override
    public String toString() {
      return "light annotation argument list";
    }

    @Override
    public String getText() {
      if (myAttributes == null || myAttributes.isEmpty()) return "";

      StringBuilder buffer = new StringBuilder();
      buffer.append('(');

      for (GrAnnotationNameValuePair attribute : myAttributes) {
        buffer.append(attribute.getText());
        buffer.append(',');
      }
      if (!myAttributes.isEmpty()) buffer.deleteCharAt(buffer.length() - 1);
      buffer.append(')');
      return buffer.toString();
    }
  }
}
