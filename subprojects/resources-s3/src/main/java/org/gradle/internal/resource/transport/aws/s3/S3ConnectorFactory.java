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

package org.gradle.internal.resource.transport.aws.s3;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import org.gradle.api.credentials.AwsCredentials;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.credentials.CredentialsProvider;
import org.gradle.authentication.Authentication;
import org.gradle.internal.authentication.AllSchemesAuthentication;
import org.gradle.internal.authentication.AwsAuthenticationProtocol;
import org.gradle.internal.authentication.DefaultAwsIMCredentialsProvider;
import org.gradle.internal.credentials.AbstractCredentialsProvider;
import org.gradle.internal.credentials.AuthenticationProtocol;
import org.gradle.internal.resource.connector.ResourceConnectorFactory;
import org.gradle.internal.resource.connector.ResourceConnectorSpecification;
import org.gradle.internal.resource.transfer.ExternalResourceConnector;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class S3ConnectorFactory implements ResourceConnectorFactory {
    @Override
    public Set<String> getSupportedProtocols() {
        return Collections.singleton("s3");
    }

    @Override
    public Set<Class<? extends Authentication>> getSupportedAuthentication() {
        Set<Class<? extends Authentication>> supported = new HashSet<Class<? extends Authentication>>();
        supported.add(AllSchemesAuthentication.class);
        return supported;
    }

    @Override
    public ExternalResourceConnector createResourceConnector(ResourceConnectorSpecification connectionDetails) {
        Optional<AwsCredentials> awsCredentialsOptional = Optional.absent();
        Iterable<Class<? extends CredentialsProvider>> credentialsProviders = connectionDetails.getCredentialsProviders();
        if (Iterables.isEmpty(credentialsProviders)) {
            awsCredentialsOptional = Optional.fromNullable(connectionDetails.getCredentials(AwsCredentials.class));
        }
        if (Iterables.isEmpty(credentialsProviders) && !awsCredentialsOptional.isPresent()) {
            throw new IllegalArgumentException("At least one CredentialsProvider is required");
        }

        Multimap<AuthenticationProtocol, AbstractCredentialsProvider<? extends Credentials, ? extends CredentialsProvider>> configuredAuthProtocols = getConfiguredAuthProtocols(connectionDetails, getAuthenticationProtocols());
        return new S3ResourceConnector(S3Client.from(configuredAuthProtocols.values(), awsCredentialsOptional));
    }

    private Multimap<AuthenticationProtocol, AbstractCredentialsProvider<? extends Credentials, ? extends CredentialsProvider>> getConfiguredAuthProtocols(ResourceConnectorSpecification connectionDetails, Iterable<? extends AuthenticationProtocol> authenticationProtocols) {
        Multimap<AuthenticationProtocol, AbstractCredentialsProvider<? extends Credentials, ? extends CredentialsProvider>> configuredAuthProtocols = ArrayListMultimap.create();
        Iterable<Class<? extends CredentialsProvider>> configuredCredentialsProviders = connectionDetails.getCredentialsProviders();
        for (AuthenticationProtocol authenticationProtocol : authenticationProtocols) {
            for (AbstractCredentialsProvider<? extends Credentials, ? extends CredentialsProvider> credentialsProvider : authenticationProtocol.getCredentialsProviders()) {
                final Class<? extends CredentialsProvider> publicProviderType = credentialsProvider.getPublicProviderType();
                Optional<Class<? extends CredentialsProvider>> classOptional = Iterables.tryFind(configuredCredentialsProviders, new Predicate<Class<? extends CredentialsProvider>>() {
                    @Override
                    public boolean apply(Class<? extends CredentialsProvider> input) {
                        return input == publicProviderType;
                    }
                });

                if (classOptional.isPresent()) {
                    configuredAuthProtocols.put(authenticationProtocol, credentialsProvider);
                }
            }
        }
        return configuredAuthProtocols;
    }

    @Override
    public Iterable<? extends AuthenticationProtocol> getAuthenticationProtocols() {
        AwsAuthenticationProtocol awsAuthenticationProtocol = new AwsAuthenticationProtocol();
        awsAuthenticationProtocol.attachProvider(new DefaultAwsIMCredentialsProvider());
        return ImmutableSet.of(awsAuthenticationProtocol);
    }
}
