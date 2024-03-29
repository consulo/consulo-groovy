package org.jetbrains.plugins.groovy.dsl;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiWildcardType;
import consulo.util.lang.StringUtil;

/**
 * @author peter
 */
public class GdslType {
  public final PsiType psiType;

  public GdslType(PsiType psiType) {
    this.psiType = psiType;
  }

  public String getShortName() {
    return StringUtil.getShortName(getName());
  }

  public String getName() {
    PsiType type = psiType;
    if (type instanceof PsiWildcardType) {
      type = ((PsiWildcardType)type).getBound();
    }
    if (type instanceof PsiClassType) {
      final PsiClass resolve = ((PsiClassType)type).resolve();
      if (resolve != null) {
        return resolve.getName();
      }
      final String canonicalText = type.getCanonicalText();
      final int i = canonicalText.indexOf('<');
      if (i < 0) return canonicalText;
      return canonicalText.substring(0, i);
    }

    if (type == null) {
      return "";
    }

    return type.getCanonicalText();
  }

}
