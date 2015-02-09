package org.sample.jaxws.client;

import com.sun.xml.internal.ws.transport.Headers;
import org.wso2.auth.jaxws.client.AuthenticationAdmin;
import org.wso2.auth.jaxws.client.AuthenticationAdminPortType;

import javax.xml.ws.BindingProvider;
import javax.xml.ws.handler.MessageContext;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by ajith on 2/8/15.
 */
public class LoginClient {

    public static void main(String[] args) {
        System.setProperty("javax.net.ssl.trustStore", "<product_home>/repository/resources/security/client-truststore.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "wso2carbon");
        AuthenticationAdmin authenticationAdmin = new AuthenticationAdmin();
        AuthenticationAdminPortType portType = authenticationAdmin.getAuthenticationAdminHttpsSoap11Endpoint();

        try {
            portType.login("admin", "admin", "localhost");
            Headers headers = (Headers) ((BindingProvider) portType).getResponseContext().get(MessageContext.HTTP_RESPONSE_HEADERS);
            List<String> cookie = headers.get("Set-Cookie");
            Map<String, List<String>> requestHeaders = (Map) ((BindingProvider) portType).getResponseContext().get(MessageContext.HTTP_REQUEST_HEADERS);

            if (requestHeaders == null) {
                requestHeaders = new HashMap<String, List<String>>();
            }
            requestHeaders.put("Cookie", Collections.singletonList(cookie.get(0)));
            ((BindingProvider) portType).getRequestContext().put(MessageContext.HTTP_REQUEST_HEADERS, requestHeaders);

            portType.logout();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
