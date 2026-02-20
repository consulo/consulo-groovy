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
package org.jetbrains.plugins.groovy.impl.refactoring.introduce.constant;

import com.intellij.java.impl.codeInsight.PackageUtil;
import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import com.intellij.java.impl.refactoring.ui.JavaVisibilityPanel;
import com.intellij.java.impl.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.java.language.impl.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.util.TreeClassChooser;
import com.intellij.java.language.util.TreeClassChooserFactory;
import consulo.application.WriteAction;
import consulo.document.event.DocumentAdapter;
import consulo.document.event.DocumentEvent;
import consulo.ide.util.DirectoryChooserUtil;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.ui.NameSuggestionsField;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.project.Project;
import consulo.ui.ex.RecentsManager;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.impl.actions.GroovyTemplates;
import org.jetbrains.plugins.groovy.impl.actions.GroovyTemplatesFactory;
import org.jetbrains.plugins.groovy.impl.actions.NewGroovyActionBase;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.GrIntroduceDialog;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.field.GrFieldNameSuggester;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.variable.GroovyVariableValidator;
import org.jetbrains.plugins.groovy.impl.refactoring.ui.GrTypeComboBox;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.*;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceConstantDialog extends DialogWrapper implements GrIntroduceConstantSettings, GrIntroduceDialog<GrIntroduceConstantSettings> {

  private static final Logger LOG =
    Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.introduce.constant" + ".GrIntroduceConstantDialog");

  private final GrIntroduceContext myContext;
  private JLabel myNameLabel;
  private JCheckBox myReplaceAllOccurrences;
  private JPanel myPanel;
  private GrTypeComboBox myTypeCombo;
  private ReferenceEditorComboWithBrowseButton myTargetClassEditor;
  private NameSuggestionsField myNameField;
  private JavaVisibilityPanel myJavaVisibilityPanel;
  private JPanel myTargetClassPanel;
  private JLabel myTargetClassLabel;
  @Nullable
  private PsiClass myTargetClass;
  @Nullable
  private PsiClass myDefaultTargetClass;

  private TargetClassInfo myTargetClassInfo;

  public GrIntroduceConstantDialog(GrIntroduceContext context, @Nullable PsiClass defaultTargetClass) {
    super(context.getProject());
    myContext = context;
    myTargetClass = defaultTargetClass;
    myDefaultTargetClass = defaultTargetClass;

    setTitle(GrIntroduceConstantHandler.REFACTORING_NAME);

    myJavaVisibilityPanel.setVisibility(JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY);

    updateVisibilityPanel();
    updateOkStatus();
    init();
  }

  @Nullable
  public static PsiClass getParentClass(PsiElement occurrence) {
    PsiElement cur = occurrence;
    while (true) {
      PsiClass parentClass = PsiTreeUtil.getParentOfType(cur, PsiClass.class, true);
      if (parentClass == null || parentClass.hasModifierProperty(PsiModifier.STATIC)) {
        return parentClass;
      }
      cur = parentClass;
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

  @Override
  protected JComponent createNorthPanel() {
    initializeName();
    initializeTargetClassEditor();

    if (myContext.getVar() != null) {
      myReplaceAllOccurrences.setEnabled(false);
      myReplaceAllOccurrences.setSelected(true);
    }
    else if (myContext.getOccurrences().length < 2) {
      myReplaceAllOccurrences.setVisible(false);
    }
    return myPanel;
  }

  private void initializeTargetClassEditor() {

    myTargetClassEditor = new ReferenceEditorComboWithBrowseButton(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        TreeClassChooser chooser =
          TreeClassChooserFactory.getInstance(myContext.getProject()).createWithInnerClassesScopeChooser(RefactoringBundle.message(
            "choose.destination.class"),
                                                                                                         GlobalSearchScope.projectScope(
                                                                                                           myContext.getProject()),
                                                                                                         aClass -> aClass.getParent() instanceof GroovyFile || aClass
                                                                                                           .hasModifierProperty(PsiModifier.STATIC),
                                                                                                         null);
        if (myTargetClass != null) {
          chooser.selectDirectory(myTargetClass.getContainingFile().getContainingDirectory());
        }
        chooser.showDialog();
        PsiClass aClass = chooser.getSelected();
        if (aClass != null) {
          myTargetClassEditor.setText(aClass.getQualifiedName());
        }

      }
    }, "", myContext.getProject(), true, RECENTS_KEY);
    myTargetClassPanel.setLayout(new BorderLayout());
    myTargetClassPanel.add(myTargetClassLabel, BorderLayout.NORTH);
    myTargetClassPanel.add(myTargetClassEditor, BorderLayout.CENTER);
    Set<String> possibleClassNames = new LinkedHashSet<String>();
    for (PsiElement occurrence : myContext.getOccurrences()) {
      PsiClass parentClass = getParentClass(occurrence);
      if (parentClass != null && parentClass.getQualifiedName() != null) {
        possibleClassNames.add(parentClass.getQualifiedName());
      }
    }

    for (String possibleClassName : possibleClassNames) {
      myTargetClassEditor.prependItem(possibleClassName);
    }

    if (myDefaultTargetClass != null) {
      myTargetClassEditor.prependItem(myDefaultTargetClass.getQualifiedName());
    }

    myTargetClassEditor.getChildComponent().addDocumentListener(new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        targetClassChanged();
        updateOkStatus();
        // enableEnumDependant(introduceEnumConstant());
      }
    });
  }

  private void initializeName() {
    myNameLabel.setLabelFor(myNameField);

    myPanel.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myNameField.requestFocus();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);

    myNameField.addDataChangedListener(new NameSuggestionsField.DataChanged() {
      @Override
      public void dataChanged() {
        updateOkStatus();
      }
    });
  }

  @Override
  public String getVisibilityModifier() {
    return myJavaVisibilityPanel.getVisibility();
  }

  @Nullable
  @Override
  public PsiClass getTargetClass() {
    return myTargetClassInfo.getTargetClass();
  }

  @Nonnull
  public String getTargetClassName() {
    return myTargetClassEditor.getText();
  }

  @Override
  public GrIntroduceConstantSettings getSettings() {
    return this;
  }

  @Nonnull
  @Override
  public LinkedHashSet<String> suggestNames() {
    return new GrFieldNameSuggester(myContext, new GroovyVariableValidator(myContext), true).suggestNames();
  }

  @Nullable
  @Override
  public String getName() {
    return myNameField.getEnteredName();
  }

  @Override
  public boolean replaceAllOccurrences() {
    return myReplaceAllOccurrences.isSelected();
  }

  @Override
  public PsiType getSelectedType() {
    return myTypeCombo.getSelectedType();
  }

  @NonNls
  private static final String RECENTS_KEY = "GrIntroduceConstantDialog.RECENTS_KEY";

  private void createUIComponents() {
    myJavaVisibilityPanel = new JavaVisibilityPanel(false, true);

    GrVariable var = myContext.getVar();
    GrExpression expression = myContext.getExpression();
    StringPartInfo stringPart = myContext.getStringPart();
    if (expression != null) {
      myTypeCombo = GrTypeComboBox.createTypeComboBoxFromExpression(expression);
    }
    else if (stringPart != null) {
      myTypeCombo = GrTypeComboBox.createTypeComboBoxFromExpression(stringPart.getLiteral());
    }
    else {
      assert var != null;
      myTypeCombo = GrTypeComboBox.createTypeComboBoxWithDefType(var.getDeclaredType(), var);
    }

    List<String> names = new ArrayList<String>();
    if (var != null) {
      names.add(var.getName());
    }
    if (expression != null) {
      ContainerUtil.addAll(names, suggestNames());
    }

    myNameField = new NameSuggestionsField(ArrayUtil.toStringArray(names), myContext.getProject(), GroovyFileType.GROOVY_FILE_TYPE);

    GrTypeComboBox.registerUpDownHint(myNameField, myTypeCombo);
  }

  private void targetClassChanged() {
    String targetClassName = getTargetClassName();
    myTargetClass =
      JavaPsiFacade.getInstance(myContext.getProject()).findClass(targetClassName, GlobalSearchScope.projectScope(myContext.getProject()));
    updateVisibilityPanel();
    //    myIntroduceEnumConstantCb.setEnabled(EnumConstantsUtil.isSuitableForEnumConstant(getSelectedType(),
    // myTargetClassEditor));
  }

  private void updateVisibilityPanel() {
    if (myTargetClass != null && myTargetClass.isInterface()) {
      myJavaVisibilityPanel.disableAllButPublic();
    }
    else {
      UIUtil.setEnabled(TargetAWT.to(myJavaVisibilityPanel.getComponent()), true, true);
      // exclude all modifiers not visible from all occurrences
      Set<String> visible = new HashSet<String>();
      visible.add(PsiModifier.PRIVATE);
      visible.add(PsiModifier.PROTECTED);
      visible.add(PsiModifier.PACKAGE_LOCAL);
      visible.add(PsiModifier.PUBLIC);
      for (PsiElement occurrence : myContext.getOccurrences()) {
        PsiManager psiManager = PsiManager.getInstance(myContext.getProject());
        for (Iterator<String> iterator = visible.iterator(); iterator.hasNext(); ) {
          String modifier = iterator.next();

          try {
            String modifierText = PsiModifier.PACKAGE_LOCAL.equals(modifier) ? "" : modifier + " ";
            PsiField field = JavaPsiFacade.getInstance(psiManager.getProject())
                                                .getElementFactory()
                                                .createFieldFromText(modifierText + "int xxx;", myTargetClass);
            if (!JavaResolveUtil.isAccessible(field, myTargetClass, field.getModifierList(), occurrence, myTargetClass, null)) {
              iterator.remove();
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
      if (!visible.contains(getVisibilityModifier())) {
        if (visible.contains(PsiModifier.PUBLIC)) {
          myJavaVisibilityPanel.setVisibility(PsiModifier.PUBLIC);
        }
        if (visible.contains(PsiModifier.PACKAGE_LOCAL)) {
          myJavaVisibilityPanel.setVisibility(PsiModifier.PACKAGE_LOCAL);
        }
        if (visible.contains(PsiModifier.PROTECTED)) {
          myJavaVisibilityPanel.setVisibility(PsiModifier.PROTECTED);
        }
        if (visible.contains(PsiModifier.PRIVATE)) {
          myJavaVisibilityPanel.setVisibility(PsiModifier.PRIVATE);
        }
      }
    }
  }

  private void updateOkStatus() {
    if (myTargetClassEditor == null) {
      return; //dialog is not initialized yet
    }

    String text = getName();
    if (!GroovyNamesUtil.isIdentifier(text)) {
      setOKActionEnabled(false);
      return;
    }

    String targetClassName = myTargetClassEditor.getText();
    if (targetClassName.trim().length() == 0 && myDefaultTargetClass == null) {
      setOKActionEnabled(false);
      return;
    }
    String trimmed = targetClassName.trim();
    if (!JavaPsiFacade.getInstance(myContext.getProject()).getNameHelper().isQualifiedName(trimmed)) {
      setOKActionEnabled(false);
      return;
    }
    setOKActionEnabled(true);
  }

  @Override
  protected void doOKAction() {
    String targetClassName = getTargetClassName();

    if (myDefaultTargetClass == null || !targetClassName.isEmpty() && !Comparing.strEqual(targetClassName,
                                                                                          myDefaultTargetClass.getQualifiedName())) {
      Module module = ModuleUtilCore.findModuleForPsiElement(myContext.getPlace());
      JavaPsiFacade facade = JavaPsiFacade.getInstance(myContext.getProject());
      PsiClass newClass = facade.findClass(targetClassName, GlobalSearchScope.projectScope(myContext.getProject()));

      if (newClass == null && Messages.showOkCancelDialog(myContext.getProject(),
                                                          GroovyRefactoringBundle.message("class.does.not.exist.in.the.module"),
                                                          RefactoringBundle.message("introduce.constant.title"),
                                                          Messages.getErrorIcon()) != OK_EXIT_CODE) {
        return;
      }
      myTargetClassInfo = new TargetClassInfo(targetClassName,
                                              myContext.getPlace().getContainingFile().getContainingDirectory(),
                                              module,
                                              myContext.getProject());
    }
    else {
      myTargetClassInfo = new TargetClassInfo(myDefaultTargetClass);
    }


    JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY = getVisibilityModifier();

    RecentsManager.getInstance(myContext.getProject()).registerRecentEntry(RECENTS_KEY, targetClassName);

    super.doOKAction();
  }

  private static class TargetClassInfo {
    private PsiClass myTargetClass;

    String myQualifiedName;
    PsiDirectory myBaseDirectory;
    Module myModule;
    Project myProject;

    private TargetClassInfo(PsiClass targetClass) {
      myTargetClass = targetClass;
    }

    private TargetClassInfo(String qualifiedName, PsiDirectory baseDirectory, Module module, Project project) {
      myQualifiedName = qualifiedName;
      myBaseDirectory = baseDirectory;
      myModule = module;
      myProject = project;
    }

    @Nullable
    public PsiClass getTargetClass() {
      if (myTargetClass == null) {
        myTargetClass = getTargetClass(myQualifiedName, myBaseDirectory, myProject, myModule);
      }
      return myTargetClass;
    }

    @Nullable
    private static PsiClass getTargetClass(String qualifiedName, PsiDirectory baseDirectory, Project project, Module module) {
      GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

      PsiClass targetClass = JavaPsiFacade.getInstance(project).findClass(qualifiedName, scope);
      if (targetClass != null) {
        return targetClass;
      }

      String packageName = StringUtil.getPackageName(qualifiedName);
      PsiPackage psiPackage = JavaPsiFacade.getInstance(project).findPackage(packageName);
      PsiDirectory psiDirectory;
      if (psiPackage != null) {
        PsiDirectory[] directories = psiPackage.getDirectories(GlobalSearchScope.allScope(project));
        psiDirectory = directories.length > 1 ? DirectoryChooserUtil.chooseDirectory(directories,
                                                                                     null,
                                                                                     project,
                                                                                     new HashMap<PsiDirectory, String>()) : directories[0];
      }
      else {
        psiDirectory = PackageUtil.findOrCreateDirectoryForPackage(module, packageName, baseDirectory, false);
      }
      if (psiDirectory == null) {
        return null;
      }
      String shortName = StringUtil.getShortName(qualifiedName);
      String fileName = shortName + NewGroovyActionBase.GROOVY_EXTENSION;

      return WriteAction.compute(() ->
                                 {
                                   GroovyFile file = (GroovyFile)GroovyTemplatesFactory.createFromTemplate(psiDirectory,
                                                                                                                 shortName,
                                                                                                                 fileName,
                                                                                                                 GroovyTemplates.GROOVY_CLASS,
                                                                                                                 true);
                                   return file.getTypeDefinitions()[0];
                                 });
    }
  }
}
