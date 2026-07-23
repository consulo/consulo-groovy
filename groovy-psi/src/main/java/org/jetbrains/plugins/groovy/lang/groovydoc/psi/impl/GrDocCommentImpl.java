/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.editor.util.PsiUtilBase;
import consulo.language.impl.psi.LazyParseablePsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTag;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ilyas
 */
public class GrDocCommentImpl extends LazyParseablePsiElement implements GroovyDocElementTypes, GrDocComment {
  public GrDocCommentImpl(CharSequence text) {
    super(GROOVY_DOC_COMMENT, text);
  }

  @Override
  public String toString() {
    return "GrDocComment";
  }

  @Override
  public IElementType getTokenType() {
    return getElementType();
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitDocComment(this);
  }

  @Override
  @RequiredReadAction
  public void acceptChildren(GroovyElementVisitor visitor) {
    PsiElement child = getFirstChild();
    while (child != null) {
      if (child instanceof GroovyPsiElement childElement) {
        childElement.accept(visitor);
      }

      child = child.getNextSibling();
    }
  }

  @Override
  public GrDocCommentOwner getOwner() {
    return GrDocCommentUtil.findDocOwner(this);
  }

  @Nonnull
  @Override
  @RequiredReadAction
  public GrDocTag[] getTags() {
    GrDocTag[] tags = PsiTreeUtil.getChildrenOfType(this, GrDocTag.class);
    return tags == null ? GrDocTag.EMPTY_ARRAY : tags;
  }

  @Nullable
  @Override
  @RequiredReadAction
  public GrDocTag findTagByName(String name) {
    if (!getText().contains(name)) return null;
    for (PsiElement e = getFirstChild(); e != null; e = e.getNextSibling()) {
      if (e instanceof GrDocTag docTag && docTag.getName().equals(name)) {
        return docTag;
      }
    }
    return null;
  }

  @Nonnull
  @Override
  @RequiredReadAction
  public GrDocTag[] findTagsByName(String name) {
    if (!getText().contains(name)) return GrDocTag.EMPTY_ARRAY;
    List<GrDocTag> list = new ArrayList<>();
    for (PsiElement e = getFirstChild(); e != null; e = e.getNextSibling()) {
      if (e instanceof GrDocTag docTag && name.equals(docTag.getName())) {
        list.add(docTag);
      }
    }
    return list.toArray(new GrDocTag[list.size()]);
  }

  @Nonnull
  @Override
  @RequiredReadAction
  public PsiElement[] getDescriptionElements() {
    List<PsiElement> array = new ArrayList<>();
    for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
      ASTNode node = child.getNode();
      if (node == null) continue;
      IElementType i = node.getElementType();
      if (i == GDOC_TAG) break;
      if (i != mGDOC_COMMENT_START && i != mGDOC_COMMENT_END && i != mGDOC_ASTERISKS) {
        array.add(child);
      }
    }
    return PsiUtilBase.toPsiElementArray(array);
  }
}
