/*
 * Copyright (c) 2006, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.sample.securevault;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.securevault.SecretResolver;
import org.wso2.securevault.SecretResolverFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ModuleProperties {
    private static final Log log = LogFactory.getLog(ModuleConf.class);

    private String username;
    private String password;
    private String serverURL;
    private String remote;

    public ModuleProperties() {
        FileInputStream fileInputStream = null;

        //Assumed that configuration file(myconf-test.properties) is under the <PRODUCT_HOME>/repository/conf
        String configPath = CarbonUtils.getCarbonHome() + File.separator + "repository" + File.separator + "conf" +
                File.separator + "myconf-test.properties";

        File registryXML = new File(configPath);
        if (registryXML.exists()) {
            try {
                fileInputStream = new FileInputStream(registryXML);

                Properties properties = new Properties();
                properties.load(fileInputStream);
                SecretResolver secretResolver = SecretResolverFactory.create(properties);
                //Resolved the secret password.
                String secretAlias = "myconf.module.password";

                if (secretResolver != null && secretResolver.isInitialized()) {
                    if (secretResolver.isTokenProtected(secretAlias)) {
                        password = secretResolver.resolve(secretAlias);
                    } else {
                        password = (String) properties.get(secretAlias);
                    }
                }
                username = (String) properties.get("myconf.module.username");
                serverURL = (String) properties.get("myconf.module.serverUrl");
                remote = (String) properties.get("myconf.module.remote");

            } catch (IOException e) {
                log.error("Unable to read myconf-test.properties", e);
            } finally {
                if (fileInputStream != null) {
                    try {
                        fileInputStream.close();
                    } catch (IOException e) {
                        log.error("Failed to close the FileInputStream, file : " + configPath);
                    }
                }
            }
        }
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getServerURL() {
        return serverURL;
    }

    public boolean isRemote() {
        return Boolean.valueOf(remote);
    }
}
