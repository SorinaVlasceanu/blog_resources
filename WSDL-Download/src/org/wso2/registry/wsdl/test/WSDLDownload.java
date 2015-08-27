/*
*  Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.registry.wsdl.test;

import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.ConfigurationContextFactory;
import org.apache.commons.io.FileUtils;
import org.wso2.carbon.governance.api.generic.GenericArtifactManager;
import org.wso2.carbon.governance.api.generic.dataobjects.GenericArtifact;
import org.wso2.carbon.governance.api.util.GovernanceUtils;
import org.wso2.carbon.registry.core.Association;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.session.UserRegistry;
import org.wso2.carbon.registry.ws.client.registry.WSRegistryServiceClient;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class WSDLDownload {

    private static final String CARBON_HOME = "/home/ajith/wso2/support/wso2greg-4.6.0";
    private static final String username = "admin";
    private static final String password = "admin";
    private static final String serverURL = "https://localhost:9443/services/";
    private static final String zipFilesLocation = "/home/ajith/";
    private static final String compressMethod = ".zip";

    private static WSRegistryServiceClient initialize() throws Exception {

        System.setProperty("javax.net.ssl.trustStore", CARBON_HOME + File.separator + "repository" +
                File.separator + "resources" + File.separator + "security" + File.separator +
                "wso2carbon.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "wso2carbon");
        System.setProperty("javax.net.ssl.trustStoreType", "JKS");
        System.setProperty("carbon.repo.write.mode", "true");
        ConfigurationContext configContext = ConfigurationContextFactory.createConfigurationContextFromFileSystem(
                null, null);
        return new WSRegistryServiceClient(serverURL, username, password, configContext);
    }

    public static void main(String[] args) throws Exception {
        //Initialize registry
        Registry registry = initialize();
        //initialize governance registry
        Registry gov = GovernanceUtils.getGovernanceUserRegistry(registry, "admin");
        //load governance artifacts
        GovernanceUtils.loadGovernanceArtifacts((UserRegistry) gov);
        GenericArtifactManager manager = new GenericArtifactManager(gov, "wsdl");
        //get all the wsdl files
        GenericArtifact[] genericArtifacts = manager.getAllGenericArtifacts();

        for (GenericArtifact wsdlArtifact : genericArtifacts) {

            String wsdlPath = wsdlArtifact.getPath();
            Association[] associations = gov.getAssociations(wsdlArtifact.getPath(), "depends");

            Resource resource = gov.get(wsdlArtifact.getPath());
            File wsdlFileDir = new File(zipFilesLocation + wsdlPath.substring(wsdlPath.lastIndexOf("/")).replace(".wsdl", ""));
            if (!wsdlFileDir.exists()) {
                wsdlFileDir.mkdir();
            }

            byte[] bytes = (byte[]) resource.getContent();
            String content = new String(bytes);

            if (content.contains("../../../../../../../schemas/")) {
                content = content.replace("../../../../../../../schemas/", "");
            }

            File wsdlFile = new File(wsdlFileDir.getPath() + "/" + wsdlPath.substring(wsdlPath.lastIndexOf("/")));
            // if file doesn't exists, then create it
            if (!wsdlFile.exists()) {
                boolean status = wsdlFile.createNewFile();
            }
            FileWriter fw = null;
            BufferedWriter bw = null;

            try {
                fw = new FileWriter(wsdlFile.getAbsoluteFile());
                bw = new BufferedWriter(fw);
                bw.write(content);


            } finally {
                if (bw != null) {
                    bw.close();
                }
                if (fw != null) {
                    fw.close();
                }

            }


            for (Association association : associations) {
                //Find the XSD files from all the WSDL dependencies
                if (association.getDestinationPath().endsWith(".xsd")) {
                    String registryXSDLocation = association.getDestinationPath();
                    //get content of XSD
                    Resource xsdContent = gov.get(registryXSDLocation);
                    byte[] bytes1 = (byte[]) xsdContent.getContent();
                    String xsdContentString = new String(bytes1);

                    String pathWithNameSpace = registryXSDLocation.replace("/trunk/schemas/", "");

                    String xsdDirPath = pathWithNameSpace.substring(0, pathWithNameSpace.lastIndexOf("/"));
                    File fileXsdDirs = new File(wsdlFileDir.getPath() + "/" + xsdDirPath);
                    if (!fileXsdDirs.exists()) {
                        fileXsdDirs.mkdirs();
                    }

                    File xsdFile = new File(wsdlFileDir.getPath() + "/" + pathWithNameSpace);
                    if (!xsdFile.exists()) {
                        boolean status = xsdFile.createNewFile();
                    }
                    FileWriter xsdfw = null;
                    BufferedWriter xsdbw = null;
                    try {
                        xsdfw = new FileWriter(xsdFile.getAbsoluteFile());
                        xsdbw = new BufferedWriter(xsdfw);
                        xsdbw.write(xsdContentString);

                    } finally {
                        if (xsdbw != null) {
                            xsdbw.close();
                        }
                        if (xsdfw != null) {
                            xsdfw.close();
                        }

                    }
                }
            }
            zipWSDLDirectory(wsdlFileDir.getPath(), wsdlFileDir.getPath() + compressMethod);
            FileUtils.deleteDirectory(wsdlFileDir);

            System.out.println("WSDL archive created at : " + wsdlFileDir.getPath() + compressMethod);

        }
    }

    private static void zipWSDLDirectory(String srcFolder, String destZipFile) throws Exception {
        ZipOutputStream zip = null;
        FileOutputStream fileWriter = null;

        try {
            fileWriter = new FileOutputStream(destZipFile);
            zip = new ZipOutputStream(fileWriter);

            addDirectoryToZip("", srcFolder, zip);
            zip.flush();

        } finally {
            if (zip != null) {
                zip.close();
            }
            if (fileWriter != null) {
                fileWriter.close();
            }

        }
    }

    private static void addFileToWSDLZip(String path, String srcFile, ZipOutputStream zip)
            throws Exception {

        File folder = new File(srcFile);
        if (folder.isDirectory()) {
            addDirectoryToZip(path, srcFile, zip);
        } else {
            byte[] buf = new byte[1024];
            int len;
            zip.putNextEntry(new ZipEntry(path + File.separator + folder.getName()));
            try (FileInputStream in = new FileInputStream(srcFile)) {
                while ((len = in.read(buf)) > 0) {
                    zip.write(buf, 0, len);
                }
            }
        }
    }

    private static void addDirectoryToZip(String path, String srcFolder, ZipOutputStream zip)
            throws Exception {
        File folder = new File(srcFolder);

        for (String fileName : folder.list()) {
            if (path.equals("")) {
                addFileToWSDLZip(folder.getName(), srcFolder + File.separator + fileName, zip);
            } else {
                addFileToWSDLZip(path + File.separator + folder.getName(), srcFolder + File.separator + fileName, zip);
            }
        }
    }
}
