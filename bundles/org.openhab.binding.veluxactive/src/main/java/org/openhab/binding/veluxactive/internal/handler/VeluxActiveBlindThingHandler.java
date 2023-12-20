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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.veluxactive.internal.config.VeluxActiveBlindConfiguration;
import org.openhab.binding.veluxactive.internal.dto.ModuleDTO;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.StopMoveType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.types.UpDownType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link VeluxActiveBlindThingHandler} class defines the handler
 * for the blind thing.
 *
 * @author Jared Lyon - Initial contribution
 */
@NonNullByDefault
public class VeluxActiveBlindThingHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(VeluxActiveBlindThingHandler.class);

    private @NonNullByDefault({}) String blindId;

    private @NonNullByDefault({}) Boolean shutterMoving;
    private @NonNullByDefault({}) Integer shutterTarget;

    public VeluxActiveBlindThingHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        blindId = getConfigAs(VeluxActiveBlindConfiguration.class).blindId;
        logger.debug("Velux Active Blind Thing: initializing blind <{}>", blindId);
        shutterMoving = false;
        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void dispose() {
        logger.debug("Velux Active Blind Thing: disposing blind <{}>", blindId);
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
        VeluxActiveGatewayBridgeHandler gatewayHandler = getBridgeHandler();
        VeluxActiveAccountBridgeHandler accountHandler = gatewayHandler.getBridgeHandler();

        logger.debug("Velux Active Blind Thing: got command '{}' on '{}'", command, channelUID.getId());

        if (command == RefreshType.REFRESH) {
            /* REFRESH - nothing to do really? */
            logger.debug("Velux Active Blind Thing: got refresh command!");
        } else if (command == UpDownType.DOWN) {
            /*
             * DOWN - shutter position channel changes to 0, disable updating it until the poll shows 0 also or we get
             * command stop
             */
            logger.debug("Velux Active Blind Thing: got down command, set target to 0 and moving to true!");
            shutterMoving = true;
            shutterTarget = 0;
            accountHandler.setShutterPosition(0, blindId, gatewayHandler.getGatewayId());
        } else if (command == UpDownType.UP) {
            /*
             * UP - shutter position channel changes to 100, disable updating it until the poll shows 100 also or we get
             * command stop
             */
            logger.debug("Velux Active Blind Thing: got up command, set target to 100 and moving to true!");
            shutterMoving = true;
            shutterTarget = 100;
            accountHandler.setShutterPosition(100, blindId, gatewayHandler.getGatewayId());
        } else if (command instanceof DecimalType) {
            /* Specific decimal - move blind to that position */
            Integer value = ((DecimalType) command).intValue();
            logger.debug("Velux Active Blind Thing: got decimal command, set target to {} and moving to true!", value);
            shutterMoving = true;
            shutterTarget = 100 - value;
            accountHandler.setShutterPosition(100 - value, blindId, gatewayHandler.getGatewayId());
        } else if (command == StopMoveType.STOP) {
            /* STOP - tell shutter to stop moving, let poll update position */
            logger.debug("Velux Active Blind Thing: send stop movements request and set moving to false!");
            accountHandler.stopShutterMovement(blindId, gatewayHandler.getGatewayId());
            shutterMoving = false;
        }
    }

    @Nullable
    private VeluxActiveGatewayBridgeHandler getBridgeHandler() {
        VeluxActiveGatewayBridgeHandler handler = null;
        Bridge bridge = getBridge();
        if (bridge != null) {
            handler = (VeluxActiveGatewayBridgeHandler) bridge.getHandler();
        }
        return handler;
    }

    public String getBlindId() {
        return blindId;
    }

    /* TODO: function to update channels */
    public void updateChannels(ModuleDTO module) {
        updateState("battery_state", new StringType(module.battery_state));
        if (!shutterMoving) {
            updateState("shutter_position", new PercentType((100 - module.current_position)));
        } else {
            /* check if the poll value matches the current state, set moving to false if it does */
            if (module.current_position.equals(shutterTarget)) {
                shutterMoving = false;
            }
        }
        updateState("reachable", module.reachable ? new StringType("true") : new StringType("false"));
    }
}
