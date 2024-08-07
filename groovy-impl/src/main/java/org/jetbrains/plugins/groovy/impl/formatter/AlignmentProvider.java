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
package org.jetbrains.plugins.groovy.impl.formatter;

import consulo.language.ast.ASTNode;
import consulo.language.codeStyle.Alignment;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;

/**
 * @author Max Medvedev
 */
public class AlignmentProvider {
  private final Map<PsiElement, Set<PsiElement>> myTree = new HashMap<PsiElement, Set<PsiElement>>();
  private final Map<Set<PsiElement>, Alignment> myAlignments = new HashMap<Set<PsiElement>, Alignment>();
  private final Map<Set<PsiElement>, Boolean> myAllowBackwardShift = new HashMap<Set<PsiElement>, Boolean>();
  private final Map<Set<PsiElement>, Alignment.Anchor> myAnchor = new HashMap<Set<PsiElement>, Alignment.Anchor>();

  public void addPair(@Nonnull PsiElement e1, @Nonnull PsiElement e2, @Nullable Boolean allowBackwardShift) {
    addPair(e1, e2, allowBackwardShift, null);
  }

  public void addPair(@Nonnull PsiElement e1, @Nonnull PsiElement e2, @Nullable Boolean allowBackwardShift, @Nullable Alignment.Anchor anchor) {
    assert e1 != e2;

    final Set<PsiElement> set1 = myTree.get(e1);
    final Set<PsiElement> set2 = myTree.get(e2);

    if (set1 != null && set2 != null) {
      assert (!myAlignments.containsKey(set1) || !myAlignments.containsKey(set2));
      assert(myAllowBackwardShift.get(set1).booleanValue() == myAllowBackwardShift.get(set2).booleanValue());
      assert(myAnchor.get(set1) == myAnchor.get(set2));
      if (allowBackwardShift != null) {
        assert(myAllowBackwardShift.get(set1).booleanValue() == allowBackwardShift.booleanValue());
      }
      if (anchor != null) {
        assert(myAnchor.get(set1) == anchor);
      }

      if (myAlignments.containsKey(set2)) {
        for (Iterator<PsiElement> iterator = set1.iterator(); iterator.hasNext(); ) {
          PsiElement element = iterator.next();
          iterator.remove();

          addInternal(set2, element);
        }
      }
      else {
        set1.addAll(set2);
        for (Iterator<PsiElement> iterator = set2.iterator(); iterator.hasNext(); ) {
          PsiElement element = iterator.next();
          iterator.remove();

          addInternal(set1, element);
        }
      }
    }
    else if (set1 != null) {
      if (allowBackwardShift != null) {
        assert myAllowBackwardShift.get(set1).booleanValue() == allowBackwardShift.booleanValue();
      }
      if (anchor != null) {
        assert myAnchor.get(set1) == anchor;
      }
      addInternal(set1, e2);
    }
    else if (set2 != null) {
      if (allowBackwardShift != null) {
        assert(myAllowBackwardShift.get(set2).booleanValue() == allowBackwardShift.booleanValue());
      }
      if (anchor != null) {
        assert(myAnchor.get(set2) == anchor);
      }
      addInternal(set2, e1);
    }
    else {
      final HashSet<PsiElement> set = createHashSet();
      addInternal(set, e1);
      addInternal(set, e2);
      myAllowBackwardShift.put(set, allowBackwardShift);
      myAnchor.put(set, anchor);
    }
  }

  private void addInternal(@Nonnull Set<PsiElement> set, @Nonnull PsiElement element) {
    myTree.put(element, set);
    set.add(element);
  }

  @Nonnull
  private static HashSet<PsiElement> createHashSet() {
    return new HashSet<PsiElement>() {
      private final int myhash = new Object().hashCode();

      @Override
      public int hashCode() {
        return myhash;
      }
    };
  }

  public void addPair(@Nonnull ASTNode node1, @Nonnull ASTNode node2, boolean allowBackwardShift) {
    addPair(node1.getPsi(), node2.getPsi(), allowBackwardShift);
  }

  private void add(@Nonnull PsiElement element, boolean allowBackwardShift) {
    add(element, allowBackwardShift, Alignment.Anchor.LEFT);
  }

  private void add(@Nonnull PsiElement element, boolean allowBackwardShift, @Nonnull Alignment.Anchor anchor) {
    if (myTree.get(element) != null) return;

    final HashSet<PsiElement> set = createHashSet();
    set.add(element);
    myTree.put(element, set);
    myAllowBackwardShift.put(set, allowBackwardShift);
    myAnchor.put(set, anchor);
  }

  @Nullable
  public Alignment getAlignment(@Nonnull PsiElement e) {
    final Set<PsiElement> set = myTree.get(e);
    if (set == null) {
      return null;
    }

    Alignment alignment = myAlignments.get(set);
    if (alignment != null) return alignment;

    Alignment.Anchor anchor = myAnchor.get(set);
    if (anchor == null) {
      myAnchor.put(set, Alignment.Anchor.LEFT);
      anchor = Alignment.Anchor.LEFT;
    }
    alignment = Alignment.createAlignment(myAllowBackwardShift.get(set), anchor);
    myAlignments.put(set, alignment);
    return alignment;
  }

  @Nonnull
  public Aligner createAligner(boolean allowBackwardShift) {
    return new Aligner(allowBackwardShift, Alignment.Anchor.LEFT);
  }

  @Nonnull
  public Aligner createAligner(PsiElement element, boolean allowBackwardShift, Alignment.Anchor anchor) {
    final Aligner aligner = new Aligner(allowBackwardShift, anchor);
    aligner.append(element);
    return aligner;
  }

  /**
   * This class helps to assign one alignment to some elements.
   * You can create an instance of Aligner and apply 'append' to any element, you want to be aligned.
   *
   * @author Max Medvedev
   */
  public class Aligner {
    private PsiElement myRef = null;
    private boolean allowBackwardShift = true;
    @Nonnull
    private final Alignment.Anchor myAnchor;

    private Aligner(boolean allowBackwardShift, @Nonnull Alignment.Anchor anchor) {
      this.allowBackwardShift = allowBackwardShift;
      myAnchor = anchor;
    }

    public void append(@Nullable PsiElement element) {
      if (element == null) return;

      if (myRef == null) {
        myRef = element;
        add(element, allowBackwardShift, myAnchor);
      }
      else {
        addPair(myRef, element, allowBackwardShift, myAnchor);
      }
    }
  }
}
