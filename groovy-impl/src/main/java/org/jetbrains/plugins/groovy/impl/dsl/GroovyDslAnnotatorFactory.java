package org.jetbrains.plugins.groovy.impl.dsl;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.language.Language;
import consulo.language.editor.annotation.Annotator;
import consulo.language.editor.annotation.AnnotatorFactory;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;

/**
 * @author VISTALL
 * @since 08/07/2023
 */
@ExtensionImpl
public class GroovyDslAnnotatorFactory implements AnnotatorFactory, DumbAware {
  @Nullable
  @Override
  public Annotator createAnnotator() {
    return new GroovyDslAnnotator();
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }
}
