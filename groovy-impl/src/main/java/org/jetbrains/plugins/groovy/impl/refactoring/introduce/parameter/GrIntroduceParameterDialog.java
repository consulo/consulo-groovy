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
package org.jetbrains.plugins.groovy.impl.refactoring.introduce.parameter;

import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiType;
import consulo.application.AccessToken;
import consulo.application.WriteAction;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.ui.NameSuggestionsField;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.*;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.primitive.ints.IntList;
import consulo.util.collection.primitive.ints.IntLists;
import consulo.util.collection.primitive.objects.ObjectIntMap;
import consulo.util.collection.primitive.objects.ObjectMaps;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParameterListOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.impl.refactoring.GrRefactoringError;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.impl.refactoring.HelpID;
import org.jetbrains.plugins.groovy.impl.refactoring.extract.ExtractUtil;
import org.jetbrains.plugins.groovy.impl.refactoring.extract.ParameterInfo;
import org.jetbrains.plugins.groovy.impl.refactoring.extract.ParameterTablePanel;
import org.jetbrains.plugins.groovy.impl.refactoring.extract.closure.ExtractClosureFromClosureProcessor;
import org.jetbrains.plugins.groovy.impl.refactoring.extract.closure.ExtractClosureFromMethodProcessor;
import org.jetbrains.plugins.groovy.impl.refactoring.extract.closure.ExtractClosureHelperImpl;
import org.jetbrains.plugins.groovy.impl.refactoring.extract.closure.ExtractClosureProcessorBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;
import org.jetbrains.plugins.groovy.impl.refactoring.ui.GrMethodSignatureComponent;
import org.jetbrains.plugins.groovy.impl.refactoring.ui.GrTypeComboBox;
import org.jetbrains.plugins.groovy.impl.settings.GroovyApplicationSettings;

import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.ObjIntConsumer;

import static com.intellij.java.impl.refactoring.IntroduceParameterRefactoring.*;

public class GrIntroduceParameterDialog extends DialogWrapper {
  private GrTypeComboBox myTypeComboBox;
  private NameSuggestionsField myNameSuggestionsField;
  private JCheckBox myDeclareFinalCheckBox;
  private JCheckBox myDelegateViaOverloadingMethodCheckBox;
  private JBRadioButton myDoNotReplaceRadioButton;
  private JBRadioButton myReplaceFieldsInaccessibleInRadioButton;
  private JBRadioButton myReplaceAllFieldsRadioButton;
  private JPanel myGetterPanel;
  private IntroduceParameterInfo myInfo;
  private ObjectIntMap<JCheckBox> toRemoveCBs;

  private GrMethodSignatureComponent mySignature;
  private ParameterTablePanel myTable;
  private JPanel mySignaturePanel;
  private JCheckBox myForceReturnCheckBox;
  private Project myProject;

  private final boolean myCanIntroduceSimpleParameter;

  public GrIntroduceParameterDialog(IntroduceParameterInfo info) {
    super(info.getProject(), true);
    myInfo = info;
    myProject = info.getProject();
    myCanIntroduceSimpleParameter =
      GroovyIntroduceParameterUtil.findExpr(myInfo) != null || GroovyIntroduceParameterUtil.findVar(myInfo) != null || findStringPart() != null;

    ObjectIntMap<GrParameter> parametersToRemove = GroovyIntroduceParameterUtil.findParametersToRemove(info);
    toRemoveCBs = ObjectMaps.newObjectIntHashMap(parametersToRemove.size());
    for (Object p : parametersToRemove.keySet()) {
      JCheckBox cb = new JCheckBox(GroovyRefactoringBundle.message("remove.parameter.0.no.longer.used", ((GrParameter)p).getName()));
      toRemoveCBs.putInt(cb, parametersToRemove.getInt((GrParameter)p));
      cb.setSelected(true);
    }

    init();
  }

  @Override
  protected void init() {
    super.init();

    JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();

    initReplaceFieldsWithGetters(settings);

    myDeclareFinalCheckBox.setSelected(hasFinalModifier());
    myDelegateViaOverloadingMethodCheckBox.setVisible(myInfo.getToSearchFor() != null);

    setTitle(RefactoringBundle.message("introduce.parameter.title"));

    myTable.init(myInfo);

    final GrParameter[] parameters = myInfo.getToReplaceIn().getParameters();
    toRemoveCBs.forEach(new ObjIntConsumer<JCheckBox>() {
      @Override
      public void accept(JCheckBox checkbox, int index) {
        checkbox.setSelected(true);

        GrParameter param = parameters[index];
        ParameterInfo pinfo = findParamByOldName(param.getName());
        if (pinfo != null) {
          pinfo.setPassAsParameter(false);
        }
      }
    });

    updateSignature();

    if (myCanIntroduceSimpleParameter) {
      mySignaturePanel.setVisible(false);

      //action to hide signature panel if we have variants to introduce simple parameter
      myTypeComboBox.addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          mySignaturePanel.setVisible(myTypeComboBox.isClosureSelected());
          pack();
        }
      });
    }

    PsiType closureReturnType = inferClosureReturnType();
    if (closureReturnType == PsiType.VOID) {
      myForceReturnCheckBox.setEnabled(false);
      myForceReturnCheckBox.setSelected(false);
    }
    else {
      myForceReturnCheckBox.setSelected(isForceReturn());
    }

    if (myInfo.getToReplaceIn() instanceof GrClosableBlock) {
      myDelegateViaOverloadingMethodCheckBox.setEnabled(false);
      myDelegateViaOverloadingMethodCheckBox.setToolTipText("Delegating is not allowed in closure context");
    }


    pack();
  }

  private static boolean isForceReturn() {
    return GroovyApplicationSettings.getInstance().FORCE_RETURN;
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel north = new JPanel();
    north.setLayout(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, true));

    JPanel namePanel = createNamePanel();
    namePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    north.add(namePanel);

    createCheckBoxes(north);

    myGetterPanel = createFieldPanel();
    myGetterPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    north.add(myGetterPanel);

    JPanel root = new JPanel(new BorderLayout());
    mySignaturePanel = createSignaturePanel();
    root.add(mySignaturePanel, BorderLayout.CENTER);
    root.add(north, BorderLayout.NORTH);

    return root;
  }

  private JPanel createSignaturePanel() {
    mySignature = new GrMethodSignatureComponent("", myProject);
    myTable = new ParameterTablePanel() {
      @Override
      protected void updateSignature() {
        GrIntroduceParameterDialog.this.updateSignature();
      }

      @Override
      protected void doEnterAction() {
        clickDefaultButton();
      }

      @Override
      protected void doCancelAction() {
        GrIntroduceParameterDialog.this.doCancelAction();
      }
    };

    mySignature.setBorder(IdeBorderFactory.createTitledBorder(GroovyRefactoringBundle.message("signature.preview.border.title"), false));

    Splitter splitter = new Splitter(true);

    splitter.setFirstComponent(myTable);
    splitter.setSecondComponent(mySignature);

    mySignature.setPreferredSize(new Dimension(500, 100));
    mySignature.setSize(new Dimension(500, 100));

    splitter.setShowDividerIcon(false);

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(splitter, BorderLayout.CENTER);
    myForceReturnCheckBox = new JCheckBox(UIUtil.replaceMnemonicAmpersand("Use e&xplicit return statement"));
    panel.add(myForceReturnCheckBox, BorderLayout.NORTH);

    return panel;
  }

  private JPanel createFieldPanel() {
    myDoNotReplaceRadioButton = new JBRadioButton(UIUtil.replaceMnemonicAmpersand("Do n&ot replace"));
    myReplaceFieldsInaccessibleInRadioButton =
      new JBRadioButton(UIUtil.replaceMnemonicAmpersand("Replace fields &inaccessible in usage context"));
    myReplaceAllFieldsRadioButton =
      new JBRadioButton(UIUtil.replaceMnemonicAmpersand("&Replace all fields"));

    myDoNotReplaceRadioButton.setFocusable(false);
    myReplaceFieldsInaccessibleInRadioButton.setFocusable(false);
    myReplaceAllFieldsRadioButton.setFocusable(false);

    ButtonGroup group = new ButtonGroup();
    group.add(myDoNotReplaceRadioButton);
    group.add(myReplaceFieldsInaccessibleInRadioButton);
    group.add(myReplaceAllFieldsRadioButton);

    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.add(myDoNotReplaceRadioButton);
    panel.add(myReplaceFieldsInaccessibleInRadioButton);
    panel.add(myReplaceAllFieldsRadioButton);

    panel.setBorder(IdeBorderFactory.createTitledBorder("Replace fields used in expression with their getters", true));
    return panel;
  }

  private JPanel createNamePanel() {
    GridBag c = new GridBag().setDefaultAnchor(GridBagConstraints.WEST).setDefaultInsets(1, 1, 1, 1);
    JPanel namePanel = new JPanel(new GridBagLayout());

    JLabel typeLabel = new JLabel(UIUtil.replaceMnemonicAmpersand("&Type:"));
    c.nextLine().next().weightx(0).fillCellNone();
    namePanel.add(typeLabel, c);

    myTypeComboBox =
      createTypeComboBox(GroovyIntroduceParameterUtil.findVar(myInfo), GroovyIntroduceParameterUtil.findExpr(myInfo), findStringPart());
    c.next().weightx(1).fillCellHorizontally();
    namePanel.add(myTypeComboBox, c);
    typeLabel.setLabelFor(myTypeComboBox);

    JLabel nameLabel = new JLabel(UIUtil.replaceMnemonicAmpersand("&Name:"));
    c.nextLine().next().weightx(0).fillCellNone();
    namePanel.add(nameLabel, c);

    myNameSuggestionsField = createNameField(GroovyIntroduceParameterUtil.findVar(myInfo));
    c.next().weightx(1).fillCellHorizontally();
    namePanel.add(myNameSuggestionsField, c);
    nameLabel.setLabelFor(myNameSuggestionsField);

    GrTypeComboBox.registerUpDownHint(myNameSuggestionsField, myTypeComboBox);

    return namePanel;
  }


  private void createCheckBoxes(JPanel panel) {
    myDeclareFinalCheckBox = new JCheckBox(UIUtil.replaceMnemonicAmpersand("Declare &final"));
    myDeclareFinalCheckBox.setFocusable(false);
    panel.add(myDeclareFinalCheckBox);

    myDelegateViaOverloadingMethodCheckBox = new JCheckBox(UIUtil.replaceMnemonicAmpersand("De&legate via overloading method"));
    myDelegateViaOverloadingMethodCheckBox.setFocusable(false);
    panel.add(myDelegateViaOverloadingMethodCheckBox);

    for (JCheckBox cb : toRemoveCBs.keySet()) {
      cb.setFocusable(false);
      panel.add(cb);
    }
  }

  private GrTypeComboBox createTypeComboBox(GrVariable var, GrExpression expr, StringPartInfo stringPartInfo) {
    GrTypeComboBox box;
    if (var != null) {
      box = GrTypeComboBox.createTypeComboBoxWithDefType(var.getDeclaredType(), var);
    }
    else if (expr != null) {
      box = GrTypeComboBox.createTypeComboBoxFromExpression(expr);
    }
    else if (stringPartInfo != null) {
      box = GrTypeComboBox.createTypeComboBoxFromExpression(stringPartInfo.getLiteral());
    }
    else {
      box = GrTypeComboBox.createEmptyTypeComboBox();
    }

    box.addClosureTypesFrom(inferClosureReturnType(), myInfo.getContext());
    if (expr == null && var == null && stringPartInfo == null) {
      box.setSelectedIndex(box.getItemCount() - 1);
    }
    return box;
  }

  @Nullable
  private PsiType inferClosureReturnType() {
    ExtractClosureHelperImpl mockHelper =
      new ExtractClosureHelperImpl(myInfo, "__test___n_", false, IntLists.newArrayList(), false, 0, false, false);
    PsiType returnType;
    AccessToken token = WriteAction.start();
    try {
      returnType = ExtractClosureProcessorBase.generateClosure(mockHelper).getReturnType();
    }
    finally {
      token.finish();
    }
    return returnType;
  }

  private NameSuggestionsField createNameField(GrVariable var) {
    List<String> names = new ArrayList<String>();
    if (var != null) {
      names.add(var.getName());
    }
    ContainerUtil.addAll(names, suggestNames());

    return new NameSuggestionsField(ArrayUtil.toStringArray(names), myProject, GroovyFileType.GROOVY_FILE_TYPE);
  }

  private void initReplaceFieldsWithGetters(JavaRefactoringSettings settings) {
    PsiField[] usedFields = GroovyIntroduceParameterUtil.findUsedFieldsWithGetters(myInfo.getStatements(), getContainingClass());
    myGetterPanel.setVisible(usedFields.length > 0);
    switch (settings.INTRODUCE_PARAMETER_REPLACE_FIELDS_WITH_GETTERS) {
      case REPLACE_FIELDS_WITH_GETTERS_ALL:
        myReplaceAllFieldsRadioButton.setSelected(true);
        break;
      case REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE:
        myReplaceFieldsInaccessibleInRadioButton.setSelected(true);
        break;
      case REPLACE_FIELDS_WITH_GETTERS_NONE:
        myDoNotReplaceRadioButton.setSelected(true);
        break;
    }
  }

  private void updateSignature() {
    StringBuilder b = new StringBuilder();
    b.append("{ ");
    String[] params = ExtractUtil.getParameterString(myInfo, false);
    for (int i = 0; i < params.length; i++) {
      if (i > 0) {
        b.append("  ");
      }
      b.append(params[i]);
      b.append('\n');
    }
    b.append(" ->\n}");
    mySignature.setSignature(b.toString());
  }

  @RequiredUIAccess
  @Override
  protected ValidationInfo doValidate() {
    String text = getEnteredName();
    if (!GroovyNamesUtil.isIdentifier(text)) {
      return new ValidationInfo(GroovyRefactoringBundle.message("name.is.wrong", text), myNameSuggestionsField);
    }

    if (myTypeComboBox.isClosureSelected()) {
      for (ObjectIntMap.Entry<JCheckBox> entry : toRemoveCBs.entrySet()) {
        JCheckBox checkbox = entry.getKey();
        int index = entry.getValue();

        if (!checkbox.isSelected()) {
          continue;
        }

        GrParameter param = myInfo.getToReplaceIn().getParameters()[index];
        ParameterInfo pinfo = findParamByOldName(param.getName());
        if (pinfo == null || !pinfo.passAsParameter()) {
          continue;
        }

        String message = GroovyRefactoringBundle
          .message("you.cannot.pass.as.parameter.0.because.you.remove.1.from.base.method", pinfo.getName(), param.getName());
        return new ValidationInfo(message);
      }
    }

    return null;
  }

  @Nullable
  private ParameterInfo findParamByOldName(String name) {
    for (ParameterInfo info : myInfo.getParameterInfos()) {
      if (name.equals(info.getOldName())) {
        return info;
      }
    }
    return null;
  }

  @Nullable
  private PsiClass getContainingClass() {
    GrParameterListOwner toReplaceIn = myInfo.getToReplaceIn();
    if (toReplaceIn instanceof GrMethod) {
      return ((GrMethod)toReplaceIn).getContainingClass();
    }
    else {
      return PsiTreeUtil.getContextOfType(toReplaceIn, PsiClass.class);
    }
  }

  private boolean hasFinalModifier() {
    Boolean createFinals = JavaRefactoringSettings.getInstance().INTRODUCE_PARAMETER_CREATE_FINALS;
    return createFinals == null ? CodeStyleSettingsManager.getSettings(myProject).GENERATE_FINAL_PARAMETERS : createFinals.booleanValue();
  }

  @Override
  public void doOKAction() {
    saveSettings();

    super.doOKAction();

    GrParameterListOwner toReplaceIn = myInfo.getToReplaceIn();

    GrExpression expr = GroovyIntroduceParameterUtil.findExpr(myInfo);
    GrVariable var = GroovyIntroduceParameterUtil.findVar(myInfo);
    StringPartInfo stringPart = findStringPart();

    if (myTypeComboBox.isClosureSelected() || expr == null && var == null && stringPart == null) {
      GrIntroduceParameterSettings settings = new ExtractClosureHelperImpl(myInfo,
                                                                           getEnteredName(),
                                                                           myDeclareFinalCheckBox.isSelected(),
                                                                           getParametersToRemove(),
                                                                           myDelegateViaOverloadingMethodCheckBox.isSelected(),
                                                                           getReplaceFieldsWithGetter(),
                                                                           myForceReturnCheckBox.isSelected(),
                                                                           myTypeComboBox.getSelectedType() == null);
      if (toReplaceIn instanceof GrMethod) {
        invokeRefactoring(new ExtractClosureFromMethodProcessor(settings));
      }
      else {
        invokeRefactoring(new ExtractClosureFromClosureProcessor(settings));
      }
    }
    else {

      GrIntroduceParameterSettings settings = new GrIntroduceExpressionSettingsImpl(myInfo,
                                                                                    getEnteredName(),
                                                                                    myDeclareFinalCheckBox.isSelected(),
                                                                                    getParametersToRemove(),
                                                                                    myDelegateViaOverloadingMethodCheckBox.isSelected(),
                                                                                    getReplaceFieldsWithGetter(),
                                                                                    expr,
                                                                                    var,
                                                                                    myTypeComboBox.getSelectedType(),
                                                                                    myForceReturnCheckBox.isSelected());
      if (toReplaceIn instanceof GrMethod) {
        invokeRefactoring(new GrIntroduceParameterProcessor(settings));
      }
      else {
        invokeRefactoring(new GrIntroduceClosureParameterProcessor(settings));
      }
    }
  }

  private String getEnteredName() {
    return myNameSuggestionsField.getEnteredName();
  }

  private void saveSettings() {
    JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    settings.INTRODUCE_PARAMETER_CREATE_FINALS = myDeclareFinalCheckBox.isSelected();
    if (myGetterPanel.isVisible()) {
      settings.INTRODUCE_PARAMETER_REPLACE_FIELDS_WITH_GETTERS = getReplaceFieldsWithGetter();
    }
    if (myForceReturnCheckBox.isEnabled() && mySignaturePanel.isVisible()) {
      GroovyApplicationSettings.getInstance().FORCE_RETURN = myForceReturnCheckBox.isSelected();
    }
  }

  protected void invokeRefactoring(BaseRefactoringProcessor processor) {
    Runnable prepareSuccessfulCallback = new Runnable() {
      public void run() {
        close(DialogWrapper.OK_EXIT_CODE);
      }
    };
    processor.setPrepareSuccessfulSwingThreadCallback(prepareSuccessfulCallback);
    processor.setPreviewUsages(false);
    processor.run();
  }

  @Nonnull
  public LinkedHashSet<String> suggestNames() {
    GrVariable var = GroovyIntroduceParameterUtil.findVar(myInfo);
    GrExpression expr = GroovyIntroduceParameterUtil.findExpr(myInfo);
    StringPartInfo stringPart = findStringPart();

    return GroovyIntroduceParameterUtil.suggestNames(var, expr, stringPart, myInfo.getToReplaceIn(), myProject);
  }

  private int getReplaceFieldsWithGetter() {
    if (myDoNotReplaceRadioButton.isSelected()) {
      return REPLACE_FIELDS_WITH_GETTERS_NONE;
    }
    if (myReplaceFieldsInaccessibleInRadioButton.isSelected()) {
      return REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE;
    }
    if (myReplaceAllFieldsRadioButton.isSelected()) {
      return REPLACE_FIELDS_WITH_GETTERS_ALL;
    }
    throw new GrRefactoringError("no check box selected");
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameSuggestionsField;
  }

  @Override
  protected String getHelpId() {
    return HelpID.GROOVY_INTRODUCE_PARAMETER;
  }

  private IntList getParametersToRemove() {
    IntList list = IntLists.newArrayList();
    for (JCheckBox o : toRemoveCBs.keySet()) {
      if (o.isSelected()) {
        list.add(toRemoveCBs.getInt(o));
      }
    }
    return list;
  }

  private StringPartInfo findStringPart() {
    return myInfo.getStringPartInfo();
  }

}
