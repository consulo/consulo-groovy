package org.jetbrains.plugins.groovy.impl.actions.generate.accessors;

import com.intellij.java.impl.codeInsight.generation.GenerateAccessorProvider;
import com.intellij.java.language.impl.codeInsight.generation.EncapsulatableClassMember;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiEnumConstant;
import com.intellij.java.language.psi.PsiField;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author VISTALL
 * @since 18/02/2023
 */
@ExtensionImpl
public class GroovyGenerateAccessorProvider implements GenerateAccessorProvider {
  @RequiredReadAction
  @Nonnull
  @Override
  public Collection<EncapsulatableClassMember> getEncapsulatableClassMembers(PsiClass s) {
    if (!(s instanceof GrTypeDefinition)) return Collections.emptyList();
    final List<EncapsulatableClassMember> result = new ArrayList<EncapsulatableClassMember>();
    for (PsiField field : s.getFields()) {
      if (!(field instanceof PsiEnumConstant) && field instanceof GrField) {
        result.add(new GrFieldMember(field));
      }
    }
    return result;
  }
}
