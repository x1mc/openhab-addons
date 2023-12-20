/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.veluxactive.internal.api;

import static org.openhab.binding.veluxactive.internal.VeluxActiveBindingConstants.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.netatmo.internal.deserialization.AccessTokenResponseDeserializer;
import org.openhab.binding.veluxactive.internal.dto.HomeDTO;
import org.openhab.binding.veluxactive.internal.dto.ModuleDTO;
import org.openhab.binding.veluxactive.internal.dto.ResponseDTO;
import org.openhab.binding.veluxactive.internal.handler.VeluxActiveAccountBridgeHandler;
import org.openhab.core.auth.client.oauth2.AccessTokenRefreshListener;
import org.openhab.core.auth.client.oauth2.AccessTokenResponse;
import org.openhab.core.auth.client.oauth2.OAuthClientService;
import org.openhab.core.auth.client.oauth2.OAuthException;
import org.openhab.core.auth.client.oauth2.OAuthFactory;
import org.openhab.core.auth.client.oauth2.OAuthResponseException;
import org.openhab.core.io.net.http.HttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * The {@link VeluxActiveApi} class provides access the Velux Active API using OAuth2 authentication.
 * All REST calling and response parsing is done here.
 *
 * @author Jared Lyon - Initial contribution
 */
@NonNullByDefault
public class VeluxActiveApi implements AccessTokenRefreshListener {

    private final String username;
    private final String password;
    private final String clientId;
    private final String clientSecret;
    private final Integer apiTimeout;

    private final Logger logger = LoggerFactory.getLogger(VeluxActiveApi.class);

    private final VeluxActiveAccountBridgeHandler accountBridgeHandler;

    private final OAuthFactory oAuthFactory;
    private @NonNullByDefault({}) OAuthClientService oAuthClientService;

    private static final Gson GSON = new Gson();

    private Boolean authenticated;

    /* contructor */
    public VeluxActiveApi(VeluxActiveAccountBridgeHandler accountBridgeHandler, final String username,
            final String password, final String clientId, final String clientSecret, Integer apiTimeout,
            OAuthFactory oAuthFactory) {
        this.username = username;
        this.password = password;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.apiTimeout = apiTimeout;
        this.accountBridgeHandler = accountBridgeHandler;
        this.oAuthFactory = oAuthFactory;

        /* create OAuth service */
        createOAuthClientService();

        /* authenticate */
        authenticated = false;
        authenticate();
    }

    public void createOAuthClientService() {
        String handle = accountBridgeHandler.getThing().getUID().getAsString();

        /* create oauth2 client service using client ID and secret with account bridge thing UID as the handle */
        logger.debug("Velux Active API: Creating OAuth Client Service with handle {}", handle);
        oAuthClientService = oAuthFactory
                .createOAuthClientService(handle, VELUX_ACTIVE_TOKEN_URL, null, clientId, clientSecret, null, null)
                .withGsonBuilder(new GsonBuilder().registerTypeAdapter(AccessTokenResponse.class,
                        new AccessTokenResponseDeserializer()));

        /* add extra fields required by Velux API */
        oAuthClientService.addExtraAuthField("app_version", "11201");
        oAuthClientService.addExtraAuthField("user_prefix", "velux");

        /* add this class as a refresh listener so we can schedule a refresh job as needed */
        oAuthClientService.addAccessTokenRefreshListener(this);
    }

    public void deleteOAuthClientService() {
        String handle = accountBridgeHandler.getThing().getUID().getAsString();
        logger.debug("Velux Active API: Deleting OAuth Client Service with handle {}", handle);
        oAuthClientService.removeAccessTokenRefreshListener(this);
        oAuthFactory.deleteServiceAndAccessToken(handle);
    }

    public void closeOAuthClientService() {
        String handle = accountBridgeHandler.getThing().getUID().getAsString();
        logger.debug("Velux Active API: Deleting OAuth Client Service with handle {}", handle);
        oAuthClientService.removeAccessTokenRefreshListener(this);
        oAuthFactory.deleteServiceAndAccessToken(handle);
    }

    @Override
    public void onAccessTokenResponse(AccessTokenResponse accessTokenResponse) {
        /* TODO - need to do anything when a new token comes around? */
    }

    /* method for authorizing with the velux active API */
    private void authenticate() {
        try {
            AccessTokenResponse atr = oAuthClientService.getAccessTokenByResourceOwnerPasswordCredentials(username,
                    password, null);
            if (atr == null) {
                logger.debug("Velux Active API: Did not get a token response... credentials wrong?");
            } else {
                logger.debug("Velux Active API: Successfully authenticated!");
                authenticated = true;
            }
        } catch (OAuthResponseException e) {
            logger.error("Velux Active API: Got exception from OAuth service: {} - {}", e.getError(),
                    e.getErrorDescription());
        } catch (Exception e) {
            logger.error("Velux Active API: Got {} trying to authenticate: {}", e.getClass().getSimpleName(),
                    e.getMessage());
        }
    }

    public Boolean isAuthenticated() {
        return authenticated;
    }

    private String getOAuthToken() {
        try {
            String token = oAuthClientService.getAccessTokenResponse().getAccessToken();
            if (token == null) {
                return "";
            }
            return token;
        } catch (IOException e) {
            logger.error("Velux Active API: Got I/O exception trying to get access token: {}", e.getMessage());
        } catch (OAuthException e) {
            logger.error("Velux Active API: Got generic OAuth exception trying to get access token: {}",
                    e.getMessage());
        } catch (OAuthResponseException e) {
            logger.error("Velux Active API: Got OAuth response exception tring to get access token: {}",
                    e.getMessage());
        }
        return "";
    }

    /* method for querying homes data */
    public List<HomeDTO> queryHomesData() {
        String formData = "access_token=";
        formData += URLEncoder.encode(getOAuthToken(), StandardCharsets.UTF_8);
        formData += "&gateway_types=NXG";
        String response = postForm(VELUX_ACTIVE_HOMES_DATA_URL, formData);
        logger.trace("Velux Active API: response string: {}", response);
        ResponseDTO homesDataResponse = GSON.fromJson(response, ResponseDTO.class);
        /* TODO: check for good response */
        return homesDataResponse.body.homes;
    }

    /* method for querying home status */
    public List<ModuleDTO> queryHomeStatus(String homeId) {
        String formData = "access_token=";
        formData += URLEncoder.encode(getOAuthToken(), StandardCharsets.UTF_8);
        formData += "&home_id=";
        formData += homeId;
        String response = postForm(VELUX_ACTIVE_HOME_STATUS_URL, formData);
        logger.trace("Velux Active API: response string: {}", response);
        ResponseDTO homeStatusResponse = GSON.fromJson(response, ResponseDTO.class);
        /* TODO: check for good response */
        return homeStatusResponse.body.home.modules;
    }

    /* methods for setting device state */
    public void setStatePosition(Integer position, String blindId, String gatewayId, String homeId) {
        JsonObject jsonObj = new JsonObject();
        JsonObject home = new JsonObject();
        JsonObject module = new JsonObject();
        JsonArray modules = new JsonArray();

        module.addProperty("bridge", gatewayId.replace('-', ':'));
        module.addProperty("id", blindId);
        module.addProperty("target_position", position);
        modules.add(module);
        home.addProperty("id", homeId);
        home.add("modules", modules);
        jsonObj.add("home", home);

        String json = GSON.toJson(jsonObj);
        postJson(VELUX_ACTIVE_SET_STATE_URL, json);
        // TODO - handle errors
    }

    public void setStateStop(String blindId, String gatewayId, String homeId) {
        JsonObject jsonObj = new JsonObject();
        JsonObject home = new JsonObject();
        JsonObject module = new JsonObject();
        JsonArray modules = new JsonArray();

        module.addProperty("id", gatewayId.replace('-', ':'));
        module.addProperty("stop_movements", "all");
        modules.add(module);
        home.addProperty("id", homeId);
        home.add("modules", modules);
        jsonObj.add("home", home);

        String json = GSON.toJson(jsonObj);
        postJson(VELUX_ACTIVE_SET_STATE_URL, json);
    }

    /* POST form data */
    private String postForm(String url, String data) {
        logger.trace("Velux Active API: POSTing form data {} to url {}", data, url);
        try {
            return HttpUtil.executeUrl("POST", url, new ByteArrayInputStream(data.getBytes()),
                    "application/x-www-form-urlencoded", apiTimeout * 1000);
        } catch (IOException e) {
            logger.error("I/O exception when trying to POST! {}", e.getMessage());
            return "";
        }
    }

    /* POST JSON data */
    private void postJson(String url, String data) {
        Properties headers = new Properties();
        headers.put("Content-Type", "application/json;charset=utf-8");
        headers.put("Authorization", "Bearer " + getOAuthToken());
        logger.trace("Velux Active API: POSTing JSON data {} to url {}", data, url);
        try {
            String response = HttpUtil.executeUrl("POST", url, headers, new ByteArrayInputStream(data.getBytes()),
                    "application/json", apiTimeout * 1000);
            logger.trace("Velux Active API: response string: {}", response);
        } catch (IOException e) {
            logger.error("I/O exception when trying to POST! {}", e.getMessage());
        }
    }
}
