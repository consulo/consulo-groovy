package org.jetbrains.plugins.groovy.griffon.module.extension;

import javax.annotation.Nonnull;

import consulo.extension.impl.ModuleExtensionImpl;
import consulo.roots.ModuleRootLayer;

/**
 * @author VISTALL
 * @since 14:33/30.06.13
 */
public class GriffonModuleExtension extends ModuleExtensionImpl<GriffonModuleExtension>
{
	public GriffonModuleExtension(@Nonnull String id, @Nonnull ModuleRootLayer module)
	{
		super(id, module);
	}
}
