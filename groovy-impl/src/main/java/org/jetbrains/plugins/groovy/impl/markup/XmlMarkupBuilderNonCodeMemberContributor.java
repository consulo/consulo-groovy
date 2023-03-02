package org.jetbrains.plugins.groovy.impl.markup;

import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Sergey Evdokimov
 */
@ExtensionImpl
public class XmlMarkupBuilderNonCodeMemberContributor extends NonCodeMembersContributor {

  @Nullable
  @Override
  protected String getParentClassName() {
    return "groovy.xml.MarkupBuilder";
  }

  @Override
  public void processDynamicElements(@Nonnull PsiType qualifierType,
                                     PsiClass aClass,
                                     PsiScopeProcessor processor,
                                     PsiElement place,
                                     ResolveState state) {
    String nameHint = ResolveUtil.getNameHint(processor);

    if (nameHint == null) return;

    ClassHint classHint = processor.getHint(ClassHint.KEY);
    if (classHint != null && !classHint.shouldProcess(ClassHint.ResolveKind.METHOD)) return;

    GrLightMethodBuilder res = new GrLightMethodBuilder(aClass.getManager(), nameHint);
    res.addParameter("attrs", CommonClassNames.JAVA_UTIL_MAP, false);
    res.addParameter("content", CommonClassNames.JAVA_LANG_OBJECT, true);
    res.setOriginInfo("XML tag");

    if (!processor.execute(res, state)) return;

    res = new GrLightMethodBuilder(aClass.getManager(), nameHint);
    res.addParameter("content", CommonClassNames.JAVA_LANG_OBJECT, true);
    res.setOriginInfo("XML tag");

    processor.execute(res, state);
  }
}
