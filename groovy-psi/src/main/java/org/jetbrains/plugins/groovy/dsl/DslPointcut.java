/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;
import consulo.util.dataholder.Key;
import org.jetbrains.plugins.groovy.dsl.toplevel.ClassContextFilter;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ClassUtil;

import jakarta.annotation.Nullable;
import java.util.*;

/**
 * @author peter
 */
@SuppressWarnings("UnusedDeclaration")
public abstract class DslPointcut<T, V>
{
	public static final DslPointcut UNKNOWN = new DslPointcut()
	{

		@Override
		List matches(Object src, ProcessingContext context)
		{
			return Collections.emptyList();
		}

		@Override
		boolean operatesOn(Class c)
		{
			return true;
		}
	};
	public static Key<Map<String, List>> BOUND = Key.create("gdsl.bound");

	@Nullable
	abstract List<V> matches(T src, ProcessingContext context);

	abstract boolean operatesOn(Class c);

	public DslPointcut<T, V> and(final DslPointcut<T, V> next)
	{
		final DslPointcut<T, V> first = this;
		return new DslPointcut<T, V>()
		{
			@Override
			List<V> matches(T src, ProcessingContext context)
			{
				final List<V> vs1 = first.matches(src, context);
				if(vs1 == null)
				{
					return null;
				}

				final List<V> vs2 = next.matches(src, context);
				if(vs2 == null)
				{
					return null;
				}

				final List<V> result = new ArrayList<V>(vs1);
				result.retainAll(new HashSet<V>(vs2));
				return result;
			}

			@Override
			boolean operatesOn(Class c)
			{
				return first.operatesOn(c) && next.operatesOn(c);
			}
		};
	}

	public DslPointcut<T, V> or(final DslPointcut<T, V> next)
	{
		final DslPointcut<T, V> first = this;
		return new DslPointcut<T, V>()
		{
			@Override
			List<V> matches(T src, ProcessingContext context)
			{
				final List<V> vs1 = first.matches(src, context);
				final List<V> vs2 = next.matches(src, context);

				if(vs1 == null && vs2 == null)
				{
					return null;
				}

				final Set<V> result = new LinkedHashSet<V>();
				if(vs1 != null)
				{
					result.addAll(vs1);
				}
				if(vs2 != null)
				{
					result.addAll(vs2);
				}
				return new ArrayList<V>(result);
			}

			@Override
			boolean operatesOn(Class c)
			{
				return first.operatesOn(c) && next.operatesOn(c);
			}
		};
	}

	public DslPointcut<T, V> bitwiseNegate()
	{
		final DslPointcut<T, V> base = this;
		return new DslPointcut<T, V>()
		{
			@Override
			List<V> matches(T src, ProcessingContext context)
			{
				return base.matches(src, context) == null ? Collections.<V>emptyList() : null;
			}

			@Override
			boolean operatesOn(Class c)
			{
				return base.operatesOn(c);
			}
		};
	}


	public static DslPointcut<GdslType, GdslType> subType(final Object arg)
	{
		return new DslPointcut<GdslType, GdslType>()
		{

			@Override
			List<GdslType> matches(GdslType src, ProcessingContext context)
			{
				final PsiFile placeFile = context.get(GdslUtil.INITIAL_CONTEXT).getPlaceFile();
				if(ClassContextFilter.isSubtype(src.psiType, placeFile, (String) arg))
				{
					return Arrays.asList(src);
				}
				return null;
			}

			@Override
			boolean operatesOn(Class c)
			{
				return GdslType.class == c;
			}
		};

	}

	public static DslPointcut<GroovyClassDescriptor, GdslType> currentType(final Object arg)
	{
		final DslPointcut<GdslType, ?> inner;
		if(arg instanceof String)
		{
			inner = subType(arg);
		}
		else
		{
			inner = (DslPointcut<GdslType, ?>) arg;
			assert inner.operatesOn(GdslType.class) : "The argument to currentType should be a pointcut working with " +
					"types, e.g. subType";
		}

		return new DslPointcut<GroovyClassDescriptor, GdslType>()
		{

			@Override
			List<GdslType> matches(GroovyClassDescriptor src, ProcessingContext context)
			{
				final GdslType currentType = new GdslType(ClassUtil.findPsiType(src, context));
				if(inner.matches(currentType, context) != null)
				{
					return Arrays.asList(currentType);
				}
				return null;
			}

			@Override
			boolean operatesOn(Class c)
			{
				return GroovyClassDescriptor.class == c;
			}
		};
	}

	public static DslPointcut<GroovyClassDescriptor, GdslType> enclosingType(final Object arg)
	{
		return new DslPointcut<GroovyClassDescriptor, GdslType>()
		{
			@Override
			List<GdslType> matches(GroovyClassDescriptor src, ProcessingContext context)
			{
				List<GdslType> result = new ArrayList<GdslType>();
				PsiElement place = src.getPlace();
				while(true)
				{
					final PsiClass cls = PsiTreeUtil.getContextOfType(place, PsiClass.class);
					if(cls == null)
					{
						break;
					}
					if(arg.equals(cls.getQualifiedName()))
					{
						result.add(new GdslType(JavaPsiFacade.getElementFactory(cls.getProject()).createType(cls)));
					}
					place = cls;
				}
				return result.isEmpty() ? null : result;
			}

			@Override
			boolean operatesOn(Class c)
			{
				return GroovyClassDescriptor.class == c;
			}
		};
	}

	public static DslPointcut<Object, String> name(final Object arg)
	{
		return new DslPointcut<Object, String>()
		{
			@Override
			List<String> matches(Object src, ProcessingContext context)
			{
				if(src instanceof GdslType)
				{
					return arg.equals(((GdslType) src).getName()) ? Arrays.asList((String) arg) : null;
				}
				if(src instanceof GdslMethod)
				{
					return arg.equals(((GdslMethod) src).getName()) ? Arrays.asList((String) arg) : null;
				}
				return Collections.emptyList();
			}

			@Override
			boolean operatesOn(Class c)
			{
				return c == GdslType.class || c == GdslMethod.class;
			}
		};

	}

	public static DslPointcut<GroovyClassDescriptor, GdslMethod> enclosingMethod(final Object arg)
	{
		final DslPointcut<? super GdslMethod, ?> inner;
		if(arg instanceof String)
		{
			inner = name(arg);
		}
		else
		{
			inner = (DslPointcut<GdslMethod, ?>) arg;
			assert inner.operatesOn(GdslMethod.class) : "The argument to enclosingMethod should be a pointcut working " +
					"with methods, e.g. name";
		}

		return new DslPointcut<GroovyClassDescriptor, GdslMethod>()
		{
			@Override
			List<GdslMethod> matches(GroovyClassDescriptor src, ProcessingContext context)
			{
				List<GdslMethod> result = new ArrayList<GdslMethod>();
				PsiElement place = src.getPlace();
				while(true)
				{
					final PsiMethod method = PsiTreeUtil.getContextOfType(place, PsiMethod.class);
					if(method == null)
					{
						break;
					}
					final GdslMethod wrapper = new GdslMethod(method);
					if(inner.matches(wrapper, context) != null)
					{
						result.add(wrapper);
					}
					place = method;
				}
				return result.isEmpty() ? null : result;
			}

			@Override
			boolean operatesOn(Class c)
			{
				return GroovyClassDescriptor.class == c;
			}
		};
	}

	public static DslPointcut bind(final Object arg)
	{
		assert arg instanceof Map;
		assert ((Map) arg).size() == 1;
		final String name = (String) ((Map) arg).keySet().iterator().next();
		final DslPointcut pct = (DslPointcut) ((Map) arg).values().iterator().next();

		return new DslPointcut()
		{
			@Override
			List matches(Object src, ProcessingContext context)
			{
				final List result = pct.matches(src, context);
				if(result != null)
				{
					Map<String, List> map = context.get(BOUND);
					if(map == null)
					{
						context.put(BOUND, map = new HashMap<String, List>());
					}
					map.put(name, result);
				}
				return result;
			}

			@Override
			boolean operatesOn(Class c)
			{
				return pct.operatesOn(c);
			}
		};
	}

}
