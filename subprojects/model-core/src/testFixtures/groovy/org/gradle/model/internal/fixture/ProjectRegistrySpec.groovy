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

package org.gradle.model.internal.fixture
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.typeconversion.TypeConverter
import org.gradle.model.internal.core.DefaultNodeInitializerRegistry
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.core.ModelRegistrations
import org.gradle.model.internal.core.NodeInitializerRegistry
import org.gradle.model.internal.inspect.ModelRuleExtractor
import org.gradle.model.internal.manage.instance.ManagedStructBindingStore
import org.gradle.model.internal.manage.instance.NewManagedProxyFactory
import org.gradle.model.internal.manage.schema.ModelSchemaStore
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.util.TestUtil
import spock.lang.Specification

@SuppressWarnings("GrMethodMayBeStatic")
class ProjectRegistrySpec extends Specification {
    public static final ModelSchemaStore SCHEMA_STORE
    public static final NewManagedProxyFactory MANAGED_PROXY_FACTORY
    public static final ModelRuleExtractor MODEL_RULE_EXTRACTOR
    public static final NodeInitializerRegistry NODE_INITIALIZER_REGISTRY
    public static final ManagedStructBindingStore BINDING_STORE

    static {
        def services = TestUtil.createRootProject().services
        SCHEMA_STORE = services.get(ModelSchemaStore)
        MANAGED_PROXY_FACTORY = services.get(NewManagedProxyFactory)
        MODEL_RULE_EXTRACTOR = services.get(ModelRuleExtractor)
        BINDING_STORE = new ManagedStructBindingStore()
        NODE_INITIALIZER_REGISTRY = new DefaultNodeInitializerRegistry(SCHEMA_STORE, BINDING_STORE)
    }

    ModelRegistry registry = createModelRegistry()
    ModelSchemaStore schemaStore = SCHEMA_STORE
    NewManagedProxyFactory proxyFactory = MANAGED_PROXY_FACTORY
    ModelRuleExtractor modelRuleExtractor = MODEL_RULE_EXTRACTOR
    ManagedStructBindingStore bindingStore = createBindingStore()
    TypeConverter typeConverter = createTypeConverter()
    NodeInitializerRegistry nodeInitializerRegistry = createNodeInitializerRegistry()

    def setup() {
        registerService "schemaStore", ModelSchemaStore, schemaStore
        registerService "proxyFactory", NewManagedProxyFactory, proxyFactory
        registerService "serviceRegistry", ServiceRegistry, Mock(ServiceRegistry)
        registerService "typeConverter", TypeConverter, typeConverter
        registerService "structBindingStore", ManagedStructBindingStore, bindingStore
        registerService "nodeInitializerRegistry", NodeInitializerRegistry, nodeInitializerRegistry
    }

    protected ModelRegistry createModelRegistry() {
        return new ModelRegistryHelper()
    }

    protected NodeInitializerRegistry createNodeInitializerRegistry() {
        return NODE_INITIALIZER_REGISTRY
    }

    protected ManagedStructBindingStore createBindingStore() {
        return BINDING_STORE
    }

    protected TypeConverter createTypeConverter() {
        return Mock(TypeConverter)
    }

    protected <T> void registerService(String path, Class<T> type, T instance) {
        registry.register(ModelRegistrations.serviceInstance(ModelReference.of(path, type), instance).descriptor("register service '$path'").build())
    }
}
