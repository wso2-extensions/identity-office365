/*
 *  Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.wso2.carbon.identity.outbound.provisioning.connector.office365;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.application.common.model.Property;
import org.wso2.carbon.identity.provisioning.AbstractOutboundProvisioningConnector;
import org.wso2.carbon.identity.provisioning.AbstractProvisioningConnectorFactory;
import org.wso2.carbon.identity.provisioning.IdentityProvisioningException;

import java.util.ArrayList;
import java.util.List;

public class Office365ProvisioningConnectorFactory extends AbstractProvisioningConnectorFactory {

    private static final Log log = LogFactory.getLog(Office365ProvisioningConnectorFactory.class);
    private static final String CONNECTOR_TYPE = "Office365";

    @Override
    protected AbstractOutboundProvisioningConnector buildConnector(
            Property[] provisioningProperties) throws IdentityProvisioningException {
        Office365ProvisioningConnector connector = new Office365ProvisioningConnector();
        connector.init(provisioningProperties);
        if (log.isDebugEnabled()) {
            log.debug("Office365 provisioning connector created successfully.");
        }
        return connector;
    }

    @Override
    public String getConnectorType() {
        return CONNECTOR_TYPE;
    }

    @Override
    public List<Property> getConfigurationProperties() {
        List<Property> properties = new ArrayList<>();

        Property username = new Property();
        username.setName(Office365ConnectorConstants.OFFICE365_CLIENT_ID);
        username.setDisplayName("Client ID");
        username.setDisplayOrder(1);
        username.setRequired(true);

        Property userPassword = new Property();
        userPassword.setName(Office365ConnectorConstants.OFFICE365_CLIENT_SECRET);
        userPassword.setDisplayName("Client Secret");
        userPassword.setConfidential(true);
        userPassword.setDisplayOrder(2);
        userPassword.setRequired(true);

        Property userEndpoint = new Property();
        userEndpoint.setName(Office365ConnectorConstants.OFFICE365_NAME_ID);
        userEndpoint.setDisplayName("NameId");
        userEndpoint.setDisplayOrder(3);
        userEndpoint.setRequired(true);

        Property groupEndpoint = new Property();
        groupEndpoint.setName(Office365ConnectorConstants.OFFICE365_IDP_EMAIL);
        groupEndpoint.setDisplayName("IDPEmail");
        groupEndpoint.setDisplayOrder(4);
        userEndpoint.setRequired(true);

        properties.add(username);
        properties.add(userPassword);
        properties.add(userEndpoint);
        properties.add(groupEndpoint);

        return properties;
    }

}
