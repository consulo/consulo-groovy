/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.refactoring.introduce.variable;

import com.intellij.java.impl.refactoring.util.CanonicalTypes;
import com.intellij.java.language.psi.PsiType;
import consulo.application.Result;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.refactoring.introduce.inplace.OccurrencesChooser;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.ui.ex.action.Shortcut;
import consulo.ui.ex.awt.NonFocusableCheckBox;
import consulo.ui.ex.keymap.Keymap;
import consulo.ui.ex.keymap.KeymapManager;
import consulo.ui.ex.keymap.util.KeymapUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SupertypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyNameSuggestionUtil;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.GrAbstractInplaceIntroducer;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.GrFinalListener;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;
import org.jetbrains.plugins.groovy.impl.settings.GroovyApplicationSettings;
import org.jetbrains.plugins.groovy.impl.template.expressions.ChooseTypeExpression;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Created by Max Medvedev on 10/29/13
 */
public abstract class GrInplaceVariableIntroducer extends GrAbstractInplaceIntroducer<GroovyIntroduceVariableSettings> {
  private JCheckBox myCanBeFinalCb;

  public GrInplaceVariableIntroducer(String title,
                                     OccurrencesChooser.ReplaceChoice replaceChoice,
                                     GrIntroduceContext context) {
    super(title, replaceChoice, context);
    setAdvertisementText(getAdvertisementText());
  }

  @Nullable
  private static String getAdvertisementText() {
    final Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
    final Shortcut[] shortcuts = keymap.getShortcuts("PreviousTemplateVariable");
    if (shortcuts.length > 0) {
      return "Press " + KeymapUtil.getShortcutText(shortcuts[0]) + " to change type";
    }
    return null;
  }

  @Override
  protected String getActionName() {
    return GrIntroduceVariableHandler.REFACTORING_NAME;
  }

  @Override
  protected String[] suggestNames(boolean replaceAll, @Nullable GrVariable variable) {
    return GroovyNameSuggestionUtil.suggestVariableNames(getContext().getExpression(),
                                                         new GroovyVariableValidator(getContext()));
  }

  @Override
  protected JComponent getComponent() {
    myCanBeFinalCb = new NonFocusableCheckBox("Declare final");
    myCanBeFinalCb.setSelected(false);
    myCanBeFinalCb.setMnemonic('f');
    final GrFinalListener finalListener = new GrFinalListener(myEditor);
    myCanBeFinalCb.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        new WriteCommandAction(myProject, getCommandName(), getCommandName()) {
          @Override
          protected void run(@Nonnull Result result) throws Throwable {
            PsiDocumentManager.getInstance(myProject).commitDocument(myEditor.getDocument());
            final GrVariable variable = getVariable();
            if (variable != null) {
              finalListener.perform(myCanBeFinalCb.isSelected(), variable);
            }
          }
        }.execute();
      }
    });
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(null);

    if (myCanBeFinalCb != null) {
      panel.add(myCanBeFinalCb, new GridBagConstraints(0, 1, 1, 1, 1, 0, GridBagConstraints.NORTHWEST,
                                                       GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 5), 0, 0));
    }

    panel.add(Box.createVerticalBox(), new GridBagConstraints(0, 2, 1, 1, 1, 1, GridBagConstraints.NORTHWEST,
                                                              GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));

    return panel;
  }

  @Nullable
  @Override
  protected GroovyIntroduceVariableSettings getInitialSettingsForInplace(@Nonnull final GrIntroduceContext context,
                                                                         @Nonnull final OccurrencesChooser.ReplaceChoice choice,
                                                                         final String[] names) {
    return new GroovyIntroduceVariableSettings() {
      private final CanonicalTypes.Type myType;

      {
        GrExpression expression = context.getExpression();
        StringPartInfo stringPart = context.getStringPart();
        GrVariable var = context.getVar();
        PsiType type = expression != null ? expression.getType() : var != null ? var.getType() : stringPart !=
          null ? stringPart.getLiteral().getType() : null;
        myType = type != null && !PsiType.NULL.equals(type) ? CanonicalTypes.createTypeWrapper(type) : null;
      }


      @Override
      public boolean isDeclareFinal() {
        return myCanBeFinalCb != null ? myCanBeFinalCb.isSelected() : false;
      }

      @Nullable
      @Override
      public String getName() {
        return names[0];
      }

      @Override
      public boolean replaceAllOccurrences() {
        return choice == OccurrencesChooser.ReplaceChoice.ALL;
      }

      @Nullable
      @Override
      public PsiType getSelectedType() {
        return myType != null ? myType.getType(context.getPlace(), context.getPlace().getManager()) : null;
      }
    };
  }

  @Override
  protected void addAdditionalVariables(TemplateBuilder builder) {
    GrVariable variable = getVariable();
    assert variable != null && variable.getInitializerGroovy() != null;
    final PsiType initializerType = variable.getInitializerGroovy().getType();
    TypeConstraint[] constraints = initializerType != null && !initializerType.equals(PsiType.NULL) ? new
      SupertypeConstraint[]{SupertypeConstraint.create(initializerType)} : TypeConstraint.EMPTY_ARRAY;
    ChooseTypeExpression typeExpression = new ChooseTypeExpression(constraints,
                                                                   variable.getManager(),
                                                                   variable.getResolveScope(),
                                                                   true,
                                                                   GroovyApplicationSettings.getInstance().INTRODUCE_LOCAL_SELECT_DEF);
    PsiElement element = variable.getTypeElementGroovy() != null ? variable.getTypeElementGroovy() : PsiUtil
      .findModifierInList(variable.getModifierList(), GrModifier.DEF);
    builder.replaceElement(element, "Variable_type", typeExpression, true, true);
  }

  @Override
  protected GroovyIntroduceVariableSettings getSettings() {
    return new GroovyIntroduceVariableSettings() {
      @Override
      public boolean isDeclareFinal() {
        return myCanBeFinalCb.isSelected();
      }

      @Nullable
      @Override
      public String getName() {
        return GrInplaceVariableIntroducer.this.getInputName();
      }

      @Override
      public boolean replaceAllOccurrences() {
        return isReplaceAllOccurrences();
      }

      @Nullable
      @Override
      public PsiType getSelectedType() {
        return GrInplaceVariableIntroducer.this.getSelectedType();
      }
    };
  }

  @Override
  protected void saveSettings(@Nonnull GrVariable variable) {
    GroovyApplicationSettings.getInstance().INTRODUCE_LOCAL_SELECT_DEF = variable.getDeclaredType() == null;
  }

  @Override
  protected int getCaretOffset() {
    return getVariable().getNameIdentifierGroovy().getTextRange().getEndOffset();
  }
}