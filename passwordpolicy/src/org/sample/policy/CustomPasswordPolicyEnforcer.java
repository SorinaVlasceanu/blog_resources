package org.sample.policy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.mgt.policy.AbstractPasswordPolicyEnforcer;
import org.wso2.carbon.identity.mgt.policy.password.DefaultPasswordLengthPolicy;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class CustomPasswordPolicyEnforcer extends AbstractPasswordPolicyEnforcer {

    private static final Log log = LogFactory.getLog(DefaultPasswordLengthPolicy.class);
    private String policyPattern;


    @Override
    public boolean enforce(Object... args) {
        //check the policyPattern at the user adding as well, same done at the server initialization as well.
        if (policyPattern == null) {
            errorMessage = "Failed to initialize the PasswordPolicy handler because policy pattern is null ";
            log.error(errorMessage);
            return false;
        }
        //can't add empty password
        if (args == null || args.length == 0) {
            errorMessage = "Password can't be empty";
            log.info(errorMessage);
            return false;
        }
        //match the user password with the password policy.
        String password = args[0].toString();
        Pattern pattern = Pattern.compile(policyPattern);
        Matcher matcher = pattern.matcher(password);
        boolean result = matcher.matches();

        if (!result) {
            errorMessage = "Password should have one upper case, one lowercase and one digit and special character";
            log.warn(errorMessage);
        }
        return result;
    }

    @Override
    public void init(Map<String, String> properties) {
        if (properties != null && properties.size() > 0) {
            policyPattern = properties.get("pattern");
            //Check at the server startup
            if (policyPattern == null) {
                log.error("Failed to initialize the PasswordPolicy handler because policy pattern is null ");
            }
        }
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }
}

