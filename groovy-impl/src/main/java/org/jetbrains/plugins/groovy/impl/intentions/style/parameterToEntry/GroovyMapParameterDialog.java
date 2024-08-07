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
package org.jetbrains.plugins.groovy.impl.intentions.style.parameterToEntry;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.util.EventListener;

import jakarta.annotation.Nullable;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;

import consulo.document.event.DocumentEvent;
import consulo.document.event.DocumentListener;
import consulo.language.editor.ui.awt.*;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.impl.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.impl.settings.GroovyApplicationSettings;
import consulo.ui.ex.awt.ComboBox;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.language.editor.ui.awt.EditorComboBoxEditor;
import consulo.language.editor.ui.awt.EditorTextField;
import consulo.language.editor.ui.awt.StringComboboxEditor;

public class GroovyMapParameterDialog extends DialogWrapper {
  private JPanel contentPane;
  private ComboBox myNameComboBox;
  private JLabel myNameLabel;
  private JCheckBox myCbTypeSpec;
  private JCheckBox myCreateNew;
  private final Project myProject;
  private final EventListenerList myListenerList = new EventListenerList();

  public GroovyMapParameterDialog(Project project, final String[] possibleNames, final boolean createNew) {
    super(true);
    myProject = project;
    setUpDialog(possibleNames, createNew);
    init();
  }

  private void setUpDialog(final String[] possibleNames, boolean createNew) {
    setTitle(GroovyIntentionsBundle.message("convert.param.to.map.entry"));

    if (GroovyApplicationSettings.getInstance().CONVERT_PARAM_CREATE_NEW_PARAM != null) {
      myCreateNew.setSelected(createNew = GroovyApplicationSettings.getInstance().CONVERT_PARAM_CREATE_NEW_PARAM.booleanValue());
    } else {
      myCreateNew.setSelected(createNew);
    }

    myNameLabel.setLabelFor(myNameComboBox);
    myCbTypeSpec.setMnemonic(KeyEvent.VK_T);
    myCbTypeSpec.setFocusable(false);
    setUpNameComboBox(possibleNames);
    setModal(true);
    if (GroovyApplicationSettings.getInstance().CONVERT_PARAM_SPECIFY_MAP_TYPE != null) {
      myCbTypeSpec.setSelected(GroovyApplicationSettings.getInstance().CONVERT_PARAM_SPECIFY_MAP_TYPE.booleanValue());
    } else {
      myCbTypeSpec.setSelected(true);
    }

    myCbTypeSpec.setEnabled(createNew);
    myNameComboBox.setEnabled(createNew);
    myNameLabel.setEnabled(createNew);

    myCreateNew.addChangeListener(new ChangeListener() {
      public void stateChanged(final ChangeEvent e) {
        final boolean flag = myCreateNew.isSelected();
        myCbTypeSpec.setEnabled(flag);
        myNameComboBox.setEnabled(flag);
        myNameLabel.setEnabled(flag);
      }
    });
  }

  public boolean createNewFirst() {
    return myCreateNew.isSelected();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameComboBox;
  }

  public boolean specifyTypeExplicitly() {
    return myCbTypeSpec.isSelected();
  }

  @Override
  protected void doOKAction() {
    if (myCbTypeSpec.isEnabled()) {
      GroovyApplicationSettings.getInstance().CONVERT_PARAM_SPECIFY_MAP_TYPE = myCbTypeSpec.isSelected();
    }
    GroovyApplicationSettings.getInstance().CONVERT_PARAM_CREATE_NEW_PARAM = myCreateNew.isSelected();
    super.doOKAction();
  }

  @Override
  public void doCancelAction() {
    super.doCancelAction();
  }

  protected JComponent createCenterPanel() {
    return contentPane;
  }

  public JComponent getContentPane() {
    return contentPane;
  }

  @Nullable
  protected String getEnteredName() {
    if (myNameComboBox.getEditor().getItem() instanceof String && ((String)myNameComboBox.getEditor().getItem()).length() > 0) {
      return (String)myNameComboBox.getEditor().getItem();
    } else {
      return null;
    }
  }

  private void setUpNameComboBox(String[] possibleNames) {

    final EditorComboBoxEditor comboEditor = new StringComboboxEditor(myProject, GroovyFileType.GROOVY_FILE_TYPE, myNameComboBox);

    myNameComboBox.setEditor(comboEditor);
    myNameComboBox.setRenderer(new EditorComboBoxRenderer(comboEditor));

    myNameComboBox.setEditable(true);
    myNameComboBox.setMaximumRowCount(8);
    myListenerList.add(DataChangedListener.class, new DataChangedListener());

    myNameComboBox.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        fireNameDataChanged();
      }
    });

    ((EditorTextField)myNameComboBox.getEditor().getEditorComponent()).addDocumentListener(new DocumentListener() {
      public void beforeDocumentChange(DocumentEvent event) {
      }

      public void documentChanged(DocumentEvent event) {
        fireNameDataChanged();
      }
    });

    contentPane.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myNameComboBox.requestFocus();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);

    for (String possibleName : possibleNames) {
      myNameComboBox.addItem(possibleName);
    }
  }

  private void fireNameDataChanged() {
    Object[] list = myListenerList.getListenerList();
    for (Object aList : list) {
      if (aList instanceof DataChangedListener) {
        ((DataChangedListener)aList).dataChanged();
      }
    }
  }

  class DataChangedListener implements EventListener {
    void dataChanged() {
      updateOkStatus();
    }
  }

  private void updateOkStatus() {
    String text = getEnteredName();
    setOKActionEnabled(GroovyNamesUtil.isIdentifier(text));
  }

}
