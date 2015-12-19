/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.model.internal.manage.instance;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.typeconversion.TypeConverter;
import org.gradle.model.internal.manage.schema.NewStructSchema;
import org.gradle.model.internal.manage.schema.extract.NewManagedProxyClassGenerator;
import org.gradle.model.internal.type.ModelType;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class NewManagedProxyFactory {
    private final NewManagedProxyClassGenerator proxyClassGenerator = new NewManagedProxyClassGenerator();
    private final LoadingCache<CacheKey<?>, Class<?>> generatedImplementationTypes = CacheBuilder.newBuilder()
        .weakValues()
        .build(new CacheLoader<CacheKey<?>, Class<?>>() {
            @Override
            public Class<?> load(CacheKey<?> key) throws Exception {
                return proxyClassGenerator.generate(key.backingStateType, key.viewSchema, key.bindings);
            }
        });

    /**
     * Generates a view of the given type.
     */
    public <T> T createProxy(GeneratedViewState state, NewStructSchema<T> viewSchema, ManagedStructBindingStore.ManagedStructBinding<?> bindings) {
        try {
            Class<? extends T> generatedClass = Cast.uncheckedCast(generatedImplementationTypes.get(new CacheKey<T>(GeneratedViewState.class, viewSchema, bindings)));
            Constructor<? extends T> constructor = generatedClass.getConstructor(GeneratedViewState.class, TypeConverter.class);
            return constructor.newInstance(state, null);
        } catch (InvocationTargetException e) {
            throw UncheckedException.throwAsUncheckedException(e.getTargetException());
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    /**
     * Generates a view of the given type.
     */
    public <T> T createProxy(ModelElementState state, NewStructSchema<T> viewSchema, ManagedStructBindingStore.ManagedStructBinding<?> bindings, TypeConverter typeConverter) {
        try {
            Class<? extends T> generatedClass = Cast.uncheckedCast(generatedImplementationTypes.get(new CacheKey<T>(ModelElementState.class, viewSchema, bindings)));
            NewStructSchema<?> delegateSchema = bindings.getDelegateSchema();
            if (delegateSchema == null) {
                Constructor<? extends T> constructor = generatedClass.getConstructor(ModelElementState.class, TypeConverter.class);
                return constructor.newInstance(state, typeConverter);
            } else {
                ModelType<?> delegateType = delegateSchema.getType();
                Object delegate = state.getBackingNode().getPrivateData(delegateType);
                Constructor<? extends T> constructor = generatedClass.getConstructor(ModelElementState.class, TypeConverter.class, delegateType.getConcreteClass());
                return constructor.newInstance(state, typeConverter, delegate);
            }
        } catch (InvocationTargetException e) {
            throw UncheckedException.throwAsUncheckedException(e.getTargetException());
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private static class CacheKey<T> {
        private final Class<? extends GeneratedViewState> backingStateType;
        private final NewStructSchema<T> viewSchema;
        private final ManagedStructBindingStore.ManagedStructBinding<?> bindings;

        private CacheKey(Class<? extends GeneratedViewState> backingStateType, NewStructSchema<T> viewSchema, ManagedStructBindingStore.ManagedStructBinding<?> bindings) {
            this.backingStateType = backingStateType;
            this.viewSchema = viewSchema;
            this.bindings = bindings;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            CacheKey<?> cacheKey = (CacheKey<?>) o;

            if (!backingStateType.equals(cacheKey.backingStateType)) {
                return false;
            }
            if (!viewSchema.equals(cacheKey.viewSchema)) {
                return false;
            }
            return !(bindings != null ? !bindings.equals(cacheKey.bindings) : cacheKey.bindings != null);
        }

        @Override
        public int hashCode() {
            int result = viewSchema.hashCode();
            result = 31 * result + (bindings != null ? bindings.hashCode() : 0);
            result = 31 * result + backingStateType.hashCode();
            return result;
        }
    }
}
