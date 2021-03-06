package org.jetbrains.plugins.groovy.gant;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrBuilderMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.scope.PsiScopeProcessor;
import consulo.awt.TargetAWT;
import icons.JetgroovyIcons;

/**
* @author peter
*/
class AntBuilderMethod extends LightMethodBuilder implements GrBuilderMethod {
  private final PsiFile myPlace;
  @Nullable private final Class myAntClass;

  public AntBuilderMethod(PsiFile place, String name, PsiType closureType, @Nullable Class antClass, final PsiType stringType) {
    super(place.getManager(), GroovyFileType.GROOVY_LANGUAGE, name);
    myPlace = place;
    myAntClass = antClass;
    setModifiers(PsiModifier.PUBLIC);
    addParameter("args", GrMapType.create(place.getResolveScope()));
    setBaseIcon(TargetAWT.to(JetgroovyIcons.Groovy.Ant_task));
    addParameter(new GrLightParameter("singleArg", stringType, this).setOptional(true));
    addParameter(new GrLightParameter("body", closureType, this).setOptional(true));
    setMethodReturnType(new Computable<PsiType>() {
      @Override
      public PsiType compute() {
        return PsiType.getJavaLangObject(getManager(), getResolveScope());
      }
    });
  }

  @Nonnull
  @Override
  public PsiElement getNavigationElement() {
    if (myAntClass != null) {
      final PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass(myAntClass.getName(), myPlace.getResolveScope());
      if (psiClass != null) {
        return psiClass;
      }
    }
    return this;
  }

  public boolean processNestedElements(PsiScopeProcessor processor) {
    /*TODO [VISTALL]final AntIntrospector introspector = AntDomExtender.getIntrospector(myAntClass);
    if (introspector != null) {
      String expectedName = ResolveUtil.getNameHint(processor);
      final PsiType stringType = getParameterList().getParameters()[1].getType();
      final PsiType closureType = getParameterList().getParameters()[2].getType();

      for (String name : Collections.list(introspector.getNestedElements())) {
        if (expectedName == null || expectedName.equals(name)) {
          final AntBuilderMethod method = new AntBuilderMethod(myPlace, name, closureType, introspector.getElementType(name),
                                                               stringType);
          if (!processor.execute(method, ResolveState.initial())) return false;
        }
      }
    }    */
    return true;
  }

  @Override
  public boolean hasObligatoryNamedArguments() {
    return false;
  }
}
