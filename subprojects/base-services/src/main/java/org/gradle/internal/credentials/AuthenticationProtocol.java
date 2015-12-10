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

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

public abstract class AuthenticationProtocol {

    private final List<AbstractCredentialsProvider<? extends Credentials, ? extends CredentialsProvider>> credentialsProviders = newArrayList();

    public abstract Iterable<Class<? extends Credentials>> supportedCredentialTypes();

    public void attachProvider(AbstractCredentialsProvider credentialsProvider) {
        validateProvider(credentialsProvider);
        credentialsProviders.add(credentialsProvider);
    }

    public List<AbstractCredentialsProvider<? extends Credentials, ? extends CredentialsProvider>> getCredentialsProviders() {
        return credentialsProviders;
    }

    private void validateProvider(AbstractCredentialsProvider credentialsProvider) {
        for (Class<? extends Credentials> type : supportedCredentialTypes()) {
            if (type.isAssignableFrom(credentialsProvider.getCredentialsType())) {
                return;
            }
        }
        throw new IllegalArgumentException("The authentication protocol does not support CredentialProviders providing credentials of type " + credentialsProvider.getCredentialsType());
    }
}
