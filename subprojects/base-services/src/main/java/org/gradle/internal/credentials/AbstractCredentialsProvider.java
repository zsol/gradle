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

package org.gradle.internal.credentials;

import org.gradle.api.credentials.Credentials;
import org.gradle.api.credentials.CredentialsProvider;

public abstract class AbstractCredentialsProvider<T extends Credentials, P extends CredentialsProvider> implements CredentialsProviderInternal {
    private final Class<T> credentialsType;
    private final Class<P> publicProviderType;

    public AbstractCredentialsProvider(Class<T> credentialsType, Class<P> publicProviderType) {
        this.credentialsType = credentialsType;
        this.publicProviderType = publicProviderType;
    }

    @Override
    public Class<T> getCredentialsType() {
        return credentialsType;
    }

    public Class<P> getPublicProviderType() {
        return publicProviderType;
    }

    public abstract T get();
}
