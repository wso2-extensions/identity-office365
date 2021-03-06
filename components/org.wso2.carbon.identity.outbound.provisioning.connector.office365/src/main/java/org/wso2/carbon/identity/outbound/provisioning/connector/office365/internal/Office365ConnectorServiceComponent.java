/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.outbound.provisioning.connector.office365.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.wso2.carbon.identity.outbound.provisioning.connector.office365.Office365ProvisioningConnectorFactory;
import org.wso2.carbon.identity.provisioning.AbstractProvisioningConnectorFactory;

/**
 * Registers the connector as an osgi component.
 */
@Component(
        name = "identity.outbound.provisioning.office365.component",
        immediate = true
)
public class Office365ConnectorServiceComponent {

    private static final Log log = LogFactory.getLog(Office365ConnectorServiceComponent.class);

    @Activate
    protected void activate(ComponentContext context) {
        if (log.isDebugEnabled()) {
            log.debug("Activating Office365ConnectorServiceComponent");
        }
        try {
            Office365ProvisioningConnectorFactory provisioningConnectorFactory = new
                    Office365ProvisioningConnectorFactory();
            context.getBundleContext().registerService(AbstractProvisioningConnectorFactory.class.getName(),
                    provisioningConnectorFactory, null);
            if (log.isDebugEnabled()) {
                log.debug("Office365 Identity Provisioning Connector bundle is activated");
            }
        } catch (Throwable e) {
            log.error("Error while activating Office365 Identity Provisioning Connector ", e);
        }
    }
}
