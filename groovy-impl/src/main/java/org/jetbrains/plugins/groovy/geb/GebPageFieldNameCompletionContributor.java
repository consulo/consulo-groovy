package org.jetbrains.plugins.groovy.geb;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiModifier;
import consulo.language.Language;
import consulo.language.editor.completion.*;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.completion.lookup.TailType;
import consulo.language.editor.completion.lookup.TailTypeDecorator;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;
import consulo.util.lang.Pair;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.util.FieldInitializerTailTypes;

import javax.annotation.Nonnull;

import static com.intellij.java.language.patterns.PsiJavaPatterns.psiClass;
import static com.intellij.java.language.patterns.PsiJavaPatterns.psiField;
import static consulo.language.pattern.PlatformPatterns.psiElement;

/**
 * @author Sergey Evdokimov
 */
public class GebPageFieldNameCompletionContributor extends CompletionContributor {

  public GebPageFieldNameCompletionContributor() {
    extend(CompletionType.BASIC, psiElement(GroovyTokenTypes.mIDENT).withParent(
      psiField().withModifiers(PsiModifier.STATIC).inClass(psiClass().inheritorOf(true, "geb.Page"))), new GebCompletionProvider());
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }

  private static class GebCompletionProvider implements CompletionProvider
  {

    @SuppressWarnings("unchecked")
    private static Pair<String, TailType>[] VARIANTS = new Pair[]{
      Pair.create("url", FieldInitializerTailTypes.EQ_STRING),
      Pair.create("content", FieldInitializerTailTypes.EQ_CLOSURE),
      Pair.create("at", FieldInitializerTailTypes.EQ_CLOSURE),
    };

    @Override
    public void addCompletions(@Nonnull CompletionParameters parameters,
                                  ProcessingContext context,
                                  @Nonnull CompletionResultSet result) {
      final PsiClass psiClass = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiClass.class);
      assert psiClass != null;

      for (Pair<String, TailType> trinity : VARIANTS) {
        String fieldName = trinity.first;

        if (psiClass.findFieldByName(fieldName, false) == null) {
          result.addElement(TailTypeDecorator.withTail(LookupElementBuilder.create(fieldName), trinity.second));
        }
      }
    }
  }
}
