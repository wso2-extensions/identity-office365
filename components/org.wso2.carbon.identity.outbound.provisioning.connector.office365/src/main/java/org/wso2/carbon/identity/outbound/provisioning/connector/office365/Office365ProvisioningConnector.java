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
import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.wso2.carbon.identity.application.common.model.Property;
import org.wso2.carbon.identity.provisioning.AbstractOutboundProvisioningConnector;
import org.wso2.carbon.identity.provisioning.IdentityProvisioningConstants;
import org.wso2.carbon.identity.provisioning.IdentityProvisioningException;
import org.wso2.carbon.identity.provisioning.ProvisionedIdentifier;
import org.wso2.carbon.identity.provisioning.ProvisioningEntity;
import org.wso2.carbon.identity.provisioning.ProvisioningEntityType;
import org.wso2.carbon.identity.provisioning.ProvisioningOperation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class Office365ProvisioningConnector extends AbstractOutboundProvisioningConnector {

    private static final Log log = LogFactory.getLog(Office365ProvisioningConnector.class);
    private Office365ProvisioningConnectorConfig configHolder;

    @Override
    public void init(Property[] provisioningProperties) throws IdentityProvisioningException {
        Properties configs = new Properties();

        if (provisioningProperties != null && provisioningProperties.length > 0) {
            for (Property property : provisioningProperties) {
                //Add your code to add property to the configHolder
                configs.put(property.getName(), property.getValue());
                if (IdentityProvisioningConstants.JIT_PROVISIONING_ENABLED.equals(property
                        .getName())) {
                    if ("1".equals(property.getValue())) {
                        jitProvisioningEnabled = true;
                    }
                }
            }
        }

        configHolder = new Office365ProvisioningConnectorConfig(configs);
    }

    @Override
    public ProvisionedIdentifier provision(ProvisioningEntity provisioningEntity)
            throws IdentityProvisioningException {
        String provisionedId = null;

        if (provisioningEntity != null) {

            if (provisioningEntity.isJitProvisioning() && !isJitProvisioningEnabled()) {
                log.debug("JIT provisioning disabled for Office365 connector");
                return null;
            }

            if (provisioningEntity.getEntityType() == ProvisioningEntityType.USER) {
                if (provisioningEntity.getOperation() == ProvisioningOperation.DELETE) {
                    deleteUser();
                } else if (provisioningEntity.getOperation() == ProvisioningOperation.POST) {
                    provisionedId = createUser(provisioningEntity);
                } else if (provisioningEntity.getOperation() == ProvisioningOperation.PUT) {
                    updateUser();
                } else {
                    log.warn("Unsupported provisioning operation.");
                }
            } else {
                log.warn("Unsupported provisioning operation.");
            }
        }

        // creates a provisioned identifier for the provisioned user.
        ProvisionedIdentifier identifier = new ProvisionedIdentifier();
        identifier.setIdentifier(provisionedId);
        return identifier;
    }

    private String createUser(ProvisioningEntity provisioningEntity) throws IdentityProvisioningException {

        boolean isDebugEnabled = log.isDebugEnabled();
        String provisionedId = null;

        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build()) {
            JSONObject user = buildUserAsJson(provisioningEntity);

            HttpPost post = new HttpPost(Office365ConnectorConstants.OFFICE365_CREATE_USER_ENDPOINT);
            setAuthorizationHeader(post);

            StringEntity requestBody = new StringEntity(user.toString());
            requestBody.setContentType(Office365ConnectorConstants.CONTENT_TYPE_APPLICATION_JSON);
            post.setEntity(requestBody);
            post.setHeader(Office365ConnectorConstants.CONTENT_TYPE, Office365ConnectorConstants
                    .CONTENT_TYPE_APPLICATION_JSON);

            try (CloseableHttpResponse response = httpclient.execute(post)) {

                if (isDebugEnabled) {
                    log.debug("HTTP status " + response.getStatusLine().getStatusCode() + " creating user");
                }

                JSONObject jsonResponse = new JSONObject(new JSONTokener(new InputStreamReader(
                        response.getEntity().getContent())));

                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_CREATED) {
                    provisionedId = jsonResponse.getString("id");

                    if (isDebugEnabled) {
                        log.debug("New record id " + provisionedId);
                    }

                } else {
                    String errorMessage = jsonResponse.getJSONObject("error").getString("message");
                    log.error("Received response status code: " + response.getStatusLine().getStatusCode() + " "
                            + response.getStatusLine().getReasonPhrase() + " with the message '" + errorMessage + "'");

                    if (isDebugEnabled) {
                        log.error("Request which cause the error : " + readResponse(post));
                    }

                }
            } catch (IOException | JSONException e) {
                throw new IdentityProvisioningException("Error in invoking provisioning operation for the user", e);
            } finally {
                post.releaseConnection();
            }

            if (isDebugEnabled) {
                log.debug("Returning provisioned user's ID: " + provisionedId);
            }

        } catch (IOException e) {
            log.error("Error while closing HttpClient.");
        }
        return provisionedId;
    }

    private void updateUser() {
        log.info("Update user");
        // TODO: 8/14/18 Implement update user logic
    }

    private void deleteUser() {
        log.info("Delete user");
        // TODO: 8/14/18 Implement delete user logic
    }

    /**
     * Get an access token to call Microsoft Graph APIs
     *
     * @return token    Access token as a string
     * @throws IdentityProvisioningException If the access token can not be obtained from the API
     */
    private String getAccessToken() throws IdentityProvisioningException {
        boolean isDebugEnabled = log.isDebugEnabled();

        String clientId = this.configHolder.getValue(Office365ConnectorConstants.OFFICE365_CLIENT_ID);
        String clientSecret = this.configHolder.getValue(Office365ConnectorConstants.OFFICE365_CLIENT_SECRET);
        String tenantName = this.configHolder.getValue(Office365ConnectorConstants.OFFICE365_TENANT);

        // Generate the endpoint using the base url and the user's tenant name
        String tokenGrantUrl = Office365ConnectorConstants.OFFICE365_BASE_URL + "/" + tenantName +
                Office365ConnectorConstants.OFFICE365_TOKEN_ENDPOINT;

        String accessToken = null;
        try (CloseableHttpClient httpclient = HttpClientBuilder.create().useSystemProperties().build()) {

            // Define the path parameters of the access token grant endpoint
            List<NameValuePair> urlParameters = new ArrayList<>();
            urlParameters.add(new BasicNameValuePair(Office365ConnectorConstants.OFFICE365_CLIENT_ID, clientId));
            urlParameters.add(new BasicNameValuePair(Office365ConnectorConstants.OFFICE365_OAUTH_SCOPE,
                    "https://graph.microsoft.com/.default"));
            urlParameters.add(new BasicNameValuePair(Office365ConnectorConstants.OFFICE365_CLIENT_SECRET,
                    clientSecret));
            urlParameters.add(new BasicNameValuePair(Office365ConnectorConstants.OFFICE365_OAUTH_GRANT,
                    Office365ConnectorConstants.CLIENT_CREDENTIALS));

            // Create the post request.
            HttpPost post = new HttpPost(tokenGrantUrl);
            post.setHeader(Office365ConnectorConstants.CONTENT_TYPE,
                    Office365ConnectorConstants.CONTENT_TYPE_FORM_URLENCODED);
            post.setEntity(new UrlEncodedFormEntity(urlParameters));

            try (CloseableHttpResponse response = httpclient.execute(post)) {

                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {

                    JSONObject jsonResponse = new JSONObject(
                            new JSONTokener(new InputStreamReader(response.getEntity().getContent())));
                    accessToken = jsonResponse.getString("access_token");

                    if (isDebugEnabled) {
                        log.debug("A valid Access token is received for the tenant " + tenantName);
                    }
                } else {
                    log.error("Received response status code: " + response.getStatusLine().getStatusCode() + " text: "
                            + response.getStatusLine().getReasonPhrase());

                    if (isDebugEnabled) {
                        log.debug("Error response : " + readResponse(post));
                    }
                }
            } catch (IOException | JSONException e) {
                throw new IdentityProvisioningException("Error while obtaining the access token from the response.", e);
            } finally {
                post.releaseConnection();
            }
        } catch (IOException e) {
            log.error("Error while closing HttpClient.");
        }
        return accessToken;
    }

    private JSONObject buildUserAsJson(ProvisioningEntity provisioningEntity) {

        Map<String, String> requiredAttributes = getSingleValuedClaims(provisioningEntity.getAttributes());

        String displayNameClaim = this.configHolder.getValue(Office365ConnectorConstants.OFFICE365_DISPLAY_NAME);
        String mailNickNameClaim = this.configHolder.getValue(Office365ConnectorConstants.OFFICE365_EMAIL_NICKNAME);
        String upnClaim = this.configHolder.getValue(Office365ConnectorConstants.OFFICE365_UPN);
        String immutableIdClaim = this.configHolder.getValue(Office365ConnectorConstants.OFFICE365_IMMUTABLE_ID);
        Boolean enableDomain = Boolean.parseBoolean(this.configHolder.getValue(Office365ConnectorConstants
                .OFFICE365_ENABLE_DOMAIN));
        String domainName = this.configHolder.getValue(Office365ConnectorConstants.OFFICE365_DOMAIN);

        String displayName = requiredAttributes.get(displayNameClaim);
        String mailNickName = requiredAttributes.get(mailNickNameClaim);
        String immutableId = requiredAttributes.get(immutableIdClaim);
        String upn = requiredAttributes.get(upnClaim);

        // Append the domain name at the end of the claim which given as the user principal name.
        if (enableDomain && !upn.endsWith("@"+domainName)) {
            upn = upn + "@" + domainName;
        }

        // Create a json object corresponding to the attributes of the user in the request.
        JSONObject passwordProfile = new JSONObject();
        passwordProfile.put(Office365ConnectorConstants.FORCE_CHANGE_PASSWORD, false);
        passwordProfile.put(Office365ConnectorConstants.PASSWORD, "IiLCJhcHBpZCI6ImMw");

        JSONObject user = new JSONObject();
        user.put(Office365ConnectorConstants.ACCOUNT_ENABLED, true);
        user.put(Office365ConnectorConstants.OFFICE365_DISPLAY_NAME, displayName);
        user.put(Office365ConnectorConstants.OFFICE365_EMAIL_NICKNAME, mailNickName);
        user.put(Office365ConnectorConstants.OFFICE365_UPN, upn);
        user.put(Office365ConnectorConstants.OFFICE365_IMMUTABLE_ID, immutableId);
        user.put(Office365ConnectorConstants.PASSWORD_PROFILE, passwordProfile);

        if(log.isDebugEnabled()){
            log.debug("An user object is created. " + user.toString());
        }
        return user;
    }

    private void setAuthorizationHeader(HttpRequestBase httpMethod) throws IdentityProvisioningException {

        boolean isDebugEnabled = log.isDebugEnabled();

        String accessToken = getAccessToken();

        if (!accessToken.isEmpty()) {
            httpMethod.addHeader(Office365ConnectorConstants.AUTHORIZATION_HEADER_NAME,
                    Office365ConnectorConstants.AUTHORIZATION_HEADER_BEARER + " " + accessToken);

            if (isDebugEnabled) {
                log.debug("Setting authorization header for method: " + httpMethod.getMethod() + " as follows,");
                Header authorizationHeader = httpMethod
                        .getLastHeader(Office365ConnectorConstants.AUTHORIZATION_HEADER_NAME);
                log.debug(authorizationHeader.getName() + ": " + authorizationHeader.getValue());
            }
        } else {
            throw new IdentityProvisioningException("Authentication failed");
        }
    }

    private String readResponse(HttpPost post) throws IOException {
        try (InputStream is = post.getEntity().getContent()) {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            String line;
            StringBuilder response = new StringBuilder();
            while ((line = rd.readLine()) != null) {
                response.append(line);
                response.append('\r');
            }
            rd.close();
            return response.toString();
        }
    }

}

