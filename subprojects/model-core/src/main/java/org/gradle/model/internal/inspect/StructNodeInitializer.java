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

package org.gradle.model.internal.inspect;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import org.gradle.api.Named;
import org.gradle.internal.BiAction;
import org.gradle.internal.typeconversion.TypeConverter;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.instance.ManagedStructBindingStore;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.projection.StructProjection;
import org.gradle.model.internal.manage.schema.*;
import org.gradle.model.internal.type.ModelType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.gradle.model.internal.core.ModelViews.getInstance;
import static org.gradle.model.internal.core.NodeInitializerContext.forProperty;

public class StructNodeInitializer<T> implements NodeInitializer {

    protected final ManagedStructBindingStore.ManagedStructBinding<T> bindings;

    public StructNodeInitializer(ManagedStructBindingStore.ManagedStructBinding<T> bindings) {
        this.bindings = bindings;
    }

    @Override
    public Multimap<ModelActionRole, ModelAction> getActions(ModelReference<?> subject, ModelRuleDescriptor descriptor) {
        return ImmutableSetMultimap.<ModelActionRole, ModelAction>builder()
            .put(ModelActionRole.Discover, DirectNodeInputUsingModelAction.of(subject, descriptor,
                Arrays.<ModelReference<?>>asList(
                    ModelReference.of(ManagedProxyFactory.class),
                    ModelReference.of(TypeConverter.class)
                ),
                new BiAction<MutableModelNode, List<ModelView<?>>>() {
                    @Override
                    public void execute(MutableModelNode modelNode, List<ModelView<?>> modelViews) {
                        ManagedProxyFactory proxyFactory = getInstance(modelViews.get(0), ManagedProxyFactory.class);
                        TypeConverter typeConverter = getInstance(modelViews, 1, TypeConverter.class);
                        for (NewStructSchema<?> viewSchema : bindings.getAllViewSchemas()) {
                            addProjection(modelNode, viewSchema, proxyFactory, typeConverter);
                        }
                    }
                }
            ))
            .put(ModelActionRole.Create, DirectNodeInputUsingModelAction.of(subject, descriptor,
                Arrays.<ModelReference<?>>asList(
                    ModelReference.of(ModelSchemaStore.class),
                    ModelReference.of(NodeInitializerRegistry.class),
                    ModelReference.of(ManagedProxyFactory.class),
                    ModelReference.of(TypeConverter.class)
                ),
                new BiAction<MutableModelNode, List<ModelView<?>>>() {
                    @Override
                    public void execute(MutableModelNode modelNode, List<ModelView<?>> modelViews) {
                        ModelSchemaStore schemaStore = getInstance(modelViews, 0, ModelSchemaStore.class);
                        NodeInitializerRegistry nodeInitializerRegistry = getInstance(modelViews, 1, NodeInitializerRegistry.class);
                        ManagedProxyFactory proxyFactory = getInstance(modelViews, 2, ManagedProxyFactory.class);
                        TypeConverter typeConverter = getInstance(modelViews, 3, TypeConverter.class);

                        addPropertyLinks(modelNode, schemaStore, nodeInitializerRegistry, proxyFactory, bindings.getGeneratedProperties(), typeConverter);
                        initializePrivateData(modelNode);
                    }
                }
            ))
            .build();
    }

    protected void initializePrivateData(MutableModelNode modelNode) {
    }

    private <V> void addProjection(MutableModelNode modelNode, NewStructSchema<V> viewSchema, ManagedProxyFactory proxyFactory, TypeConverter typeConverter) {
        modelNode.addProjection(new StructProjection<V>(viewSchema, bindings, proxyFactory, typeConverter));
    }

    protected void addPropertyLinks(MutableModelNode modelNode,
                                    ModelSchemaStore schemaStore,
                                    NodeInitializerRegistry nodeInitializerRegistry,
                                    ManagedProxyFactory proxyFactory,
                                    Map<String, ManagedStructBindingStore.GeneratedProperty> properties,
                                    TypeConverter typeConverter) {
        for (ManagedStructBindingStore.GeneratedProperty property : properties.values()) {
            addPropertyLink(modelNode, property, property.getType(), schemaStore, nodeInitializerRegistry, proxyFactory, typeConverter);
        }
        // TODO:LPTR Fix this
//        if (isANamedType()) {
//            // Only initialize "name" child node if the schema has such a managed property.
//            // This is not the case for a managed subtype of an unmanaged type that implements Named.
//            ModelProperty<?> nameProperty = schema.getProperty("name");
//            if (nameProperty != null && nameProperty.getStateManagementType().equals(ModelProperty.StateManagementType.MANAGED)
//                && properties.containsKey(nameProperty)) {
//                MutableModelNode nameLink = modelNode.getLink("name");
//                if (nameLink == null) {
//                    throw new IllegalStateException("expected name node for " + modelNode.getPath());
//                }
//                nameLink.setPrivateData(ModelType.of(String.class), modelNode.getPath().getName());
//            }
//        }
    }

    private <P> void addPropertyLink(MutableModelNode modelNode,
                                     ManagedStructBindingStore.GeneratedProperty property,
                                     ModelType<P> propertyType,
                                     ModelSchemaStore schemaStore,
                                     NodeInitializerRegistry nodeInitializerRegistry,
                                     ManagedProxyFactory proxyFactory,
                                     TypeConverter typeConverter) {
        ModelSchema<P> propertySchema = schemaStore.getSchema(propertyType);

        validateProperty(propertySchema, property, nodeInitializerRegistry);

        ModelRuleDescriptor descriptor = modelNode.getDescriptor();
        ModelPath childPath = modelNode.getPath().child(property.getName());
        boolean hidden = property.isHidden();
        if (!property.isWritable()) {
            // TODO:LPTR Report proper declaring type
            ModelRegistration registration = ModelRegistrations.of(childPath, nodeInitializerRegistry.getNodeInitializer(forProperty(propertySchema.getType(), property, ModelType.UNTYPED)))
                .descriptor(descriptor)
                .hidden(hidden)
                .build();
            modelNode.addLink(registration);
        } else {
            if (propertySchema instanceof ScalarCollectionSchema) {
                // TODO:LPTR Report proper declaring type
                ModelRegistration registration = ModelRegistrations.of(childPath, nodeInitializerRegistry.getNodeInitializer(forProperty(propertySchema.getType(), property, ModelType.UNTYPED)))
                    .descriptor(descriptor)
                    .hidden(hidden)
                    .build();
                modelNode.addLink(registration);
            } else {
                modelNode.addReference(property.getName(), propertySchema.getType(), descriptor);
            }
        }
    }

    private <P> void validateProperty(ModelSchema<P> propertySchema, ManagedStructBindingStore.GeneratedProperty property, NodeInitializerRegistry nodeInitializerRegistry) {
//        if (propertySchema instanceof ManagedImplSchema) {
            if (!property.isWritable()) {
                if (isCollectionOfManagedTypes(propertySchema)) {
                    CollectionSchema<P, ?> propertyCollectionsSchema = (CollectionSchema<P, ?>) propertySchema;
                    ModelType<?> elementType = propertyCollectionsSchema.getElementType();
                    // TODO:LPTR Report declaring type properly
                    nodeInitializerRegistry.ensureHasInitializer(forProperty(elementType, property, ModelType.UNTYPED));
                }
//                if (property.isDeclaredAsHavingUnmanagedType()) {
//                    throw new UnmanagedPropertyMissingSetterException(property);
//                }
            }
//        } else if (!shouldHaveANodeInitializer(property, propertySchema) && !property.isWritable() && !isNamePropertyOfANamedType(property)) {
//            throw new ReadonlyImmutableManagedPropertyException(schema.getType(), property.getName(), property.getType());
//        }
    }

    private <P> boolean isCollectionOfManagedTypes(ModelSchema<P> propertySchema) {
        return propertySchema instanceof CollectionSchema
            && !(propertySchema instanceof ScalarCollectionSchema);
    }

    private <P> boolean isNamePropertyOfANamedType(ModelProperty<P> property) {
        return isANamedType() && "name".equals(property.getName());
    }

    public boolean isANamedType() {
        for (ModelSchema<?> viewSchema : bindings.getAllViewSchemas()) {
            if (Named.class.isAssignableFrom(viewSchema.getType().getRawClass())) {
                return true;
            }
        }
        return false;
    }

    private <P> boolean shouldHaveANodeInitializer(ModelProperty<P> property, ModelSchema<P> propertySchema) {
        return !(propertySchema instanceof ScalarValueSchema) && !property.isDeclaredAsHavingUnmanagedType();
    }
}
