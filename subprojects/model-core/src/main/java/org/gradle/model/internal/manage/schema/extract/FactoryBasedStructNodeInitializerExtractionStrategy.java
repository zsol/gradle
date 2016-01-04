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

package org.gradle.model.internal.manage.schema.extract;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.gradle.internal.Cast;
import org.gradle.model.internal.core.InstanceFactory;
import org.gradle.model.internal.core.NodeInitializer;
import org.gradle.model.internal.inspect.FactoryBasedStructNodeInitializer;
import org.gradle.model.internal.manage.instance.ManagedStructBindingStore;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.NewStructSchema;
import org.gradle.model.internal.type.ModelType;

import java.util.Set;

public class FactoryBasedStructNodeInitializerExtractionStrategy<T> implements NodeInitializerExtractionStrategy {
    private final InstanceFactory<T> instanceFactory;
    private final ModelSchemaStore schemaStore;
    private final ManagedStructBindingStore bindingStore;

    public FactoryBasedStructNodeInitializerExtractionStrategy(InstanceFactory<T> instanceFactory, ModelSchemaStore schemaStore, ManagedStructBindingStore bindingStore) {
        this.instanceFactory = instanceFactory;
        this.schemaStore = schemaStore;
        this.bindingStore = bindingStore;
    }

    @Override
    public <S> NodeInitializer extractNodeInitializer(ModelSchema<S> schema) {
        if (!instanceFactory.getBaseInterface().isAssignableFrom(schema.getType())) {
            return null;
        }
        return getNodeInitializer(Cast.<ModelSchema<? extends T>>uncheckedCast(schema));
    }

    private <S extends T> NodeInitializer getNodeInitializer(final ModelSchema<S> schema) {
        // TODO:LPTR Move all this to InstanceFactory.getBindingsFor() or something
        // TODO:LPTR Check this in a better way
        NewStructSchema<S> publicSchema = Cast.uncheckedCast(schema);

        Set<ModelType<?>> internalViews = instanceFactory.getInternalViews(publicSchema.getType());
        Iterable<NewStructSchema<?>> internalViewSchemas = Iterables.transform(internalViews, new Function<ModelType<?>, NewStructSchema<?>>() {
            @Override
            public NewStructSchema<?> apply(ModelType<?> internalView) {
                return getStructSchema(internalView, "Internal view");
            }
        });

        InstanceFactory.ImplementationInfo<T> implementationInfo = instanceFactory.getManagedSubtypeImplementationInfo(publicSchema.getType());
        NewStructSchema<? extends T> delegateSchema = getStructSchema(implementationInfo.getDelegateType(), "Delegate");
        ManagedStructBindingStore.ManagedStructBinding<S> bindings = bindingStore.getBinding(publicSchema, internalViewSchemas, delegateSchema);
        return new FactoryBasedStructNodeInitializer<T, S>(instanceFactory, bindings);
    }

    private <S> NewStructSchema<S> getStructSchema(ModelType<S> type, String role) {
        if (type == null) {
            return null;
        }
        ModelSchema<S> schema = schemaStore.getSchema(type);
        if (!(schema instanceof NewStructSchema)) {
            throw new IllegalStateException(String.format("%s type '%s' is not a struct.", role, type));
        }
        return Cast.uncheckedCast(schema);
    }

    @Override
    public Iterable<ModelType<?>> supportedTypes() {
        return ImmutableSet.<ModelType<?>>copyOf(instanceFactory.getSupportedTypes());
    }
}
