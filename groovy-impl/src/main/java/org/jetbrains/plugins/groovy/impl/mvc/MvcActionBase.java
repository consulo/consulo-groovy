package org.jetbrains.plugins.groovy.impl.mvc;

import consulo.language.editor.LangDataKeys;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.Project;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.util.lang.Pair;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public abstract class MvcActionBase extends DumbAwareAction {

  protected abstract void actionPerformed(@Nonnull AnActionEvent e, @Nonnull Module module, @Nonnull MvcFramework framework);
  
  @Override
  public final void actionPerformed(AnActionEvent e) {
    Pair<MvcFramework, Module> pair = guessFramework(e);
    if (pair != null && isFrameworkSupported(pair.getFirst())) {
      actionPerformed(e, pair.getSecond(), pair.getFirst());
    }
  }

  protected boolean isFrameworkSupported(@Nonnull MvcFramework framework) {
    return true;
  }

  @Nullable
  public static Pair<MvcFramework, Module> guessFramework(AnActionEvent event) {
    Module module = event.getData(event.getPlace().equals(ActionPlaces.MAIN_MENU) ? LangDataKeys.MODULE : LangDataKeys.MODULE_CONTEXT);

    if (module != null) {
      MvcFramework commonPluginModuleFramework = MvcFramework.findCommonPluginModuleFramework(module);

      if (commonPluginModuleFramework != null) {
        for (Module mod : ModuleManager.getInstance(module.getProject()).getModules()) {
          if (commonPluginModuleFramework.getCommonPluginsModuleName(mod).equals(module.getName())) {
            if (commonPluginModuleFramework.hasSupport(mod)) {
              return new Pair<MvcFramework, Module>(commonPluginModuleFramework, mod);
            }

            return null;
          }
        }
      }

      MvcFramework framework = MvcFramework.getInstance(module);
      if (framework != null) {
        return Pair.create(framework, module);
      }
    }

    Project project = event.getData(Project.KEY);
    if (project == null) {
      return null;
    }

    Pair<MvcFramework, Module> result = null;
    for (Module mod : ModuleManager.getInstance(project).getModules()) {
      MvcFramework framework = MvcFramework.getInstance(mod);
      if (framework != null) {
        if (result != null) {
          return null;
        }
        result = Pair.create(framework, mod);
      }
    }

    return result;
  }

  public final void update(AnActionEvent event) {
    Pair<MvcFramework, Module> pair = guessFramework(event);
    if (pair != null && isFrameworkSupported(pair.getFirst())) {
      event.getPresentation().setVisible(true);
      updateView(event, pair.getFirst(), pair.getSecond());
    }
    else {
      event.getPresentation().setVisible(false);
    }
  }

  protected void updateView(AnActionEvent event, @Nonnull MvcFramework framework, @Nonnull Module module) {

  }
}
