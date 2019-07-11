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

package org.wso2.carbon.identity.outbound.provisioning.connector.office365;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
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
import org.wso2.carbon.identity.base.IdentityConstants;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.provisioning.AbstractOutboundProvisioningConnector;
import org.wso2.carbon.identity.provisioning.IdentityProvisioningConstants;
import org.wso2.carbon.identity.provisioning.IdentityProvisioningException;
import org.wso2.carbon.identity.provisioning.ProvisionedIdentifier;
import org.wso2.carbon.identity.provisioning.ProvisioningEntity;
import org.wso2.carbon.identity.provisioning.ProvisioningEntityType;
import org.wso2.carbon.identity.provisioning.ProvisioningOperation;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * This class handles the Office365 user provisioning operations to Azure AD.
 */
public class Office365ProvisioningConnector extends AbstractOutboundProvisioningConnector {

    private static final Log log = LogFactory.getLog(Office365ProvisioningConnector.class);
    private Office365ProvisioningConnectorConfig configHolder;

    @Override
    public void init(Property[] provisioningProperties) throws IdentityProvisioningException {

        Properties configs = new Properties();

        if (provisioningProperties != null && provisioningProperties.length > 0) {
            for (Property property : provisioningProperties) {
                configs.put(property.getName(), property.getValue());
                if (IdentityProvisioningConstants.JIT_PROVISIONING_ENABLED.equals(property
                        .getName())) {
                    if (Office365ConnectorConstants.PROPERTY_VALUE_TRUE.equals(property.getValue())) {
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
                if (log.isDebugEnabled()) {
                    log.debug("JIT provisioning disabled for Office365 connector.");
                }
                return null;
            }

            if (ProvisioningEntityType.USER == provisioningEntity.getEntityType()) {
                if (ProvisioningOperation.DELETE == provisioningEntity.getOperation()) {
                    deleteUser(provisioningEntity);
                    deleteUserPermanently(provisioningEntity);
                } else if (ProvisioningOperation.POST == provisioningEntity.getOperation()) {
                    provisionedId = createUser(provisioningEntity);
                } else if (ProvisioningOperation.PUT == provisioningEntity.getOperation()) {
                    updateUser(provisioningEntity);
                } else {
                    log.warn("Unsupported provisioning operation " + provisioningEntity.getOperation() +
                            " for entity type " + provisioningEntity.getEntityType());
                }
            } else {
                log.warn("Unsupported provisioning entity type " + provisioningEntity.getEntityType());
            }
        }

        // Creates a provisioned identifier for the provisioned user.
        ProvisionedIdentifier identifier = new ProvisionedIdentifier();
        identifier.setIdentifier(provisionedId);
        return identifier;
    }

    /**
     * Call the create user endpoint of Azure AD and provision the user.
     *
     * @param provisioningEntity user to be provisioned
     * @return string id for the provisioned user
     * @throws IdentityProvisioningException if the user can not be created in the Azure AD
     */
    protected String createUser(ProvisioningEntity provisioningEntity) throws IdentityProvisioningException {

        String provisionedId = null;

        try (CloseableHttpClient httpclient = HttpClientBuilder.create().useSystemProperties().build()) {

            JSONObject user = buildUserAsJson(provisioningEntity);
            HttpPost post = new HttpPost(Office365ConnectorConstants.OFFICE365_USER_ENDPOINT);
            setAuthorizationHeader(post);

            StringEntity requestBody = new StringEntity(user.toString());
            requestBody.setContentType(Office365ConnectorConstants.CONTENT_TYPE_APPLICATION_JSON);
            post.setEntity(requestBody);
            post.setHeader(Office365ConnectorConstants.CONTENT_TYPE, Office365ConnectorConstants
                    .CONTENT_TYPE_APPLICATION_JSON);

            try (CloseableHttpResponse response = httpclient.execute(post)) {

                JSONObject jsonResponse = new JSONObject(new JSONTokener(new InputStreamReader(
                        response.getEntity().getContent())));
                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_CREATED) {
                    provisionedId = jsonResponse.getString("id");

                    if (log.isDebugEnabled()) {
                        log.debug("Successfully created an user in the Azure Active Directory. Server responds with " +
                                jsonResponse.toString());
                    }
                } else {
                    String errorMessage = jsonResponse.getJSONObject("error").getString("message");
                    log.error("Received response status code: " + response.getStatusLine().getStatusCode() + " "
                            + response.getStatusLine().getReasonPhrase() + " with the message '" + errorMessage +
                            "' while creating the user " + user.getString(Office365ConnectorConstants.OFFICE365_UPN) +
                            " in the Azure Active Directory.");

                    if (log.isDebugEnabled()) {
                        log.debug("The response received from server : " + jsonResponse.toString());
                    }
                }
            } catch (IOException | JSONException e) {
                throw new IdentityProvisioningException("Error while executing the create operation in user " +
                        "provisioning", e);
            }

            if (log.isDebugEnabled()) {
                log.debug("Returning provisioned user's ID: " + provisionedId);
            }
        } catch (IOException e) {
            log.error("Error while closing HttpClient.");
        }
        return provisionedId;
    }

    protected void updateUser(ProvisioningEntity provisioningEntity) {

        log.warn("Update user is not implemented.");
        // TODO: 8/14/18 Implement update user logic
    }

    /**
     * Delete provisioned users from the Azure AD.
     *
     * @param provisioningEntity the user being removed
     * @throws IdentityProvisioningException if the user deletion is failed.
     */
    protected void deleteUser(ProvisioningEntity provisioningEntity) throws IdentityProvisioningException {

        // Get the provisioned id of deleted user. (Unassigned role)
        // User's UPN can not be considered here because if the user himself is deleted, UPN will be null.
        String provisionedUserId = provisioningEntity.getIdentifier().getIdentifier();

        try (CloseableHttpClient httpclient = HttpClientBuilder.create().useSystemProperties().build()) {

            String deleteUserEndpoint = Office365ConnectorConstants.OFFICE365_USER_ENDPOINT + '/' + provisionedUserId;
            HttpDelete delete = new HttpDelete(deleteUserEndpoint);
            setAuthorizationHeader(delete);

            try (CloseableHttpResponse response = httpclient.execute(delete)) {

                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NO_CONTENT) {
                    if (log.isDebugEnabled()) {
                        log.debug("Successfully deleted the provisioned user with id " + provisionedUserId + " from " +
                                "the Azure Active Directory");
                    }
                } else {
                    JSONObject jsonResponse = new JSONObject(new JSONTokener(new InputStreamReader(
                            response.getEntity().getContent())));
                    String errorMessage = jsonResponse.getJSONObject("error").getString("message");

                    log.error("Received response status code: " + response.getStatusLine().getStatusCode() + " "
                            + response.getStatusLine().getReasonPhrase() + " with the message '" + errorMessage +
                            "' while deleting the user with id " + provisionedUserId + " from the Azure Active " +
                            "Directory.");

                    if (log.isDebugEnabled()) {
                        log.debug("The response received from server : " + jsonResponse.toString());
                    }
                }
            } catch (IOException | JSONException e) {
                throw new IdentityProvisioningException("Error while executing the delete operation in user " +
                        "provisioning", e);
            }
        } catch (IOException e) {
            log.error("Error while closing HttpClient.");
        }
    }

    /**
     * Remove a provisioned user from the deleted directory to do a permanent deletion of the user.
     *
     * @param provisioningEntity the user being deleted.
     * @throws IdentityProvisioningException if the user can not be deleted.
     */
    protected void deleteUserPermanently(ProvisioningEntity provisioningEntity) throws IdentityProvisioningException {

        // Get the provisioned id of deleted user. (Unassigned role)
        // User's UPN can not be considered here because if the user himself is deleted, UPN will be null.
        String provisionedUserId = provisioningEntity.getIdentifier().getIdentifier();

        try (CloseableHttpClient httpclient = HttpClientBuilder.create().useSystemProperties().build()) {

            String deleteUserEndpoint = Office365ConnectorConstants.OFFICE365_DELETE_ENDPOINT + '/' + provisionedUserId;
            HttpDelete delete = new HttpDelete(deleteUserEndpoint);
            setAuthorizationHeader(delete);

            try (CloseableHttpResponse response = httpclient.execute(delete)) {

                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NO_CONTENT) {
                    if (log.isDebugEnabled()) {
                        log.debug("Permanently removed the deleted user with id " + provisionedUserId +
                                " in the Azure Active Directory");
                    }
                } else {
                    JSONObject jsonResponse = new JSONObject(new JSONTokener(new InputStreamReader(
                            response.getEntity().getContent())));
                    String errorMessage = jsonResponse.getJSONObject("error").getString("message");

                    log.error("Received response status code: " + response.getStatusLine().getStatusCode() + " "
                            + response.getStatusLine().getReasonPhrase() + " with the message '" + errorMessage +
                            "' while permanently removing the user with id " + provisionedUserId +
                            " in the Azure Active Directory.");

                    if (log.isDebugEnabled()) {
                        log.debug("The response received from server : " + jsonResponse.toString());
                    }

                }
            } catch (IOException | JSONException e) {
                throw new IdentityProvisioningException("Error while executing the delete operation in user " +
                        "provisioning", e);
            }

        } catch (IOException e) {
            log.error("Error while closing HttpClient.");
        }
    }

    /**
     * Get an access token to call Microsoft Graph APIs
     *
     * @return token    Access token as a string
     * @throws IdentityProvisioningException If the access token can not be obtained from the API
     */
    private String getAccessToken() throws IdentityProvisioningException {

        String clientId = this.configHolder.getValue(Office365ConnectorConstants.OFFICE365_CLIENT_ID);
        String clientSecret = this.configHolder.getValue(Office365ConnectorConstants.OFFICE365_CLIENT_SECRET);
        String tenantName = this.configHolder.getValue(Office365ConnectorConstants.OFFICE365_TENANT);

        // Generate the endpoint using the base url and the user's tenant name.
        String tokenGrantUrl = Office365ConnectorConstants.OFFICE365_BASE_URL + "/" + tenantName +
                Office365ConnectorConstants.OFFICE365_TOKEN_ENDPOINT;

        String accessToken = null;
        try (CloseableHttpClient httpclient = HttpClientBuilder.create().useSystemProperties().build()) {

            // Define the path parameters of the access token grant endpoint.
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

                JSONObject jsonResponse = new JSONObject(
                        new JSONTokener(new InputStreamReader(response.getEntity().getContent())));
                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                    accessToken = jsonResponse.getString("access_token");

                    if (log.isDebugEnabled()) {
                        log.debug("A valid Access token is received for the tenant " + tenantName);
                    }
                } else {
                    log.error("Received response status code: " + response.getStatusLine().getStatusCode() + " "
                            + response.getStatusLine().getReasonPhrase() + " with the response " +
                            jsonResponse.toString());
                }
            } catch (IOException | JSONException e) {
                throw new IdentityProvisioningException("Error while obtaining the access token from the response.", e);
            }
        } catch (IOException e) {
            log.error("Error while closing HttpClient.");
        }
        return accessToken;
    }

    protected String generateRandomPassword() {

        String randomCapitals = RandomStringUtils.random(3, "ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        String randomSimples = RandomStringUtils.random(5, "abcdefghijklmnopqrstuvwxyz");
        String randomNumbers = RandomStringUtils.randomNumeric(4);
        return randomCapitals.concat(randomSimples).concat(randomNumbers);
    }

    private JSONObject buildUserAsJson(ProvisioningEntity provisioningEntity) throws IdentityProvisioningException {

        Map<String, String> requiredAttributes = getSingleValuedClaims(provisioningEntity.getAttributes());
        String displayNameClaim = this.configHolder.getValue(Office365ConnectorConstants.OFFICE365_DISPLAY_NAME);
        String mailNickNameClaim = this.configHolder.getValue(Office365ConnectorConstants.OFFICE365_EMAIL_NICKNAME);
        String upnClaim = this.configHolder.getValue(Office365ConnectorConstants.OFFICE365_UPN);
        String immutableIdClaim = this.configHolder.getValue(Office365ConnectorConstants.OFFICE365_IMMUTABLE_ID);
        String ruleAttributeName = this.configHolder.getValue(Office365ConnectorConstants
                .OFFICE365_MEMBERSHIP_ATTRIBUTE);
        String ruleAttributeClaim = this.configHolder.getValue(Office365ConnectorConstants.OFFICE365_MEMBERSHIP_VALUE);
        if (ruleAttributeClaim.isEmpty() && !ruleAttributeName.isEmpty()) {
            ruleAttributeClaim = Office365ConnectorConstants.WSO2_ROLE_CLAIM;
        }
        String displayName = requiredAttributes.get(displayNameClaim);
        String mailNickName = requiredAttributes.get(mailNickNameClaim);
        String immutableId = requiredAttributes.get(immutableIdClaim);
        String upn = requiredAttributes.get(upnClaim);
        String ruleAttributeValue = requiredAttributes.get(ruleAttributeClaim);

        if (displayName == null || mailNickName == null || immutableId == null || upn == null) {
            throw new IdentityProvisioningException("One or more of the mandatory user attributes: display name, mail" +
                    " " +
                    "nickname, immutable id, user principal name do not have a value.");
        } else {
            // Create a json object corresponding to the attributes of the user in the request.
            JSONObject passwordProfile = new JSONObject();
            passwordProfile.put(Office365ConnectorConstants.FORCE_CHANGE_PASSWORD, false);
            passwordProfile.put(Office365ConnectorConstants.PASSWORD, generateRandomPassword());

            JSONObject user = new JSONObject();
            user.put(Office365ConnectorConstants.ACCOUNT_ENABLED, true);
            user.put(Office365ConnectorConstants.OFFICE365_DISPLAY_NAME, displayName);
            user.put(Office365ConnectorConstants.OFFICE365_EMAIL_NICKNAME, mailNickName);
            user.put(Office365ConnectorConstants.OFFICE365_UPN, getDomainSpecificUpn(upn));
            user.put(Office365ConnectorConstants.OFFICE365_IMMUTABLE_ID, immutableId);
            user.put(Office365ConnectorConstants.PASSWORD_PROFILE, passwordProfile);
            if (!ruleAttributeName.isEmpty()) {
                user.put(ruleAttributeName, ruleAttributeValue);
            }

            if (log.isDebugEnabled()) {
                log.debug("A user object is created. " + user.toString());
            }
            return user;
        }
    }

    private void setAuthorizationHeader(HttpRequestBase httpMethod) throws IdentityProvisioningException {

        String accessToken = getAccessToken();

        if (!accessToken.isEmpty()) {
            httpMethod.addHeader(Office365ConnectorConstants.AUTHORIZATION_HEADER_NAME,
                    Office365ConnectorConstants.AUTHORIZATION_HEADER_BEARER + " " + accessToken);

            if (log.isDebugEnabled() && IdentityUtil.isTokenLoggable(IdentityConstants.IdentityTokens.ACCESS_TOKEN)) {
                log.debug("Received Bearer Token (hashed) : " + new String(Base64.encodeBase64(accessToken.getBytes()
                )));
            }
        } else {
            throw new IdentityProvisioningException("Authentication failed");
        }
    }

    private String getDomainSpecificUpn(String upn) {

        Boolean enableDomain = Boolean.parseBoolean(this.configHolder.getValue(Office365ConnectorConstants
                .OFFICE365_ENABLE_DOMAIN));
        String domainName = this.configHolder.getValue(Office365ConnectorConstants.OFFICE365_DOMAIN);

        // Append the domain name at the end of the claim which given as the user principal name.
        if (enableDomain && !upn.endsWith("@" + domainName)) {
            upn = upn + "@" + domainName;
        }
        return upn;
    }

}
