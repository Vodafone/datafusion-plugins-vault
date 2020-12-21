/*
 * Copyright © 2020 Vodafone
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vodafone.datafusion.plugins.cloudvault.helpers;

import com.bettercloud.vault.SslConfig;
import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.json.JsonObject;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.vodafone.datafusion.plugins.cloudvault.CloudVault;

import java.io.IOException;

/**
 * Helper class used to retrieve credentials from Vault.
 */
public class VaultHelper {
    private static int VAULT_VERSION = 1;

    private VaultHelper() {

    }

    /**
     * Gets a specific credential from Vault.
     * @param config Configuration.
     * @return A JSON object containing the credentials requested.
     */
    public static JsonObject retrieveCredential(CloudVault.VaultDataConf config) {
        Vault vault = getVaultObject(config.getVaultUrl());
        try {
            if(config.isVaultNamespace()) {
                String token = vault.auth().withNameSpace(config.getVaultNamespace()).
                                    loginByGCP(config.getVaultRole(), getOAuthToken(config.getVaultRole(), config.getServiceAccount()))
                                    .getAuthClientToken();
                vault = getVaultObject(config.getVaultUrl(), token);
                return vault.logical()
                            .withNameSpace(config.getVaultNamespace())
                            .read(config.getVaultCredentialPath())
                            .getDataObject();
            } else {
                String token = vault.auth()
                             .loginByGCP(config.getVaultRole(), getOAuthToken(config.getVaultRole(), config.getServiceAccount()))
                             .getAuthClientToken();
                vault = getVaultObject(config.getVaultUrl(), token);
                return vault.logical()
                            .read(config.getVaultCredentialPath())
                            .getDataObject();
            }
        } catch (VaultException e) {
            throw new RuntimeException("retrieveCredential", e);
        }
    }

    /**
     * Gets a Vault object authenticated by the compute's JWT token to run operations on.
     * @param vaultUrl A String containing the Vault's URL.
     * @return A GCP JWT authenticated Vault object.
     */
    private static Vault getVaultObject(String vaultUrl) {
        try {
            VaultConfig config = new VaultConfig().address(vaultUrl).sslConfig(new SslConfig().verify(false).build()).build();
            return new Vault(config, VaultHelper.VAULT_VERSION);
        } catch (VaultException e) {
            throw new RuntimeException("getVaultObject", e);
        }
    }

    /**
     * Get a Vault object authenticated through a token to run operations on.
     * @param vaultUrl A String containing the Vault's URL.
     * @param token A String containing the authentication token.
     * @return A token authenticated Vault object.
     */
    private static Vault getVaultObject(String vaultUrl, String token) {
        try {
            VaultConfig config = new VaultConfig().address(vaultUrl).token(token).sslConfig(new SslConfig().verify(false).build()).build();
            return new Vault(config, VaultHelper.VAULT_VERSION);
        } catch (VaultException e) {
            throw new RuntimeException("getVaultObject with token", e);
        }
    }

    /**
     * Gets a JWT token based on the compute instance's identity with the Vault as the audience.
     * @param role A String containing the Vault's user role.
     * @return A String containing the JWT token.
     */
    private static String getOAuthToken(String role, String serviceAccount) {
        try {
            HttpRequestFactory requestFactory
                    = new NetHttpTransport().createRequestFactory();
            GenericUrl metadataUrl = new GenericUrl("http://metadata/computeMetadata/v1/instance/service-accounts/" + serviceAccount + "/identity");
            metadataUrl.putIfAbsent("audience", "vault/".concat(role));
            metadataUrl.putIfAbsent("format", "full");
            HttpRequest request = requestFactory
                    .buildGetRequest(metadataUrl);
            HttpHeaders headers = request.getHeaders();
            headers.set("Metadata-Flavor", "Google");
            return request.execute().parseAsString();
        } catch (IOException e) {
            throw new RuntimeException("getOAuthToken", e);
        }
    }
}
