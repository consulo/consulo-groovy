package org.jetbrains.plugins.groovy.impl.springloaded;

import com.intellij.java.debugger.NoDataException;
import com.intellij.java.debugger.PositionManager;
import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.engine.DebugProcess;
import com.intellij.java.debugger.requests.ClassPrepareRequestor;
import com.intellij.java.language.impl.psi.impl.source.PsiClassImpl;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaFile;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.AccessRule;
import consulo.internal.com.sun.jdi.AbsentInformationException;
import consulo.internal.com.sun.jdi.Location;
import consulo.internal.com.sun.jdi.ReferenceType;
import consulo.internal.com.sun.jdi.request.ClassPrepareRequest;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Position manager to debug classes reloaded by org.springsource.springloaded
 *
 * @author Sergey Evdokimov
 */
public class SpringLoadedPositionManager implements PositionManager {
  private static final Pattern GENERATED_CLASS_NAME = Pattern.compile("\\$\\$[A-Za-z0-9]{8}");

  private final DebugProcess myDebugProcess;

  public SpringLoadedPositionManager(DebugProcess debugProcess) {
    myDebugProcess = debugProcess;
  }

  @Override
  public SourcePosition getSourcePosition(@Nullable Location location) throws NoDataException {
    throw NoDataException.INSTANCE;
  }

  @Nonnull
  @Override
  public List<ReferenceType> getAllClasses(@Nonnull final SourcePosition classPosition) throws NoDataException {
    String className = findEnclosingName(classPosition);
    if (className == null) {
      throw NoDataException.INSTANCE;
    }

    List<ReferenceType> referenceTypes = myDebugProcess.getVirtualMachineProxy().classesByName(className);
    if (referenceTypes.isEmpty()) {
      throw NoDataException.INSTANCE;
    }

    Set<ReferenceType> res = new HashSet<>();

    for (ReferenceType referenceType : referenceTypes) {
      findNested(res, referenceType, classPosition);
    }

    if (res.isEmpty()) {
      throw NoDataException.INSTANCE;
    }

    return new ArrayList<>(res);
  }

  @Nonnull
  @Override
  public List<Location> locationsOfLine(@Nonnull ReferenceType type, @Nonnull SourcePosition position) throws NoDataException {
    throw NoDataException.INSTANCE;
  }

  @Nullable
  private static String findEnclosingName(final SourcePosition position) {
    return AccessRule.read(() ->
                           {
                             PsiElement element = findElementAt(position);
                             while (true) {
                               element = PsiTreeUtil.getParentOfType(element, GrTypeDefinition.class, PsiClassImpl.class);
                               if (element == null || (element instanceof GrTypeDefinition && !((GrTypeDefinition)element).isAnonymous()) || (element instanceof PsiClassImpl && ((PsiClassImpl)element)
                                 .getName() != null)) {
                                 break;
                               }
                             }

                             if (element != null) {
                               return getClassNameForJvm((PsiClass)element);
                             }
                             return null;
                           });
  }

  @Nullable
  private static String getClassNameForJvm(final PsiClass aClass) {
    final PsiClass psiClass = aClass.getContainingClass();
    if (psiClass != null) {
      return getClassNameForJvm(psiClass) + "$" + aClass.getName();
    }

    return aClass.getQualifiedName();
  }

  @Nullable
  private static String getOuterClassName(final SourcePosition position) {
    return AccessRule.read(() ->
                           {
                             PsiElement element = findElementAt(position);
                             if (element == null) {
                               return null;
                             }
                             PsiElement sourceImage =
                               PsiTreeUtil.getParentOfType(element, GrClosableBlock.class, GrTypeDefinition.class, PsiClassImpl.class);

                             if (sourceImage instanceof PsiClass) {
                               return getClassNameForJvm((PsiClass)sourceImage);
                             }
                             return null;
                           });
  }

  @Nullable
  @RequiredReadAction
  private static PsiElement findElementAt(SourcePosition position) {
    PsiFile file = position.getFile();
    if (!(file instanceof GroovyFileBase) && !(file instanceof PsiJavaFile)) {
      return null;
    }
    return file.findElementAt(position.getOffset());
  }

  @Override
  public ClassPrepareRequest createPrepareRequest(@Nonnull ClassPrepareRequestor requestor,
                                                  @Nonnull SourcePosition position) throws NoDataException {
    String className = getOuterClassName(position);
    if (className == null) {
      throw NoDataException.INSTANCE;
    }

    return myDebugProcess.getRequestsManager().createClassPrepareRequest(requestor, className + "*");
  }

  private static boolean isSpringLoadedGeneratedClass(ReferenceType ownerClass, ReferenceType aClass) {
    String name = aClass.name();
    String ownerClassName = ownerClass.name();

    // return   name == ownerClassName + "$$" + /[A-Za-z0-9]{8}/
    return name.length() == ownerClassName.length() + 2 + 8 && name.startsWith(ownerClassName) && GENERATED_CLASS_NAME.matcher(name.substring(
      ownerClassName.length())).matches();
  }

  private static void findNested(Set<ReferenceType> res, ReferenceType fromClass, SourcePosition classPosition) {
    if (!fromClass.isPrepared()) {
      return;
    }

    List<ReferenceType> nestedTypes = fromClass.nestedTypes();

    ReferenceType springLoadedGeneratedClass = null;

    for (ReferenceType nested : nestedTypes) {
      if (!nested.isPrepared()) {
        continue;
      }

      if (isSpringLoadedGeneratedClass(fromClass, nested)) {
        if (springLoadedGeneratedClass == null || !springLoadedGeneratedClass.name().equals(nested.name())) {
          springLoadedGeneratedClass = nested; // Only latest generated classes should be used.
        }
      }
      else {
        findNested(res, nested, classPosition);
      }
    }

    try {
      final int lineNumber = classPosition.getLine() + 1;

      ReferenceType effectiveRef = springLoadedGeneratedClass == null ? fromClass : springLoadedGeneratedClass;

      if (effectiveRef.locationsOfLine(lineNumber).size() > 0) {
        res.add(effectiveRef);
      }
    }
    catch (AbsentInformationException ignored) {
    }
  }

}
