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
package org.jetbrains.plugins.groovy.impl.annotator.intentions.dynamic.ui;

import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredWriteAction;
import consulo.document.Document;
import consulo.groovy.impl.localize.GroovyInspectionLocalize;
import consulo.groovy.localize.GroovyLocalize;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.language.editor.ui.awt.EditorComboBoxEditor;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.SyntheticElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.content.scope.ProjectScopes;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.IdeBorderFactory;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.ValidationInfo;
import consulo.ui.ex.awt.table.JBTable;
import consulo.undoRedo.CommandProcessor;
import consulo.undoRedo.GlobalUndoableAction;
import consulo.undoRedo.ProjectUndoManager;
import consulo.undoRedo.UnexpectedUndoException;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.impl.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.impl.annotator.intentions.dynamic.DynamicManager;
import org.jetbrains.plugins.groovy.impl.annotator.intentions.dynamic.ParamInfo;
import org.jetbrains.plugins.groovy.impl.annotator.intentions.dynamic.elements.DClassElement;
import org.jetbrains.plugins.groovy.impl.annotator.intentions.dynamic.elements.DItemElement;
import org.jetbrains.plugins.groovy.impl.debugger.fragments.GroovyCodeFragment;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import javax.swing.*;
import java.util.List;

/**
 * @author Dmitry.Krasilschikov
 * @since 2007-12-18
 */
public abstract class DynamicDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(DynamicDialog.class);

  private JComboBox<String> myClassComboBox;
  private JPanel myPanel;
  private JComboBox myTypeComboBox;
  private JLabel myTypeLabel;
  protected JBTable myParametersTable;
  private JCheckBox myStaticCheckBox;
  private JPanel myTablePane;

  private final DynamicManager myDynamicManager;
  protected final Project myProject;
  private final PsiElement myContext;
  private final DynamicElementSettings mySettings;

  public DynamicDialog(PsiElement context, DynamicElementSettings settings, TypeConstraint[] typeConstraints, boolean isTableVisible) {
    super(context.getProject(), true);
    myProject = context.getProject();
    mySettings = settings;
    myContext = context;
    myDynamicManager = DynamicManager.getInstance(myProject);

    if (isTableVisible) {
      myTablePane.setBorder(IdeBorderFactory.createTitledBorder(GroovyLocalize.dynamicPropertiesTableName().get(), false));
    }
    else {
      myTablePane.setVisible(false);
    }

    setTitle(GroovyInspectionLocalize.dynamicElement());
    setUpTypeComboBox(typeConstraints);
    setUpContainingClassComboBox();
    setUpStaticComboBox();

    init();
  }

  private void setUpStaticComboBox() {
    myStaticCheckBox.setSelected(mySettings.isStatic());
  }

  public DynamicElementSettings getSettings() {
    return mySettings;
  }

  @Override
  @RequiredUIAccess
  protected ValidationInfo doValidate() {
    GrTypeElement typeElement = getEnteredTypeName();
    if (typeElement == null) {
      return new ValidationInfo(GroovyInspectionLocalize.noTypeSpecified(), myTypeComboBox);
    }

    PsiType type = typeElement.getType();
    if (type instanceof PsiClassType classType && classType.resolve() == null) {
      return new ValidationInfo(GroovyInspectionLocalize.unresolvedTypeStatus(type.getPresentableText()), myTypeComboBox);
    }
    return null;
  }

  private void setUpContainingClassComboBox() {
    String containingClassName = mySettings.getContainingClassName();
    PsiClass targetClass = JavaPsiFacade.getInstance(myProject).findClass(containingClassName, GlobalSearchScope.allScope(myProject));
    if (targetClass == null || targetClass instanceof SyntheticElement) {
      if (containingClassName.length() > 0) {
        myClassComboBox.addItem(containingClassName);
      }

      if (!containingClassName.equals(CommonClassNames.JAVA_LANG_OBJECT)) {
        myClassComboBox.addItem(CommonClassNames.JAVA_LANG_OBJECT);
      }

      return;
    }

    for (PsiClass aClass : PsiUtil.iterateSupers(targetClass, true)) {
      myClassComboBox.addItem(aClass.getQualifiedName());
    }
  }

  @Nullable
  private Document createDocument(String text) {
    GroovyCodeFragment fragment = new GroovyCodeFragment(myProject, text);
    fragment.setContext(myContext);
    return PsiDocumentManager.getInstance(myProject).getDocument(fragment);
  }

  private void setUpTypeComboBox(TypeConstraint[] typeConstraints) {
    EditorComboBoxEditor comboEditor = new EditorComboBoxEditor(myProject, GroovyFileType.INSTANCE);

    Document document = createDocument("");
    LOG.assertTrue(document != null);

    comboEditor.setItem(document);

    myTypeComboBox.setEditor(comboEditor);
    myTypeComboBox.setEditable(true);
    myTypeComboBox.grabFocus();

    PsiType type = typeConstraints.length == 1 ? typeConstraints[0].getDefaultType() : TypesUtil.getJavaLangObject(myContext);
    myTypeComboBox.getEditor().setItem(createDocument(type.getCanonicalText()));
  }

  @Nullable
  public GrTypeElement getEnteredTypeName() {
    Document typeEditorDocument = getTypeEditorDocument();

    if (typeEditorDocument == null) return null;
    try {
      return GroovyPsiElementFactory.getInstance(myProject).createTypeElement(typeEditorDocument.getText());
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  @Nullable
  public Document getTypeEditorDocument() {
    return myTypeComboBox.getEditor().getItem() instanceof Document document ? document : null;
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  @RequiredUIAccess
  protected void doOKAction() {
    super.doOKAction();

    mySettings.setContainingClassName((String)myClassComboBox.getSelectedItem());
    mySettings.setStatic(myStaticCheckBox.isSelected());
    GrTypeElement typeElement = getEnteredTypeName();

    if (typeElement == null) {
      mySettings.setType(CommonClassNames.JAVA_LANG_OBJECT);
    }
    else {
      PsiType type = typeElement.getType();
      if (type instanceof PsiPrimitiveType) {
        type = TypesUtil.boxPrimitiveType(type, typeElement.getManager(), (GlobalSearchScope)ProjectScopes.getAllScope(myProject));
      }

      String typeQualifiedName = type.getCanonicalText();

      if (typeQualifiedName != null) {
        mySettings.setType(typeQualifiedName);
      }
      else {
        mySettings.setType(type.getPresentableText());
      }
    }

    final Document document = PsiDocumentManager.getInstance(myProject).getDocument(myContext.getContainingFile());

    CommandProcessor.getInstance().newCommand()
      .project(myProject)
      .name(LocalizeValue.localizeTODO("Add dynamic element"))
      .run(() -> {
        ProjectUndoManager.getInstance(myProject).undoableActionPerformed(new GlobalUndoableAction(document) {
          @Override
          @RequiredWriteAction
          public void undo() throws UnexpectedUndoException {
            DItemElement itemElement;
            if (mySettings.isMethod()) {
              List<ParamInfo> myPairList = mySettings.getParams();
              String[] argumentsTypes = QuickfixUtil.getArgumentsTypes(myPairList);
              itemElement =
                myDynamicManager.findConcreteDynamicMethod(mySettings.getContainingClassName(), mySettings.getName(), argumentsTypes);
            }
            else {
              itemElement = myDynamicManager.findConcreteDynamicProperty(mySettings.getContainingClassName(), mySettings.getName());
            }
            if (itemElement == null) {
              Messages.showWarningDialog(
                myProject,
                GroovyInspectionLocalize.cannotPerformUndoOperation().get(),
                GroovyInspectionLocalize.undoDisable().get()
              );
              return;
            }
            DClassElement classElement = myDynamicManager.getClassElementByItem(itemElement);
            if (classElement == null) {
              Messages.showWarningDialog(
                myProject,
                GroovyInspectionLocalize.cannotPerformUndoOperation().get(),
                GroovyInspectionLocalize.undoDisable().get()
              );
              return;
            }
            removeElement(itemElement);
            if (classElement.getMethods().isEmpty() && classElement.getProperties().isEmpty()) {
              myDynamicManager.removeClassElement(classElement);
            }
          }

          @Override
          @RequiredWriteAction
          public void redo() throws UnexpectedUndoException {
            addElement(mySettings);
          }
        });
        addElement(mySettings);
      });
  }

  @RequiredWriteAction
  private void removeElement(DItemElement itemElement) {
    myDynamicManager.removeItemElement(itemElement);
    myDynamicManager.fireChange();
  }

  @RequiredWriteAction
  public void addElement(DynamicElementSettings settings) {
    if (settings.isMethod()) {
      myDynamicManager.addMethod(settings);
    }
    else {
      myDynamicManager.addProperty(settings);
    }

    myDynamicManager.fireChange();
  }

  @Override
  public void doCancelAction() {
    super.doCancelAction();

    DaemonCodeAnalyzer.getInstance(myProject).restart();
  }

  @Override
  @RequiredUIAccess
  public JComponent getPreferredFocusedComponent() {
    return myTypeComboBox;
  }

  protected void setUpTypeLabel(String typeLabelText) {
    myTypeLabel.setText(typeLabelText);
  }
}
