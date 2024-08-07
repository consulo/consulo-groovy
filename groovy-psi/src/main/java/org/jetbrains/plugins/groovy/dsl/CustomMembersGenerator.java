/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiType;
import consulo.language.impl.psi.FakePsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MetaMethod;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.jetbrains.plugins.groovy.dsl.dsltop.GdslMembersProvider;
import org.jetbrains.plugins.groovy.dsl.holders.CompoundMembersHolder;
import org.jetbrains.plugins.groovy.dsl.holders.CustomMembersHolder;
import org.jetbrains.plugins.groovy.dsl.holders.DeclarationType;
import org.jetbrains.plugins.groovy.dsl.holders.NonCodeMembersHolder;
import org.jetbrains.plugins.groovy.dsl.toplevel.ClassContextFilter;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;
import java.util.function.Function;

/**
 * @author peter
 */
public class CustomMembersGenerator extends GroovyObjectSupport implements GdslMembersHolderConsumer {
  private static final Logger LOG = Logger.getInstance(CustomMembersGenerator.class);
  public static final String THROWS = "throws";
  private final List<Map> myDeclarations = ContainerUtil.newArrayList();
  private final Project myProject;
  private final CompoundMembersHolder myDepot = new CompoundMembersHolder();
  private final GroovyClassDescriptor myDescriptor;
  @Nullable
  private final Map<String, List> myBindings;
  private final PsiClass myPsiClass;

  public CustomMembersGenerator(@Nonnull GroovyClassDescriptor descriptor, @Nullable PsiType type, @Nullable Map<String, List> bindings) {
    myDescriptor = descriptor;
    myBindings = bindings;
    myProject = descriptor.getProject();
    myPsiClass = type instanceof PsiClassType ? ((PsiClassType)type).resolve() : null;
  }

  public PsiElement getPlace() {
    return myDescriptor.getPlace();
  }

  @Nullable
  public PsiClass getClassType() {
    return getPsiClass();
  }

  public PsiType getPsiType() {
    return myDescriptor.getPsiType();
  }

  @Nullable
  @Override
  public PsiClass getPsiClass() {
    return myPsiClass;
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return myDescriptor.getResolveScope();
  }

  public Project getProject() {
    return myProject;
  }

  @Nullable
  public CustomMembersHolder getMembersHolder() {
    if (!myDeclarations.isEmpty()) {
      addMemberHolder(new CustomMembersHolder() {
        @Override
        public boolean processMembers(GroovyClassDescriptor descriptor, PsiScopeProcessor processor, ResolveState state) {
          return NonCodeMembersHolder.generateMembers(myDeclarations, descriptor.justGetPlaceFile()).processMembers(descriptor, processor, state);
        }
      });
    }
    return myDepot;
  }

  public void addMemberHolder(CustomMembersHolder holder) {
    myDepot.addHolder(holder);
  }

  private Object[] constructNewArgs(Object[] args) {
    final Object[] newArgs = new Object[args.length + 1];
    //noinspection ManualArrayCopy
    for (int i = 0; i < args.length; i++) {
      newArgs[i] = args[i];
    }
    newArgs[args.length] = this;
    return newArgs;
  }


  /** **********************************************************
   Methods to add new behavior
   *********************************************************** */
  public void property(Map<Object, Object> args) {
    String name = (String)args.get("name");
    Object type = args.get("type");
    Object doc = args.get("doc");
    Object docUrl = args.get("docUrl");
    Boolean isStatic = (Boolean)args.get("isStatic");

    Map<Object, Object> getter = new HashMap<Object, Object>();
    getter.put("name", GroovyPropertyUtils.getGetterNameNonBoolean(name));
    getter.put("type", type);
    getter.put("isStatic", isStatic);
    getter.put("doc", doc);
    getter.put("docUrl", docUrl);
    method(getter);

    Map<Object, Object> setter = new HashMap<Object, Object>();
    setter.put("name", GroovyPropertyUtils.getSetterName(name));
    setter.put("type", "void");
    setter.put("isStatic", isStatic);
    setter.put("doc", doc);
    setter.put("docUrl", docUrl);
    final HashMap<Object, Object> param = new HashMap<Object, Object>();
    param.put(name, type);
    setter.put("params", param);
    method(setter);
  }

  public void constructor(Map<Object, Object> args) {
    args.put("constructor", true);
    method(args);
  }

  @SuppressWarnings("MethodMayBeStatic")
  public ParameterDescriptor parameter(Map args) {
    return new ParameterDescriptor(args, myDescriptor.justGetPlaceFile());
  }

  public void method(Map<Object, Object> args) {
    parseMethod(args);
    args.put("declarationType", DeclarationType.METHOD);
    myDeclarations.add(args);
  }

  public void methodCall(Closure<Map<Object, Object>> generator) {
    PsiElement place = myDescriptor.getPlace();

    PsiElement parent = place.getParent();
    if (isMethodCall(place, parent)) {
      assert parent instanceof GrMethodCall && place instanceof GrReferenceExpression;

      GrMethodCall call = (GrMethodCall)parent;
      GrReferenceExpression ref = (GrReferenceExpression)place;

      PsiType[] argTypes =
        PsiUtil.getArgumentTypes(call.getNamedArguments(), call.getExpressionArguments(), call.getClosureArguments(), false, null, false);
      if (argTypes == null) return;

      String[] types = new String[argTypes.length];
      ContainerUtil.map(argTypes, (Function<PsiType, Object>)type -> {
        String canonical = type.getCanonicalText();
        return canonical != null ? canonical : type.getPresentableText();
      }, types);

      generator.setDelegate(this);

      HashMap<String, Object> args = new HashMap<String, Object>();
      args.put("name", ref.getReferenceName());
      args.put("argumentTypes", types);
      generator.call(args);
    }
  }

  private static boolean isMethodCall(PsiElement place, PsiElement parent) {
    return place instanceof GrReferenceExpression &&
        parent instanceof GrMethodCall &&
        ((GrMethodCall)parent).getInvokedExpression() == place;
  }

  @SuppressWarnings("unchecked")
  private static void parseMethod(Map args) {
    String type = stringifyType(args.get("type"));
    args.put("type", type);

    Object namedParams = args.get("namedParams");
    if (namedParams instanceof List) {
      LinkedHashMap newParams = new LinkedHashMap();
      newParams.put("args", namedParams);
      Object oldParams = args.get("params");
      if (oldParams instanceof Map) {
        newParams.putAll((Map)oldParams);
      }
      args.put("params", newParams);
    }

    //noinspection unchecked
    Object params = args.get("params");
    if (params instanceof Map) {
      boolean first = true;
      for (Map.Entry<Object, Object> entry : ((Map<Object, Object>)params).entrySet()) {
        Object value = entry.getValue();
        if (!first || !(value instanceof List)) {
          entry.setValue(stringifyType(value));
        }
        first = false;
      }
    }
    final Object toThrow = args.get(THROWS);
    if (toThrow instanceof List) {
      final ArrayList<String> list = new ArrayList<String>();
      for (Object o : (List)toThrow) {
        list.add(stringifyType(o));
      }
      args.put(THROWS, list);
    }
    else if (toThrow != null) {
      args.put(THROWS, Collections.singletonList(stringifyType(toThrow)));
    }

  }

  @SuppressWarnings("UnusedDeclaration")
  public void closureInMethod(Map<Object, Object> args) {
    parseMethod(args);
    final Object method = args.get("method");
    if (method instanceof Map) {
      parseMethod((Map)method);
    }
    args.put("declarationType", DeclarationType.CLOSURE);
    myDeclarations.add(args);
  }

  public void variable(Map<Object, Object> args) {
    parseVariable(args);
    myDeclarations.add(args);
  }

  private void parseVariable(Map<Object, Object> args) {
    String type = stringifyType(args.get("type"));
    args.put("type", type);
    args.put("declarationType", DeclarationType.VARIABLE);
  }

  private static String stringifyType(Object type) {
    if (type == null) return CommonClassNames.JAVA_LANG_OBJECT;
    if (type instanceof Closure) return GroovyCommonClassNames.GROOVY_LANG_CLOSURE;
    if (type instanceof Map) return CommonClassNames.JAVA_UTIL_MAP;
    if (type instanceof Class) return ((Class)type).getName();

    String s = type.toString();
    LOG.assertTrue(!s.startsWith("? extends"), s);
    LOG.assertTrue(!s.contains("?extends"), s);
    LOG.assertTrue(!s.contains("<null."), s);
    LOG.assertTrue(!s.startsWith("null."), s);
    LOG.assertTrue(!(s.contains(",") && !s.contains("<")), s);
    return s;
  }

  @SuppressWarnings("UnusedDeclaration")
  @Nullable
  public Object methodMissing(String name, Object args) {
    final Object[] newArgs = constructNewArgs((Object[])args);

    // Get other DSL methods from extensions
    for (GdslMembersProvider provider : GdslMembersProvider.EP_NAME.getExtensionList()) {
      final List<MetaMethod> variants = DefaultGroovyMethods.getMetaClass(provider).respondsTo(provider, name, newArgs);
      if (variants.size() == 1) {
        return InvokerHelper.invokeMethod(provider, name, newArgs);
      }
    }
    return null;
  }

  @SuppressWarnings("UnusedDeclaration")
  @Nullable
  public Object propertyMissing(String name) {
    if (myBindings != null) {
      final List list = myBindings.get(name);
      if (list != null) {
        return list;
      }
    }

    return null;
  }

  public static class ParameterDescriptor {
    public final String name;
    public final NamedArgumentDescriptor descriptor;

    private ParameterDescriptor(Map args, PsiElement context) {
      name = (String)args.get("name");
      final String typeText = stringifyType(args.get("type"));
      Object doc = args.get("doc");
      descriptor = new NamedArgumentDescriptor(new GdslNamedParameter(name, doc instanceof String ? (String)doc : null, context, typeText)) {
        @Override
        public boolean checkType(@Nonnull PsiType type, @Nonnull GroovyPsiElement context) {
          return typeText == null || ClassContextFilter.isSubtype(type, context.getContainingFile(), typeText);
        }
      };
      descriptor.setPriority(NamedArgumentDescriptor.Priority.ALWAYS_ON_TOP);
    }

  }
  
  public static class GdslNamedParameter extends FakePsiElement
  {
    private final String myName;
    public final String docString;
    private final PsiElement myParent;
    @Nullable
	public final String myParameterTypeText;

    public GdslNamedParameter(String name, String doc, @Nonnull PsiElement parent, @Nullable String type) {
      myName = name;
      this.docString = doc;
      myParent = parent;
      myParameterTypeText = type;
    }

    @Override
    public PsiElement getParent() {
      return myParent;
    }

    @Override
    public String getName() {
      return myName;
    }
  }

}
