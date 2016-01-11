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

package org.gradle.model.internal.core;

import com.google.common.base.Joiner;
import com.google.common.collect.*;
import org.gradle.internal.Cast;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.schema.extract.ModelSchemaUtils;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BaseInstanceFactory<T> implements InstanceFactory<T> {

    private final String displayName;
    private final ModelType<T> baseInterface;
    private final ModelType<? extends T> baseImplementation;
    private final Map<ModelType<? extends T>, TypeRegistration<? extends T>> registrations = Maps.newLinkedHashMap();

    public BaseInstanceFactory(String displayName, Class<T> baseInterface, Class<? extends T> baseImplementation) {
        this.displayName = displayName;
        this.baseInterface = ModelType.of(baseInterface);
        this.baseImplementation = ModelType.of(baseImplementation);
    }

    @Override
    public ModelType<T> getBaseInterface() {
        return baseInterface;
    }

    @Override
    public <S extends T> TypeRegistrationBuilder<S> register(ModelType<S> publicType, ModelRuleDescriptor source) {
        TypeRegistration<S> registration = Cast.uncheckedCast(registrations.get(publicType));
        if (registration == null) {
            registration = new TypeRegistration<S>(publicType);
            registrations.put(publicType, registration);
        }
        return new TypeRegistrationBuilderImpl<S>(source, registration);
    }

    @Override
    public <S extends T> ImplementationInfo<T> getImplementationInfo(final ModelType<S> publicType) {
        if (!registrations.containsKey(publicType)) {
            throw new IllegalArgumentException(
                String.format("Cannot create a '%s' because this type is not known to %s. Known types are: %s",
                    publicType.getDisplayName(), displayName, getConstructibleTypeNames()));
        }
        final List<ImplementationInfo<T>> implementationInfos = Lists.newArrayListWithCapacity(1);
        ModelSchemaUtils.walkTypeHierarchy(publicType.getConcreteClass(), new RegistrationHierarchyVisitor<S>() {
            @Override
            protected void visitRegistration(TypeRegistration<? extends T> registration) {
                if (registration != null && registration.implementationRegistration != null) {
                    implementationInfos.add(new ImplementationInfoImpl<T>(publicType, registration.implementationRegistration));
                }
            }
        });

        if (implementationInfos.isEmpty()) {
            throw new IllegalStateException(String.format("Factory registration for '%s' is invalid because it doesn't extend an interface with a default implementation", publicType));
        }

        return implementationInfos.get(0);
    }

    @Override
    public <S extends T> Set<ModelType<?>> getInternalViews(ModelType<S> publicType) {
        final ImmutableSet.Builder<ModelType<?>> builder = ImmutableSet.builder();
        ModelSchemaUtils.walkTypeHierarchy(publicType.getConcreteClass(), new RegistrationHierarchyVisitor<S>() {
            @Override
            protected void visitRegistration(TypeRegistration<? extends T> registration) {
                for (InternalViewRegistration<?> internalViewRegistration : registration.internalViewRegistrations) {
                    builder.add(internalViewRegistration.getInternalView());
                }
            }
        });
        return builder.build();
    }

    @Override
    public Set<ModelType<? extends T>> getSupportedTypes() {
        ImmutableSortedSet.Builder<ModelType<? extends T>> supportedTypes = ImmutableSortedSet.orderedBy(ModelTypes.<T>displayOrder());
        for (TypeRegistration<?> registration : registrations.values()) {
            supportedTypes.add(registration.publicType);
        }
        return supportedTypes.build();
    }

    private String getConstructibleTypeNames() {
        Set<ModelType<? extends T>> constructibleTypes = getConstructibleTypes();
        if (constructibleTypes.isEmpty()) {
            return "(None)";
        }
        return Joiner.on(", ").join(constructibleTypes);
    }

    private Set<ModelType<? extends T>> getConstructibleTypes() {
        return Sets.difference(getSupportedTypes(), Collections.singleton(baseInterface));
    }

    private <S extends T> TypeRegistration<S> getRegistration(ModelType<S> type) {
        return Cast.uncheckedCast(registrations.get(type));
    }

    @Override
    public void validateRegistrations() {
    }

    @Override
    public String toString() {
        return "[" + getConstructibleTypeNames() + "]";
    }

    private class TypeRegistrationBuilderImpl<S extends T> implements TypeRegistrationBuilder<S> {
        private final ModelRuleDescriptor source;
        private final TypeRegistration<S> registration;

        public TypeRegistrationBuilderImpl(ModelRuleDescriptor source, TypeRegistration<S> registration) {
            this.source = source;
            this.registration = registration;
        }

        @Override
        public TypeRegistrationBuilder<S> withImplementation(ModelType<? extends S> implementationType, ImplementationFactory<S> factory) {
            registration.setImplementation(implementationType, source, factory);
            return this;
        }

        @Override
        public TypeRegistrationBuilder<S> withInternalView(ModelType<?> internalView) {
            registration.addInternalView(internalView, source);
            return this;
        }
    }

    private class TypeRegistration<S extends T> {
        private final ModelType<S> publicType;
        private ImplementationRegistration<S> implementationRegistration;
        private final List<InternalViewRegistration<?>> internalViewRegistrations = Lists.newArrayList();

        public TypeRegistration(ModelType<S> publicType) {
            this.publicType = publicType;
        }

        public void setImplementation(ModelType<? extends S> implementationType, ModelRuleDescriptor source, ImplementationFactory<S> factory) {
            if (implementationRegistration != null) {
                throw new IllegalStateException(String.format("Cannot register implementation for type '%s' because an implementation for this type was already registered by %s",
                    publicType, implementationRegistration.getSource()));
            }
            if (!baseInterface.isAssignableFrom(implementationType)) {
                throw new IllegalArgumentException(String.format("Implementation type '%s' registered for '%s' must extend '%s'", implementationType, publicType, baseImplementation));
            }
            if (Modifier.isAbstract(implementationType.getConcreteClass().getModifiers())) {
                throw new IllegalArgumentException(String.format("Implementation type '%s' registered for '%s' must not be abstract", implementationType, publicType));
            }
            try {
                implementationType.getConcreteClass().getConstructor();
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(String.format("Implementation type '%s' registered for '%s' must have a public default constructor", implementationType, publicType));
            }
            this.implementationRegistration = new ImplementationRegistration<S>(source, implementationType, factory);
        }

        public <V> void addInternalView(ModelType<V> internalView, ModelRuleDescriptor source) {
            if (!internalView.getConcreteClass().isInterface()) {
                throw new IllegalArgumentException(String.format("Internal view '%s' registered for '%s' must be an interface", internalView, publicType));
            }
            internalViewRegistrations.add(new InternalViewRegistration<V>(source, internalView));
        }
    }

    private static class ImplementationRegistration<S> {
        private final ModelRuleDescriptor source;
        private final ModelType<? extends S> implementationType;
        private final ImplementationFactory<S> factory;

        private ImplementationRegistration(ModelRuleDescriptor source, ModelType<? extends S> implementationType, ImplementationFactory<S> factory) {
            this.source = source;
            this.implementationType = implementationType;
            this.factory = factory;
        }

        public ModelRuleDescriptor getSource() {
            return source;
        }

        public ModelType<? extends S> getImplementationType() {
            return implementationType;
        }

        public ImplementationFactory<? extends S> getFactory() {
            return factory;
        }
    }

    private static class InternalViewRegistration<T> {
        private final ModelRuleDescriptor source;
        private final ModelType<T> internalView;

        private InternalViewRegistration(ModelRuleDescriptor source, ModelType<T> internalView) {
            this.source = source;
            this.internalView = internalView;
        }

        public ModelRuleDescriptor getSource() {
            return source;
        }

        public ModelType<T> getInternalView() {
            return internalView;
        }
    }

    private static class ImplementationInfoImpl<T> implements ImplementationInfo<T> {
        private final ModelType<? extends T> publicType;
        private final ImplementationRegistration<T> implementationRegistration;

        public ImplementationInfoImpl(ModelType<? extends T> publicType, ImplementationRegistration<? extends T> implementationRegistration) {
            this.publicType = publicType;
            this.implementationRegistration = Cast.uncheckedCast(implementationRegistration);
        }

        @Override
        public T create(MutableModelNode modelNode) {
            return implementationRegistration.factory.create(publicType, modelNode.getPath().getName(), modelNode);
        }

        public ModelType<? extends T> getDelegateType() {
            return Cast.uncheckedCast(implementationRegistration.implementationType);
        }

        @Override
        public String toString() {
            return String.valueOf(publicType);
        }
    }

    private abstract class RegistrationHierarchyVisitor<S> implements ModelSchemaUtils.TypeVisitor<S> {
        @Override
        public void visitType(Class<? super S> type) {
            if (!baseInterface.getConcreteClass().isAssignableFrom(type)) {
                return;
            }
            Class<? extends T> superTypeClassAsBaseType = type.asSubclass(baseInterface.getConcreteClass());
            ModelType<? extends T> superTypeAsBaseType = ModelType.of(superTypeClassAsBaseType);

            TypeRegistration<? extends T> registration = getRegistration(superTypeAsBaseType);
            if (registration != null) {
                visitRegistration(registration);
            }
        }

        protected abstract void visitRegistration(TypeRegistration<? extends T> registration);
    }
}
