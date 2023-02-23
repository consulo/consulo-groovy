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

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiType;
import consulo.content.scope.SearchScope;
import consulo.language.impl.ast.LeafElement;
import consulo.language.psi.*;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.xml.psi.xml.XmlAttributeValue;
import consulo.xml.psi.xml.XmlToken;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

/**
* @author sergey.evdokimov
*/
public class GrLightVariable extends GrImplicitVariableImpl implements NavigatablePsiElement {

  private final List<PsiElement> myDeclarations;

  private Object myCreatorKey;

  public GrLightVariable(PsiManager manager,
                         @NonNls String name,
                         @NonNls @Nonnull String type,
                         @Nonnull PsiElement navigationElement) {
    this(manager, name, type, Collections.singletonList(navigationElement), getDeclarationScope(navigationElement));
  }

  public GrLightVariable(PsiManager manager,
                         @NonNls String name,
                         @NonNls @Nonnull String type,
                         @Nonnull List<PsiElement> declarations,
                         @Nonnull PsiElement scope) {
    this(manager, name, JavaPsiFacade.getElementFactory(manager.getProject()).createTypeFromText(type, scope), declarations, scope);
  }

  public GrLightVariable(PsiManager manager,
                         @NonNls String name,
                         @Nonnull PsiType type,
                         @Nonnull PsiElement navigationElement) {
    this(manager, name, type, Collections.singletonList(navigationElement), getDeclarationScope(navigationElement));
  }

  public GrLightVariable(PsiManager manager,
                         @NonNls String name,
                         @Nonnull PsiType type,
                         @Nonnull List<PsiElement> declarations,
                         @Nonnull PsiElement scope) {
    super(manager, new GrLightIdentifier(manager, name), type, false, scope);

    myDeclarations = declarations;
    if (!myDeclarations.isEmpty()) {
      setNavigationElement(myDeclarations.get(0));
    }
  }

  private static PsiElement getDeclarationScope(PsiElement navigationElement) {
    return navigationElement.getContainingFile();
  }

  @Override
  public boolean isWritable() {
    return getNavigationElement() != this;
  }

  @Override
  public PsiFile getContainingFile() {
    if (!myDeclarations.isEmpty()) {
      return myDeclarations.get(0).getContainingFile();
    }
    else {
      return getDeclarationScope().getContainingFile();
    }
  }

  @Override
  public boolean isValid() {
    for (PsiElement declaration : myDeclarations) {
      if (!declaration.isValid()) return false;
    }

    return true;
  }

  @Nonnull
  @Override
  public SearchScope getUseScope() {
    return new LocalSearchScope(getDeclarationScope());
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return myDeclarations.contains(another) || super.isEquivalentTo(another);
  }

  @Override
  public PsiElement setName(@Nonnull String name) throws IncorrectOperationException
  {
    for (PsiElement declaration : myDeclarations) {
      if (declaration instanceof PsiNamedElement) {
        if (declaration instanceof PsiMethod) {
          name = GroovyPropertyUtils.getGetterNameNonBoolean(name);
        }
        ((PsiNamedElement)declaration).setName(name);
      }
      else if (declaration instanceof GrArgumentLabel) {
        ((GrArgumentLabel)declaration).setName(name);
      }
      else if (declaration instanceof XmlAttributeValue) {
        PsiElement leftQuote = declaration.getFirstChild();

        if (!(leftQuote instanceof XmlToken)) continue;

        PsiElement textToken = leftQuote.getNextSibling();

        if (!(textToken instanceof XmlToken)) continue;

        PsiElement rightQuote = textToken.getNextSibling();

        if (!(rightQuote instanceof XmlToken) || rightQuote.getNextSibling() != null) continue;

        ((LeafElement)textToken).replaceWithText(name);
      }
      else if (declaration instanceof GrReferenceExpression) {
        ((GrReferenceExpression)declaration).handleElementRenameSimple(name);
      }
    }

    return getNameIdentifier().replace(new GrLightIdentifier(myManager, name));
  }

  public Object getCreatorKey() {
    return myCreatorKey;
  }

  public void setCreatorKey(Object creatorKey) {
    myCreatorKey = creatorKey;
  }

  public List<PsiElement> getDeclarations() {
    return myDeclarations;
  }
}
