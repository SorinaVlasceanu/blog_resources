/*
*Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*WSO2 Inc. licenses this file to you under the Apache License,
*Version 2.0 (the "License"); you may not use this file except
*in compliance with the License.
*You may obtain a copy of the License at
*
*http://www.apache.org/licenses/LICENSE-2.0
*
*Unless required by applicable law or agreed to in writing,
*software distributed under the License is distributed on an
*"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*KIND, either express or implied.  See the License for the
*specific language governing permissions and limitations
*under the License.
*/
package org.wso2.sample.ws.client;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMNamespace;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.axis2.AxisFault;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.ConfigurationContextFactory;
import org.apache.neethi.Policy;
import org.apache.neethi.PolicyEngine;
import org.apache.rampart.RampartMessageData;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileNotFoundException;


public class StockQuoteSecureClient {
    public static void main(String[] args) throws AxisFault {

        ServiceClient serviceClient;
        ConfigurationContext configContext;
        OMElement payload;
        String carbon_home = "/home/ajith/packs/wso2esb-4.8.1";
        String symbol = "IBM";
        String proxyEndpoint = "https://localhost:8243/services/SimpleStockQuoteServiceProxy";

        System.setProperty("javax.net.ssl.trustStore", carbon_home + File.separator + "repository" +
                File.separator + "resources" + File.separator + "security" + File.separator + "wso2carbon.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "wso2carbon");
        System.setProperty("javax.net.ssl.trustStoreType", "JKS");

        try {
            configContext =
                    ConfigurationContextFactory.
                            createConfigurationContextFromFileSystem(carbon_home,
                                    carbon_home + File.separator + "repository" + File.separator + "conf" + File.separator + "axis2" + File.separator + "axis2_client.xml");
            serviceClient = new ServiceClient(configContext, null);

            Options options = new Options();
            payload = createStandardQuoteRequest(symbol, 1);
            options.setAction("urn:getQuote");
            serviceClient.engageModule("addressing");
            options.setTo(new EndpointReference(proxyEndpoint));
            serviceClient.engageModule("rampart");
            options.setProperty(
                    RampartMessageData.KEY_RAMPART_POLICY, loadPolicy(carbon_home + File.separator + "policy.xml"));
            options.setUserName("admin");
            options.setPassword("admin");
            serviceClient.setOptions(options);
            //serviceClient.sendReceiveNonBlocking(payload, new StockQuoteCallback());
            OMElement response = serviceClient.sendReceive(payload);
            System.out.println(response.toString());


        } catch (AxisFault axisFault) {
            throw new AxisFault("Failed to call service", axisFault);

        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    public static OMElement createStandardQuoteRequest(String symbol, int itrCount) {
        OMFactory factory = OMAbstractFactory.getOMFactory();
        OMNamespace ns = factory.createOMNamespace("http://services.samples", "m0");
        OMElement getQuote = factory.createOMElement("getQuote", ns);
        for (int i = 0; i < itrCount; i++) {
            OMElement request = factory.createOMElement("request", ns);
            OMElement symb = factory.createOMElement("symbol", ns);
            request.addChild(symb);
            getQuote.addChild(request);
            symb.setText(symbol);
        }
        return getQuote;
    }

    private static Policy loadPolicy(String xmlPath) {
        StAXOMBuilder builder = null;
        Policy policy = null;
        try {
            builder = new StAXOMBuilder(xmlPath);
        } catch (XMLStreamException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        if (builder != null) {
            policy = PolicyEngine.getPolicy(builder.getDocumentElement());
        }
        return policy;
    }

}
