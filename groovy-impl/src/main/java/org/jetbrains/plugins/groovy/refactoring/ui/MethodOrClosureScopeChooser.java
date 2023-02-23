/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.ui;

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import consulo.application.ui.wm.IdeFocusManager;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorColors;
import consulo.codeEditor.markup.HighlighterLayer;
import consulo.codeEditor.markup.HighlighterTargetArea;
import consulo.codeEditor.markup.MarkupModel;
import consulo.codeEditor.markup.RangeHighlighter;
import consulo.colorScheme.EditorColorsManager;
import consulo.colorScheme.TextAttributes;
import consulo.component.util.Iconable;
import consulo.document.util.TextRange;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiElement;
import consulo.logging.Logger;
import consulo.ui.ex.awt.JBList;
import consulo.ui.ex.awt.ScrollPaneFactory;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.ex.popup.JBPopup;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.event.JBPopupAdapter;
import consulo.ui.ex.popup.event.LightweightWindowEvent;
import consulo.util.lang.Pair;
import consulo.util.lang.function.PairFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.JetgroovyIcons;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParameterListOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class MethodOrClosureScopeChooser {
  private static final Logger LOG = Logger.getInstance(MethodOrClosureScopeChooser.class);

  @NonNls
  private static final String USE_SUPER_METHOD_OF = "Change base method";
  @NonNls
  private static final String CHANGE_USAGES_OF = "Change usages";

  public interface JBPopupOwner {
    JBPopup get();
  }

  /**
   * @param callback is invoked if any scope was chosen. The first arg is this scope and the second arg is a psielement to search for (super method of chosen method or
   *                 variable if the scope is a closure)
   */
  public static JBPopup create(List<? extends GrParameterListOwner> scopes,
                               final Editor editor,
                               final JBPopupOwner popupRef,
                               final PairFunction<GrParameterListOwner, PsiElement, Object> callback) {
    final JPanel panel = new JPanel(new BorderLayout());
    final JCheckBox superMethod = new JCheckBox(USE_SUPER_METHOD_OF, true);
    superMethod.setMnemonic('U');
    panel.add(superMethod, BorderLayout.SOUTH);
    final JBList list = new JBList(scopes.toArray());
    list.setVisibleRowCount(5);
    list.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        final String text;
        if (value instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod)value;
          text = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY,
                                            PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                              PsiFormatUtilBase.SHOW_NAME |
                                              PsiFormatUtilBase.SHOW_PARAMETERS,
                                            PsiFormatUtilBase.SHOW_TYPE);
          setIcon(TargetAWT.to(IconDescriptorUpdaters.getIcon(method, Iconable.ICON_FLAG_VISIBILITY)));
        }
        else {
          LOG.assertTrue(value instanceof GrClosableBlock);
          setIcon(TargetAWT.to(JetgroovyIcons.Groovy.Groovy_16x16));
          text = "{...}";
        }
        setText(text);
        return this;
      }
    });
    list.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setSelectedIndex(0);
    final List<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    final TextAttributes attributes =
      EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    list.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        final GrParameterListOwner selectedMethod = (GrParameterListOwner)list.getSelectedValue();
        if (selectedMethod == null) return;
        dropHighlighters(highlighters);
        updateView(selectedMethod, editor, attributes, highlighters, superMethod);
      }
    });
    updateView(scopes.get(0), editor, attributes, highlighters, superMethod);
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(list);
    scrollPane.setBorder(null);
    panel.add(scrollPane, BorderLayout.CENTER);

    final List<Pair<ActionListener, KeyStroke>> keyboardActions = Collections.singletonList(
      Pair.<ActionListener, KeyStroke>create(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          final GrParameterListOwner ToSearchIn = (GrParameterListOwner)list.getSelectedValue();
          final JBPopup popup = popupRef.get();
          if (popup != null && popup.isVisible()) {
            popup.cancel();
          }


          final PsiElement toSearchFor;
          if (ToSearchIn instanceof GrMethod) {
            final GrMethod method = (GrMethod)ToSearchIn;
            toSearchFor = superMethod.isEnabled() && superMethod.isSelected() ? method.findDeepestSuperMethod() : method;
          }
          else {
            toSearchFor = superMethod.isEnabled() && superMethod.isSelected() ? ToSearchIn.getParent() : null;
          }
          IdeFocusManager.findInstance().doWhenFocusSettlesDown(new Runnable() {
            public void run() {
              callback.fun(ToSearchIn, toSearchFor);
            }
          });
        }
      }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)));


    return JBPopupFactory.getInstance().createComponentPopupBuilder(panel, list)
                         .setTitle("Introduce parameter to")
                         .setMovable(false)
                         .setResizable(false)
                         .setRequestFocus(true)
                         .setKeyboardActions(keyboardActions).addListener(new JBPopupAdapter() {
        @Override
        public void onClosed(LightweightWindowEvent event) {
          dropHighlighters(highlighters);
        }
      }).createPopup();
  }


  public static void updateView(GrParameterListOwner selectedMethod,
                                Editor editor,
                                TextAttributes attributes,
                                List<RangeHighlighter> highlighters,
                                JCheckBox superMethod) {
    final MarkupModel markupModel = editor.getMarkupModel();
    final TextRange textRange = selectedMethod.getTextRange();
    final RangeHighlighter rangeHighlighter =
      markupModel.addRangeHighlighter(textRange.getStartOffset(), textRange.getEndOffset(), HighlighterLayer.SELECTION - 1, attributes,
                                      HighlighterTargetArea.EXACT_RANGE);
    highlighters.add(rangeHighlighter);
    if (selectedMethod instanceof GrMethod) {
      superMethod.setText(USE_SUPER_METHOD_OF);
      superMethod.setEnabled(((GrMethod)selectedMethod).findDeepestSuperMethod() != null);
    }
    else {
      superMethod.setText(CHANGE_USAGES_OF);
      superMethod.setEnabled(findVariableToUse(selectedMethod) != null);
    }
  }

  @Nullable
  public static GrVariable findVariableToUse(@Nonnull GrParameterListOwner owner) {
    final PsiElement parent = owner.getParent();
    if (parent instanceof GrVariable) return (GrVariable)parent;
    if (parent instanceof GrAssignmentExpression &&
      ((GrAssignmentExpression)parent).getRValue() == owner &&
      ((GrAssignmentExpression)parent).getOperationToken() == GroovyTokenTypes.mASSIGN) {
      final GrExpression lValue = ((GrAssignmentExpression)parent).getLValue();
      if (lValue instanceof GrReferenceExpression) {
        final PsiElement resolved = ((GrReferenceExpression)lValue).resolve();
        if (resolved instanceof GrVariable) {
          return (GrVariable)resolved;
        }
      }
    }
    return null;
  }

  private static void dropHighlighters(List<RangeHighlighter> highlighters) {
    for (RangeHighlighter highlighter : highlighters) {
      highlighter.dispose();
    }
    highlighters.clear();
  }
}
