package org.jetbrains.plugins.groovy.impl.testIntegration;

import com.intellij.java.impl.testIntegration.JavaTestCreator;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import org.jetbrains.plugins.groovy.GroovyLanguage;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 25/02/2023
 */
@ExtensionImpl
public class GroovyTestCreator extends JavaTestCreator {
  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }
}
