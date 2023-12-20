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
package org.openhab.binding.veluxactive.internal.discovery;

import static org.openhab.binding.veluxactive.internal.VeluxActiveBindingConstants.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.veluxactive.internal.dto.ModuleDTO;
import org.openhab.binding.veluxactive.internal.handler.VeluxActiveAccountBridgeHandler;
import org.openhab.binding.veluxactive.internal.handler.VeluxActiveGatewayBridgeHandler;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link VeluxActiveDiscoveryService} class defines the discovery service
 * used to discover Velux Active things through a configrued account.
 *
 * @author Jared Lyon - Initial contribution
 */
@NonNullByDefault
public class VeluxActiveDiscoveryService extends AbstractDiscoveryService implements ThingHandlerService {

    private final Logger logger = LoggerFactory.getLogger(VeluxActiveDiscoveryService.class);

    private @NonNullByDefault({}) VeluxActiveAccountBridgeHandler bridgeHandler;

    private @Nullable Future<?> discoveryJob;

    public VeluxActiveDiscoveryService() {
        super(SUPPORTED_GATEWAY_AND_BLIND_THING_TYPES_UIDS, 15, true);
    }

    @Override
    public void setThingHandler(@Nullable ThingHandler handler) {
        if (handler instanceof VeluxActiveAccountBridgeHandler) {
            this.bridgeHandler = (VeluxActiveAccountBridgeHandler) handler;
        }
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return bridgeHandler;
    }

    @Override
    public void activate() {
        super.activate(null);
    }

    @Override
    public void deactivate() {
        super.deactivate();
    }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypes() {
        return SUPPORTED_GATEWAY_AND_BLIND_THING_TYPES_UIDS;
    }

    @Override
    protected void startBackgroundDiscovery() {
        logger.debug("VeluxActiveDiscovery: Starting background discovery job");
        Future<?> localDiscoveryJob = discoveryJob;
        if (localDiscoveryJob == null || localDiscoveryJob.isCancelled()) {
            discoveryJob = scheduler.scheduleWithFixedDelay(this::backgroundDiscover, DISCOVERY_INITIAL_DELAY_SECONDS,
                    DISCOVERY_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Override
    protected void stopBackgroundDiscovery() {
        logger.debug("VeluxActiveDiscovery: Stopping background discovery job");
        Future<?> localDiscoveryJob = discoveryJob;
        if (localDiscoveryJob != null) {
            localDiscoveryJob.cancel(true);
            discoveryJob = null;
        }
    }

    @Override
    public void startScan() {
        logger.debug("VeluxActiveDiscovery: Starting discovery scan");
        discover();
    }

    private void backgroundDiscover() {
        if (!bridgeHandler.isBackgroundDiscoveryEnabled()) {
            return;
        }
        discover();
    }

    private void discover() {
        if (bridgeHandler.getThing().getStatus() != ThingStatus.ONLINE) {
            logger.debug("VeluxActiveDiscovery: Skipping discovery because Account Bridge thing is not ONLINE");
            return;
        }
        logger.debug("VeluxActiveDiscovery: Discovering Velux Active devices");
        discoverGateways();
        discoverBlinds();
    }

    private synchronized void discoverGateways() {
        logger.debug("VeluxActiveDiscovery: Discovering gateways");
        List<ModuleDTO> modules = bridgeHandler.getModules();
        for (ModuleDTO module : modules) {
            logger.debug("VeluxActiveDiscovery: looking at module with type {}, name {}, id {}", module.type,
                    module.name, module.id);
            if (module.type.equals("NXG")) {
                String id = module.id.replace(':', '-');
                ThingUID thingUID = new ThingUID(UID_GATEWAY_BRIDGE, bridgeHandler.getThing().getUID(), id);
                DiscoveryResult result = createGatewayDiscoveryResult(thingUID, id, module);
                thingDiscovered(result);
                logger.debug("VeluxActiveDiscovery: Gateway '{}' added with UID '{}'", id, thingUID);
            }
        }
    }

    private DiscoveryResult createGatewayDiscoveryResult(ThingUID gatewayUID, String id, ModuleDTO module) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("gatewayId", id);
        properties.put("firmware_revision_netatmo", module.firmware_revision_netatmo);
        properties.put("firmware_revision_thirdparty", module.firmware_revision_thirdparty);
        properties.put("hardware_version", module.hardware_version);
        return DiscoveryResultBuilder.create(gatewayUID).withProperties(properties)
                .withRepresentationProperty("gatewayId").withBridge(bridgeHandler.getThing().getUID())
                .withLabel(String.format("Velux Active Gateway %s", id)).build();
    }

    private synchronized void discoverBlinds() {
        List<Thing> gatewayThings = bridgeHandler.getThing().getThings();
        if (gatewayThings.isEmpty()) {
            logger.debug("VeluxActiveDiscovery: skipping blind discovery because there are no gateway things");
            return;
        }
        logger.debug("VeluxActiveDiscovery: Discovering blinds");
        List<ModuleDTO> modules = bridgeHandler.getModules();
        for (Thing gateway : gatewayThings) {
            VeluxActiveGatewayBridgeHandler gatewayHandler = (VeluxActiveGatewayBridgeHandler) gateway.getHandler();
            if (gatewayHandler != null) {
                String gatewayId = gatewayHandler.getGatewayId();
                for (ModuleDTO module : modules) {
                    logger.debug(
                            "VeluxActiveDiscovery: (gateway {}) looking at module with type {}, name {}, id {}, bridge {}",
                            gatewayId, module.type, module.name, module.id, module.bridge);
                    if (module.type.equals("NXO") && module.bridge.replace(':', '-').equals(gatewayId)) {
                        ThingUID gatewayUID = gatewayHandler.getThing().getUID();
                        ThingUID thingUID = new ThingUID(UID_BLIND_THING, gatewayUID, module.id);
                        DiscoveryResult result = createBlindDiscoveryResult(thingUID, gatewayUID, module);
                        thingDiscovered(result);
                        logger.debug("VeluxActiveDiscovery: Blind '{}' with name '{}' added with UID '{}'", module.id,
                                module.name, thingUID);
                    }
                }
            }
        }
    }

    private DiscoveryResult createBlindDiscoveryResult(ThingUID blindUID, ThingUID bridgeUID, ModuleDTO module) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("blindId", module.id);
        properties.put("firmware_revision", module.firmware_revision);
        properties.put("manufacturer", module.manufacturer);
        return DiscoveryResultBuilder.create(blindUID).withProperties(properties).withRepresentationProperty("blindId")
                .withBridge(bridgeUID).withLabel(String.format("Velux Active Blind %s", module.name)).build();
    }
}
