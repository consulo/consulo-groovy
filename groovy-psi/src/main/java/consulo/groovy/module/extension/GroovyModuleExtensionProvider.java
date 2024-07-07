package consulo.groovy.module.extension;

import consulo.annotation.component.ExtensionImpl;
import consulo.groovy.psi.icon.GroovyPsiIconGroup;
import consulo.localize.LocalizeValue;
import consulo.module.content.layer.ModuleExtensionProvider;
import consulo.module.content.layer.ModuleRootLayer;
import consulo.module.extension.ModuleExtension;
import consulo.module.extension.MutableModuleExtension;
import consulo.ui.image.Image;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 03/03/2023
 */
@ExtensionImpl
public class GroovyModuleExtensionProvider implements ModuleExtensionProvider<GroovyModuleExtension> {
  @Nonnull
  @Override
  public String getId() {
    return "groovy";
  }

  @Nullable
  @Override
  public String getParentId() {
    return "java";
  }

  @Nonnull
  @Override
  public LocalizeValue getName() {
    return LocalizeValue.localizeTODO("Groovy");
  }

  @Nonnull
  @Override
  public Image getIcon() {
    return GroovyPsiIconGroup.groovyGroovy_16x16();
  }

  @Nonnull
  @Override
  public ModuleExtension<GroovyModuleExtension> createImmutableExtension(@Nonnull ModuleRootLayer moduleRootLayer) {
    return new GroovyModuleExtension(getId(), moduleRootLayer);
  }

  @Nonnull
  @Override
  public MutableModuleExtension<GroovyModuleExtension> createMutableExtension(@Nonnull ModuleRootLayer moduleRootLayer) {
    return new GroovyMutableModuleExtension(getId(), moduleRootLayer);
  }
}
