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

import static org.openhab.binding.veluxactive.internal.VeluxActiveBindingConstants.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.veluxactive.internal.config.VeluxActiveGatewayConfiguration;
import org.openhab.binding.veluxactive.internal.dto.ModuleDTO;
import org.openhab.core.i18n.TimeZoneProvider;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.type.ChannelTypeRegistry;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link VeluxActiveGatewayBridgeHandler} class defines the handler
 * for the gateway bridge thing
 *
 * @author Jared Lyon - Initial contribution
 */
@NonNullByDefault
public class VeluxActiveGatewayBridgeHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(VeluxActiveGatewayBridgeHandler.class);

    private TimeZoneProvider timeZoneProvider;
    private ChannelTypeRegistry channelTypeRegistry;

    private final Map<String, VeluxActiveBlindThingHandler> blindHandlers = new ConcurrentHashMap<>();

    private @NonNullByDefault({}) String gatewayId;

    public VeluxActiveGatewayBridgeHandler(Bridge bridge, TimeZoneProvider timeZoneProvider,
            ChannelTypeRegistry channelTypeRegistry) {
        super(bridge);
        this.timeZoneProvider = timeZoneProvider;
        this.channelTypeRegistry = channelTypeRegistry;
    }

    @Override
    public void initialize() {
        gatewayId = getConfigAs(VeluxActiveGatewayConfiguration.class).gatewayId;
        logger.debug("Velux Active Gateway Bridge: initializing gateway <{}>", gatewayId);
        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void dispose() {
        logger.debug("Velux Active Gateway Bridge: disposing gateway <{}>", gatewayId);
    }

    @Override
    public void childHandlerInitialized(ThingHandler blindHandler, Thing blindThing) {
        /* add blind thing to map */
        String blindId = (String) blindThing.getConfiguration().get("blindId");
        blindHandlers.put(blindId, (VeluxActiveBlindThingHandler) blindHandler);
        // blindIds.add(blindId);
        logger.debug("Velux Active Gateway Bridge: Adding blind handler for {} with id {}", blindThing.getUID(),
                blindId);
    }

    @Override
    public void childHandlerDisposed(ThingHandler blindHandler, Thing blindThing) {
        /* remove blind thing from map */
        String blindId = (String) blindThing.getConfiguration().get("blindId");
        blindHandlers.remove(blindId);
        // blindIds.remove(blindId);
        logger.debug("Velux Active Gateway Bridge: Removing blind handler for {} with id {}", blindThing.getUID(),
                blindId);
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Nullable
    public VeluxActiveAccountBridgeHandler getBridgeHandler() {
        VeluxActiveAccountBridgeHandler handler = null;
        Bridge bridge = getBridge();
        if (bridge != null) {
            handler = (VeluxActiveAccountBridgeHandler) bridge.getHandler();
        }
        return handler;
    }

    public String getGatewayId() {
        return gatewayId;
    }

    /* TODO: function to update channel */
    public void updateChannels(ModuleDTO module) {
        if (module.is_raining) {
            updateState("is_raining", OnOffType.ON);
        } else {
            updateState("is_raining", OnOffType.OFF);
        }
    }

    public void updateBlindChannels(ModuleDTO module) {
        VeluxActiveBlindThingHandler blind = blindHandlers.get(module.id);
        if (blind != null) {
            blind.updateChannels(module);
        }
    }
}
