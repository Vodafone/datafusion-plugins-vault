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
package com.vodafone.datafusion.plugins.cloudvault;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.bettercloud.vault.json.JsonObject;
import com.google.common.base.Strings;
import com.vodafone.datafusion.plugins.cloudvault.helpers.VaultHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Macro;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.cdap.api.plugin.PluginConfig;
import io.cdap.cdap.etl.api.PipelineConfigurer;
import io.cdap.cdap.etl.api.action.Action;
import io.cdap.cdap.etl.api.action.ActionContext;

@Plugin(type = Action.PLUGIN_TYPE)
@Name(CloudVault.NAME)
@Description("Credentials Vault connection.")
public class CloudVault extends Action {
    private static final Logger LOG = LoggerFactory.getLogger(CloudVault.class);

    public static final String NAME = "CloudVault";
    private final VaultDataConf config;

    public static class VaultDataConf extends PluginConfig {
        /**
         *
         */
        private static final long serialVersionUID = -2342328008832568250L;

        @Macro
        @Description("List of fields to mapping Vault - Argument.")
        private String fieldsMapping;

        @Macro
        @Description("The URL of the Vault Credentials.")
        private String vaultUrl;
    
        @Macro
        @Description("The role of the user in Vault.")
        private final String vaultRole;

        @Macro
        @Description("The path of credentials in Vault.")
        private final String vaultCredentialPath;

        @Description("The namespace of credentials in Vault.")
        @Macro
        @Nullable
        private final String vaultNamespace;

        @Macro
        @Description("Service Account to Credentials Vault.")
        private final String serviceAccount;

        private void validate() {
            //TO-DO
        }

        public VaultDataConf(String fieldsMapping,
                             String vaultUrl,
                             String vaultRole,
                             String vaultCredentialPath,
                             @Nullable String vaultNamespace,
                             String serviceAccount
                            ) {
            this.fieldsMapping = fieldsMapping;
            this.vaultUrl = vaultUrl;
            this.vaultRole = vaultRole;
            this.vaultCredentialPath = vaultCredentialPath;
            this.vaultNamespace = vaultNamespace;
            this.serviceAccount = serviceAccount;
        }

        public String getVaultUrl() {
            return this.vaultUrl;
        }
    
        public String getVaultRole() {
            return this.vaultRole;
        }

        public String getVaultCredentialPath() {
            return this.vaultCredentialPath;
        }

        public String getVaultNamespace() {
            return this.vaultNamespace;
        }

        public boolean isVaultNamespace() {
            return !Strings.isNullOrEmpty(this.vaultNamespace);
        }

        public String getServiceAccount() {
            return this.serviceAccount;
        }
    }

    
    public CloudVault(VaultDataConf config) {
      this.config = config;
    }
    
    @Override
    public void configurePipeline(PipelineConfigurer pipelineConfigurer) {
      config.validate();
    }

    @Override
    public void run(ActionContext context) throws Exception {
        config.validate();

       	JsonObject credentials;
  		credentials = VaultHelper.retrieveCredential(config).get("data").asObject();

        HashMap<String, String> mapping = createKeyValuePairs(config.fieldsMapping);

        for (Map.Entry<String, String> mapEntry : mapping.entrySet()) {
            String vaultName = mapEntry.getKey();
            String argName = mapEntry.getValue();
            String value = credentials.getString(vaultName);
            if(Strings.isNullOrEmpty(value)) {
                LOG.warn("Vault Column Name {} hadn't been found. {} will be assigned to string empty", vaultName, argName);
                value = "";
            }
            context.getArguments().set(argName, value);
        }
    }
    
    /**
     * A helper method to split up the parameters passed through the plugin's UI. Strings are split by "," first to
     * split out key-value pairs. Each of the pairs are then split by ":" and added to a HashMap.
     *
     * @param parameterString the configuration string split by a "," for key-value pairs. The key-value pairs are
     *                        additionally split by a ":"
     * @return a HashMap of the Vault Column Name - Argument Column Name
     */
    private HashMap<String, String> createKeyValuePairs(String parameterString) {
        HashMap<String, String> hashMap = new HashMap<>();
        String[] mappings = parameterString.split(",");
        for (String string : mappings) {
            String[] mapping = string.split(":");
            hashMap.put(mapping[0], mapping[1]);
        }

        return hashMap;
    }
}
