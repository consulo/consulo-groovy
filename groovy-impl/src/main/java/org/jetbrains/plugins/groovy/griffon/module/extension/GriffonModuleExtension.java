package org.jetbrains.plugins.groovy.griffon.module.extension;

import org.jetbrains.annotations.NotNull;
import consulo.extension.impl.ModuleExtensionImpl;
import consulo.roots.ModuleRootLayer;

/**
 * @author VISTALL
 * @since 14:33/30.06.13
 */
public class GriffonModuleExtension extends ModuleExtensionImpl<GriffonModuleExtension>
{
	public GriffonModuleExtension(@NotNull String id, @NotNull ModuleRootLayer module)
	{
		super(id, module);
	}
}
