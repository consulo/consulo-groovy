package consulo.groovy.module.extension;

import consulo.module.content.layer.ModuleRootLayer;
import consulo.module.content.layer.extension.ModuleExtensionBase;
import consulo.module.extension.ModuleExtension;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 14:47/28.05.13
 */
public class GroovyModuleExtension extends ModuleExtensionBase<GroovyModuleExtension> implements ModuleExtension<GroovyModuleExtension> {
  public GroovyModuleExtension(@Nonnull String id, @Nonnull ModuleRootLayer module) {
    super(id, module);
  }
}
