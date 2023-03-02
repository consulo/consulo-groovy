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

import consulo.ui.ex.awt.JBCheckBox;

import javax.swing.*;

/**
 * Created by Max Medvedev on 8/29/13
 */
public class GrInplaceIntroduceConstantPanel {
  private JBCheckBox myMoveToAnotherClassJBCheckBox;
  private JPanel myRootPane;

  public boolean isMoveToAnotherClass() {
    return myMoveToAnotherClassJBCheckBox.isSelected();
  }

  public JComponent getRootPane() {
    return myRootPane;
  }
}
