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
package org.openhab.binding.veluxactive.internal.handler;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.veluxactive.internal.api.VeluxActiveApi;
import org.openhab.binding.veluxactive.internal.config.VeluxActiveAccountConfiguration;
import org.openhab.binding.veluxactive.internal.discovery.VeluxActiveDiscoveryService;
import org.openhab.binding.veluxactive.internal.dto.HomeDTO;
import org.openhab.binding.veluxactive.internal.dto.ModuleDTO;
import org.openhab.core.auth.client.oauth2.OAuthFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link VeluxActiveAccountBridgeHandler} class defines the handler
 * for the account bridge thing.
 *
 * @author Jared Lyon - Initial contribution
 */
@NonNullByDefault
public class VeluxActiveAccountBridgeHandler extends BaseBridgeHandler {

    private static final int REFRESH_STARTUP_DELAY_SECONDS = 3;
    private static final int REFRESH_INTERVAL_SECONDS = 40;

    private @NonNullByDefault({}) String username;
    private @NonNullByDefault({}) String password;
    private @NonNullByDefault({}) String clientId;
    private @NonNullByDefault({}) String clientSecret;
    private @NonNullByDefault({}) Integer refreshIntervalNormal;
    private @NonNullByDefault({}) Integer refreshIntervalQuick;
    private @NonNullByDefault({}) Integer apiTimeout;
    private @NonNullByDefault({}) Boolean discoveryEnabled;

    private final OAuthFactory oAuthFactory;
    private final HttpClient httpClient;

    private @NonNullByDefault({}) VeluxActiveApi api;
    private @NonNullByDefault({}) String homeId;

    private final Map<String, VeluxActiveGatewayBridgeHandler> gatewayHandlers = new ConcurrentHashMap<>();

    private @Nullable Future<?> pollingJob;

    private final Logger logger = LoggerFactory.getLogger(VeluxActiveAccountBridgeHandler.class);

    public VeluxActiveAccountBridgeHandler(final Bridge bridge, OAuthFactory oAuthFactory, HttpClient httpClient) {
        super(bridge);
        this.oAuthFactory = oAuthFactory;
        this.httpClient = httpClient;
    }

    @Override
    public void initialize() {
        logger.debug("VeluxActive Account Bridge initializing...");

        /* get config of account bridge */
        VeluxActiveAccountConfiguration config = getConfigAs(VeluxActiveAccountConfiguration.class);

        /* required config */
        username = config.username;
        password = config.password;
        clientId = config.clientId;
        clientSecret = config.clientSecret;

        /* TODO: actually do something with these numbers... */
        refreshIntervalNormal = config.refreshIntervalNormal;
        refreshIntervalQuick = config.refreshIntervalQuick;
        apiTimeout = config.apiTimeout;
        discoveryEnabled = config.discoveryEnabled;

        logger.debug("VeluxActive Account Bridge gateway and blind discovery is {}",
                discoveryEnabled ? "enabled" : "disabled");

        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING, "Connecting to Velux Active server");

        /* create API object... */
        api = new VeluxActiveApi(this, username, password, clientId, clientSecret, apiTimeout, oAuthFactory);

        /*
         * schedule polling for the gateway and blind states - only poll if authenticated... set status to
         * offline/config error if not authenticated
         */
        schedulePolling();
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING, "Connecting to Velux Active");
    }

    @Override
    public void dispose() {
        logger.debug("VeluxActive Account Bridge disposing...");
        cancelPolling();
        api.closeOAuthClientService();
    }

    @Override
    public void handleRemoval() {
        oAuthFactory.deleteServiceAndAccessToken(thing.getUID().getAsString()); // why can't this be done when the api
                                                                                // object is destroyed??
        super.handleRemoval();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public void childHandlerInitialized(ThingHandler gatewayHandler, Thing gatewayThing) {
        /* add new gateway to map */
        String gatewayId = (String) gatewayThing.getConfiguration().get("gatewayId");
        gatewayHandlers.put(gatewayId, (VeluxActiveGatewayBridgeHandler) gatewayHandler);
        // gatewayIds.add(gatewayId);
        // scheduleQuickPoll();
        logger.debug("Velux Active Account Bridge: Adding gateway handler for {} with id {}", gatewayThing.getUID(),
                gatewayId);
    }

    @Override
    public void childHandlerDisposed(ThingHandler gatewayHandler, Thing gatewayThing) {
        /* remove gateway from map */
        String gatewayId = (String) gatewayThing.getConfiguration().get("gatewayId");
        gatewayHandlers.remove(gatewayId);
        // gatewayIds.remove(gatewayId);
        logger.debug("Velux Active Account Bridge: Removing gateway handler for {} with id {}", gatewayThing.getUID(),
                gatewayId);
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Collections.singleton(VeluxActiveDiscoveryService.class);
    }

    public boolean isBackgroundDiscoveryEnabled() {
        return discoveryEnabled;
    }

    private void schedulePolling() {
        logger.debug("Velux Active Account Bridge: scheduling polling job");
        cancelPolling();
        pollingJob = scheduler.scheduleWithFixedDelay(this::poll, REFRESH_STARTUP_DELAY_SECONDS,
                REFRESH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void cancelPolling() {
        if (pollingJob != null) {
            logger.debug("Velux Active Account Bridge: cancelling polling job");
            pollingJob.cancel(true);
        }
    }

    private void poll() {
        logger.debug("Velux Active Account Bridge: polling...");

        /* TODO: compare DTO result with previous poll, no need to update channels if nothing has changed */

        try {
            List<ModuleDTO> modules = getModules();
            for (ModuleDTO module : modules) {
                if (module.type.equals("NXG")) {
                    VeluxActiveGatewayBridgeHandler handler = gatewayHandlers.get(module.id.replace(':', '-'));
                    if (handler != null) {
                        logger.debug("Velux Active Account Bridge: updating channels for gateway {}", module.id);
                        handler.updateChannels(module);
                    }
                } else if (module.type.equals("NXO")) {
                    VeluxActiveGatewayBridgeHandler handler = gatewayHandlers.get(module.bridge.replace(':', '-'));
                    if (handler != null) {
                        logger.debug("Velux Active Account Bridge: updating channels for blind {} through gateway {}",
                                module.id, module.bridge.replace(':', '-'));
                        handler.updateBlindChannels(module);
                    }
                }
            }
            updateStatus(ThingStatus.ONLINE);
        } catch (Exception e) {
            logger.error("Velux Active Account Bridge: error polling, marking offline: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    public List<ModuleDTO> getModules() {
        /* assume one home for now... */
        HomeDTO home = api.queryHomesData().get(0);
        logger.debug("VeluxActive Account Bridge: found home with ID: {}", home.id);
        homeId = home.id;
        List<ModuleDTO> modules = api.queryHomeStatus(home.id);
        for (ModuleDTO module : modules) {
            /* for each module, fill in name from homesdata response */
            for (ModuleDTO m : home.modules) {
                if (m.id.equals(module.id)) {
                    module.name = m.name;
                }
            }
            logger.debug("VeluxActive Account Bridge: found {} module named '{}' with ID: {}", module.type, module.name,
                    module.id);
        }
        return modules;
    }

    public void setShutterPosition(Integer position, String blindId, String gatewayId) {
        api.setStatePosition(position, blindId, gatewayId, homeId);
    }

    public void stopShutterMovement(String blindId, String gatewayId) {
        api.setStateStop(blindId, gatewayId, homeId);
    }
}
