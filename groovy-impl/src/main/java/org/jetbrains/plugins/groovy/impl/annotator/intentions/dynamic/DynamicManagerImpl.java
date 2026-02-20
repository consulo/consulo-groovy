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
package org.jetbrains.plugins.groovy.impl.annotator.intentions.dynamic;

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiVariable;
import consulo.annotation.component.ServiceImpl;
import consulo.codeEditor.Editor;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.fileEditor.FileEditorManager;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.project.Project;
import consulo.ui.ex.awt.tree.TreeUtil;
import consulo.ui.ex.awt.tree.table.ListTreeTableModelOnColumns;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.plugins.groovy.impl.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.impl.annotator.intentions.dynamic.elements.*;
import org.jetbrains.plugins.groovy.impl.annotator.intentions.dynamic.ui.DynamicElementSettings;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * User: Dmitry.Krasilschikov
 * Date: 23.11.2007
 */
@Singleton
@State(name = "DynamicElementsStorage", storages = @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/dynamic.xml"))
@ServiceImpl
public class DynamicManagerImpl extends DynamicManager {
  private final Project myProject;
  private DRootElement myRootElement = new DRootElement();

  @Inject
  public DynamicManagerImpl(Project project) {
    myProject = project;
  }

  public Project getProject() {
    return myProject;
  }

  @Override
  public void addProperty(DynamicElementSettings settings) {
    assert settings != null;
    assert !settings.isMethod();

    DPropertyElement propertyElement = (DPropertyElement)createDynamicElement(settings);
    DClassElement classElement = getOrCreateClassElement(myProject, settings.getContainingClassName());

    ToolWindow window = DynamicToolWindowWrapper.getInstance(myProject).getToolWindow(); //important to fetch myToolWindow before adding
    classElement.addProperty(propertyElement);
    addItemInTree(classElement, propertyElement, window);
  }

  private void removeItemFromTree(DItemElement itemElement, DClassElement classElement) {
    DynamicToolWindowWrapper wrapper = DynamicToolWindowWrapper.getInstance(myProject);
    ListTreeTableModelOnColumns model = wrapper.getTreeTableModel();
    Object classNode = TreeUtil.findNodeWithObject(classElement, model, model.getRoot());
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)TreeUtil.findNodeWithObject(itemElement, model, classNode);
    if (node == null) {
      return;
    }
    DefaultMutableTreeNode parent = (DefaultMutableTreeNode)node.getParent();

    doRemove(wrapper, node, parent);
  }

  private void removeClassFromTree(DClassElement classElement) {
    DynamicToolWindowWrapper wrapper = DynamicToolWindowWrapper.getInstance(myProject);
    ListTreeTableModelOnColumns model = wrapper.getTreeTableModel();
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)TreeUtil.findNodeWithObject(classElement, model, model.getRoot());
    if (node == null) {
      return;
    }
    DefaultMutableTreeNode parent = (DefaultMutableTreeNode)node.getParent();

    doRemove(wrapper, node, parent);
  }

  private static void doRemove(DynamicToolWindowWrapper wrapper, DefaultMutableTreeNode node, DefaultMutableTreeNode parent) {
    DefaultMutableTreeNode toSelect = (parent.getChildAfter(node) != null || parent.getChildCount() == 1 ?
      node.getNextNode() :
      node.getPreviousNode());


    wrapper.removeFromParent(parent, node);
    if (toSelect != null) {
      wrapper.setSelectedNode(toSelect);
    }
  }

  private void addItemInTree(final DClassElement classElement, final DItemElement itemElement, ToolWindow window) {
    final ListTreeTableModelOnColumns myTreeTableModel =
      DynamicToolWindowWrapper.getInstance(myProject).getTreeTableModel();

    window.activate(new Runnable() {
      @Override
      public void run() {
        Object rootObject = myTreeTableModel.getRoot();
        if (!(rootObject instanceof DefaultMutableTreeNode)) {
          return;
        }
        DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode)rootObject;

        DefaultMutableTreeNode node = new DefaultMutableTreeNode(itemElement);
        if (rootNode.getChildCount() > 0) {
          for (DefaultMutableTreeNode classNode = (DefaultMutableTreeNode)rootNode.getFirstChild();
               classNode != null;
               classNode = (DefaultMutableTreeNode)rootNode.getChildAfter(classNode)) {

            Object classRow = classNode.getUserObject();
            if (!(classRow instanceof DClassElement)) {
              return;
            }

            DClassElement otherClassName = (DClassElement)classRow;
            if (otherClassName.equals(classElement)) {
              int index = getIndexToInsert(classNode, itemElement);
              classNode.insert(node, index);
              myTreeTableModel.nodesWereInserted(classNode, new int[]{index});
              DynamicToolWindowWrapper.getInstance(myProject).setSelectedNode(node);
              return;
            }
          }
        }

        // if there is no such class in tree
        int index = getIndexToInsert(rootNode, classElement);
        DefaultMutableTreeNode classNode = new DefaultMutableTreeNode(classElement);
        rootNode.insert(classNode, index);
        myTreeTableModel.nodesWereInserted(rootNode, new int[]{index});

        classNode.add(node);
        myTreeTableModel.nodesWereInserted(classNode, new int[]{0});

        DynamicToolWindowWrapper.getInstance(myProject).setSelectedNode(node);
      }
    }, true);
  }

  private static int getIndexToInsert(DefaultMutableTreeNode parent, DNamedElement namedElement) {
    if (parent.getChildCount() == 0) {
      return 0;
    }

    int res = 0;
    for (DefaultMutableTreeNode child = (DefaultMutableTreeNode)parent.getFirstChild();
         child != null;
         child = (DefaultMutableTreeNode)parent.getChildAfter(child)) {
      Object childObject = child.getUserObject();

      if (!(childObject instanceof DNamedElement)) {
        return 0;
      }

      String otherName = ((DNamedElement)childObject).getName();
      if (otherName.compareTo(namedElement.getName()) > 0) {
        return res;
      }
      res++;
    }
    return res;
  }

  @Override
  public void addMethod(DynamicElementSettings settings) {
    if (settings == null) {
      return;
    }
    assert settings.isMethod();

    DMethodElement methodElement = (DMethodElement)createDynamicElement(settings);
    DClassElement classElement = getOrCreateClassElement(myProject, settings.getContainingClassName());

    ToolWindow window = DynamicToolWindowWrapper.getInstance(myProject).getToolWindow(); //important to fetch myToolWindow before adding
    classElement.addMethod(methodElement);
    addItemInTree(classElement, methodElement, window);
  }

  @Override
  public void removeClassElement(DClassElement classElement) {

    DRootElement rootElement = getRootElement();
    rootElement.removeClassElement(classElement.getName());
    removeClassFromTree(classElement);
  }

  private void removePropertyElement(DPropertyElement propertyElement) {
    DClassElement classElement = getClassElementByItem(propertyElement);
    assert classElement != null;

    classElement.removeProperty(propertyElement);
  }

  @Override
  @Nonnull
  public Collection<DPropertyElement> findDynamicPropertiesOfClass(String className) {
    DClassElement classElement = findClassElement(getRootElement(), className);

    if (classElement != null) {
      return classElement.getProperties();
    }
    return new ArrayList<DPropertyElement>();
  }

  @Override
  @Nullable
  public String getPropertyType(String className, String propertyName) {
    DPropertyElement dynamicProperty = findConcreteDynamicProperty(getRootElement(), className, propertyName);

    if (dynamicProperty == null) {
      return null;
    }
    return dynamicProperty.getType();
  }

  @Override
  @Nonnull
  public Collection<DClassElement> getAllContainingClasses() {
    //TODO: use iterator
    DRootElement root = getRootElement();

    return root.getContainingClasses();
  }

  @Override
  public DRootElement getRootElement() {
    return myRootElement;
  }

  @Override
  @Nullable
  public String replaceDynamicPropertyName(String className, String oldPropertyName, String newPropertyName) {
    DClassElement classElement = findClassElement(getRootElement(), className);
    if (classElement == null) {
      return null;
    }

    DPropertyElement oldPropertyElement = classElement.getPropertyByName(oldPropertyName);
    if (oldPropertyElement == null) {
      return null;
    }
    classElement.removeProperty(oldPropertyElement);
    classElement.addProperty(new DPropertyElement(oldPropertyElement.isStatic(), newPropertyName, oldPropertyElement.getType()));
    fireChange();
    DynamicToolWindowWrapper.getInstance(getProject()).rebuildTreePanel();


    return newPropertyName;
  }

  @Override
  @Nullable
  public String replaceDynamicPropertyType(String className, String propertyName, String oldPropertyType, String newPropertyType) {
    DPropertyElement property = findConcreteDynamicProperty(className, propertyName);

    if (property == null) {
      return null;
    }

    property.setType(newPropertyType);
    fireChange();
    return newPropertyType;
  }

  /*
   * Find dynamic property in class with name
   */

  @Nullable
  private static DMethodElement findConcreteDynamicMethod(DRootElement rootElement,
                                                          String containingClassName,
                                                          String methodName,
                                                          String[] parametersTypes) {
    DClassElement classElement = findClassElement(rootElement, containingClassName);
    if (classElement == null) {
      return null;
    }

    return classElement.getMethod(methodName, parametersTypes);
  }

  //  @Nullable

  @Override
  public DMethodElement findConcreteDynamicMethod(String containingClassName, String name, String[] parameterTypes) {
    return findConcreteDynamicMethod(getRootElement(), containingClassName, name, parameterTypes);
  }

  private void removeMethodElement(DMethodElement methodElement) {
    DClassElement classElement = getClassElementByItem(methodElement);
    assert classElement != null;

    classElement.removeMethod(methodElement);
  }

  @Override
  public void removeItemElement(DItemElement element) {
    DClassElement classElement = getClassElementByItem(element);
    if (classElement == null) {
      return;
    }

    if (element instanceof DPropertyElement) {
      removePropertyElement(((DPropertyElement)element));
    }
    else if (element instanceof DMethodElement) {
      removeMethodElement(((DMethodElement)element));
    }

    removeItemFromTree(element, classElement);
  }

  @Override
  public void replaceDynamicMethodType(String className, String name, List<ParamInfo> myPairList, String oldType, String newType) {
    DMethodElement method = findConcreteDynamicMethod(className, name, QuickfixUtil.getArgumentsTypes(myPairList));

    if (method == null) {
      return;
    }
    method.setType(newType);
    fireChange();
  }

  @Override
  @Nonnull
  public DClassElement getOrCreateClassElement(Project project, String className) {
    DClassElement classElement = DynamicManager.getInstance(myProject).getRootElement().getClassElement(className);
    if (classElement == null) {
      return new DClassElement(project, className);
    }

    return classElement;
  }

  @Override
  @Nullable
  public DClassElement getClassElementByItem(DItemElement itemElement) {
    Collection<DClassElement> classes = getAllContainingClasses();
    for (DClassElement aClass : classes) {
      if (aClass.containsElement(itemElement)) {
        return aClass;
      }
    }
    return null;
  }

  @Override
  public void replaceDynamicMethodName(String className, String oldName, String newName, String[] types) {
    DMethodElement oldMethodElement = findConcreteDynamicMethod(className, oldName, types);
    if (oldMethodElement != null) {
      oldMethodElement.setName(newName);
    }
    DynamicToolWindowWrapper.getInstance(getProject()).rebuildTreePanel();
    fireChange();
  }

  @Override
  public Iterable<PsiMethod> getMethods(final String classQname) {
    DClassElement classElement = getRootElement().getClassElement(classQname);
    if (classElement == null) {
      return Collections.emptyList();
    }
    return ContainerUtil.map(classElement.getMethods(), new Function<DMethodElement, PsiMethod>() {
      @Override
      public PsiMethod apply(DMethodElement methodElement) {
        return methodElement.getPsi(PsiManager.getInstance(myProject), classQname);
      }
    });
  }

  @Override
  public Iterable<PsiVariable> getProperties(final String classQname) {
    DClassElement classElement = getRootElement().getClassElement(classQname);
    if (classElement == null) {
      return Collections.emptyList();
    }
    return ContainerUtil.map(classElement.getProperties(), new Function<DPropertyElement, PsiVariable>() {
      @Override
      public PsiVariable apply(DPropertyElement propertyElement) {
        return propertyElement.getPsi(PsiManager.getInstance(myProject), classQname);
      }
    });
  }

  @Override
  public void replaceClassName(DClassElement oldClassElement, String newClassName) {
    if (oldClassElement == null) {
      return;
    }

    DRootElement rootElement = getRootElement();
    rootElement.removeClassElement(oldClassElement.getName());

    oldClassElement.setName(newClassName);
    rootElement.mergeAddClass(oldClassElement);

    fireChange();
  }

  @Override
  public void fireChange() {
    fireChangeCodeAnalyze();
  }

  private void fireChangeCodeAnalyze() {
    Editor textEditor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();
    if (textEditor == null) {
      return;
    }
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(textEditor.getDocument());
    if (file == null) {
      return;
    }

    PsiManager.getInstance(myProject).getModificationTracker().incCounter();
    DaemonCodeAnalyzer.getInstance(myProject).restart();
  }

  @Override
  @Nullable
  public DPropertyElement findConcreteDynamicProperty(String containingClassName, String propertyName) {
    return findConcreteDynamicProperty(getRootElement(), containingClassName, propertyName);
  }

  @Nullable
  private static DPropertyElement findConcreteDynamicProperty(DRootElement rootElement,
                                                              String conatainingClassName,
                                                              String propertyName) {
    DClassElement classElement = rootElement.getClassElement(conatainingClassName);

    if (classElement == null) {
      return null;
    }

    return classElement.getPropertyByName(propertyName);
  }

  @Nullable
  private static DClassElement findClassElement(DRootElement rootElement, String conatainingClassName) {
    return rootElement.getClassElement(conatainingClassName);
  }

  /**
   * On exit
   */
  @Override
  public DRootElement getState() {
    //    return XmlSerializer.serialize(myRootElement);
    return myRootElement;
  }

  /*
   * On loading
   */
  @Override
  public void loadState(DRootElement element) {
    //    myRootElement = XmlSerializer.deserialize(element, myRootElement.getClass());
    myRootElement = element;
  }

  @Override
  public DItemElement createDynamicElement(DynamicElementSettings settings) {
    DItemElement itemElement;
    if (settings.isMethod()) {
      itemElement = new DMethodElement(settings.isStatic(), settings.getName(), settings.getType(), settings.getParams());
    }
    else {
      itemElement = new DPropertyElement(settings.isStatic(), settings.getName(), settings.getType());
    }
    return itemElement;
  }
}
