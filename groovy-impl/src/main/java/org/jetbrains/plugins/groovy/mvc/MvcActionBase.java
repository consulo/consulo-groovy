package org.jetbrains.plugins.groovy.mvc;

import javax.annotation.Nonnull;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;

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

  @javax.annotation.Nullable
  public static Pair<MvcFramework, Module> guessFramework(AnActionEvent event) {
    final Module module = event.getData(event.getPlace().equals(ActionPlaces.MAIN_MENU) ? LangDataKeys.MODULE : LangDataKeys.MODULE_CONTEXT);

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

    final Project project = event.getProject();
    if (project == null) {
      return null;
    }

    Pair<MvcFramework, Module> result = null;
    for (Module mod : ModuleManager.getInstance(project).getModules()) {
      final MvcFramework framework = MvcFramework.getInstance(mod);
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
