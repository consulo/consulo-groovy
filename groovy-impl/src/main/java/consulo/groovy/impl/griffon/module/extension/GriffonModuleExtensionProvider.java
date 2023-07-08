package consulo.groovy.impl.griffon.module.extension;

import consulo.annotation.component.ExtensionImpl;
import consulo.groovy.localize.GroovyLocalize;
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
 * @since 08/07/2023
 */
@ExtensionImpl
public class GriffonModuleExtensionProvider implements ModuleExtensionProvider<GriffonModuleExtension> {
  @Nonnull
  @Override
  public String getId() {
    return "griffon";
  }

  @Nullable
  @Override
  public String getParentId() {
    return "groovy";
  }

  @Nonnull
  @Override
  public LocalizeValue getName() {
    return GroovyLocalize.griffonConfigurationName();
  }

  @Nonnull
  @Override
  public Image getIcon() {
    return GroovyPsiIconGroup.griffonGriffon();
  }

  @Nonnull
  @Override
  public ModuleExtension<GriffonModuleExtension> createImmutableExtension(@Nonnull ModuleRootLayer moduleRootLayer) {
    return new GriffonModuleExtension(getId(), moduleRootLayer);
  }

  @Nonnull
  @Override
  public MutableModuleExtension<GriffonModuleExtension> createMutableExtension(@Nonnull ModuleRootLayer moduleRootLayer) {
    return new GriffonMutableModuleExtension(getId(), moduleRootLayer);
  }
}
