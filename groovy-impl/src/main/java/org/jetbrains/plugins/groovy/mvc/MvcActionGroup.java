package org.jetbrains.plugins.groovy.mvc;

import consulo.module.Module;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.action.Presentation;
import consulo.module.Module;
import consulo.application.dumb.DumbAware;
import consulo.util.lang.Pair;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.util.lang.Pair;

public class MvcActionGroup extends DefaultActionGroup implements DumbAware {

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();

    Pair<MvcFramework, Module> pair = MvcActionBase.guessFramework(e);

    if (pair != null) {
      presentation.setVisible(true);
      presentation.setText(pair.getFirst().getDisplayName());
      presentation.setIcon(pair.getFirst().getIcon());
    }
    else {
      presentation.setVisible(false);
    }
  }
}
