package org.jetbrains.plugins.groovy.impl.extensions;

import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.completion.lookup.PrioritizedLookupElement;
import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collection;

/**
 * @author Sergey Evdokimov
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class GroovyMapContentProvider {

  public static final ExtensionPointName<GroovyMapContentProvider> EP_NAME = ExtensionPointName.create(GroovyMapContentProvider.class);

  public void addKeyVariants(@Nonnull GrExpression qualifier, @Nullable PsiElement resolve, @Nonnull CompletionResultSet result) {
    for (String key : getKeyVariants(qualifier, resolve)) {
      LookupElement lookup = LookupElementBuilder.create(key);
      lookup = PrioritizedLookupElement.withPriority(lookup, 1);
      result.addElement(lookup);
    }
  }

  protected Collection<String> getKeyVariants(@Nonnull GrExpression qualifier, @Nullable PsiElement resolve) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public PsiType getValueType(@Nonnull GrExpression qualifier, @Nullable PsiElement resolve, @Nonnull String key) {
    return null;
  }

}
