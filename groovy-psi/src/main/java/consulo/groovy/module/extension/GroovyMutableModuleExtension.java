package consulo.groovy.module.extension;

import consulo.disposer.Disposable;
import consulo.module.content.layer.ModuleRootLayer;
import consulo.module.extension.MutableModuleExtension;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 14:47/28.05.13
 */
public class GroovyMutableModuleExtension extends GroovyModuleExtension implements MutableModuleExtension<GroovyModuleExtension> {
  public GroovyMutableModuleExtension(@Nonnull String id, @Nonnull ModuleRootLayer module) {
    super(id, module);
  }

  @RequiredUIAccess
  @Nullable
  @Override
  public Component createConfigurationComponent(@Nonnull Disposable disposable, @Nonnull Runnable runnable) {
    return null;
  }

  @Override
  public void setEnabled(boolean val) {
    myIsEnabled = val;
  }

  @Override
  public boolean isModified(@Nonnull GroovyModuleExtension extension) {
    return myIsEnabled != extension.isEnabled();
  }
}
