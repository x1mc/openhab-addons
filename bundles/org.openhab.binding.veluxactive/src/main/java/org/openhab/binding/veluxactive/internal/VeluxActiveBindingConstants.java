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
package org.openhab.binding.veluxactive.internal;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link VeluxActiveBindingConstants} class defines common constants that are
 * used across the whole binding.
 *
 * @author Jared Lyon - Initial contribution
 */
@NonNullByDefault
public class VeluxActiveBindingConstants {

    public static final String BINDING_ID = "veluxactive";

    /* account bridge */
    public static final String THING_TYPE_ACCOUNT = "account";
    public static final ThingTypeUID UID_ACCOUNT_BRIDGE = new ThingTypeUID(BINDING_ID, THING_TYPE_ACCOUNT);
    public static final Set<ThingTypeUID> SUPPORTED_ACCOUNT_BRIDGE_THING_TYPES_UIDS = Collections
            .unmodifiableSet(Stream.of(UID_ACCOUNT_BRIDGE).collect(Collectors.toSet()));

    /* gateway bridge */
    public static final String THING_TYPE_GATEWAY = "gateway";
    public static final ThingTypeUID UID_GATEWAY_BRIDGE = new ThingTypeUID(BINDING_ID, THING_TYPE_GATEWAY);
    public static final Set<ThingTypeUID> SUPPORTED_GATEWAY_BRIDGE_THING_TYPES_UIDS = Collections
            .unmodifiableSet(Stream.of(UID_GATEWAY_BRIDGE).collect(Collectors.toSet()));

    /* blind thing */
    public static final String THING_TYPE_BLIND = "blind";
    public static final ThingTypeUID UID_BLIND_THING = new ThingTypeUID(BINDING_ID, THING_TYPE_BLIND);
    public static final Set<ThingTypeUID> SUPPORTED_BLIND_THING_TYPES_UIDS = Collections
            .unmodifiableSet(Stream.of(UID_BLIND_THING).collect(Collectors.toSet()));

    /* discoverable thing types */
    public static final Set<ThingTypeUID> SUPPORTED_GATEWAY_AND_BLIND_THING_TYPES_UIDS = Stream
            .concat(SUPPORTED_GATEWAY_BRIDGE_THING_TYPES_UIDS.stream(), SUPPORTED_BLIND_THING_TYPES_UIDS.stream())
            .collect(Collectors.toSet());

    /* all supported thing types */
    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Collections.unmodifiableSet(
            Stream.of(UID_ACCOUNT_BRIDGE, UID_GATEWAY_BRIDGE, UID_BLIND_THING).collect(Collectors.toSet()));

    /* API constants */
    public static final String VELUX_ACTIVE_BASE_URL = "https://app.velux-active.com/";
    public static final String VELUX_ACTIVE_TOKEN_URL = VELUX_ACTIVE_BASE_URL + "oauth2/token";
    public static final String VELUX_ACTIVE_HOMES_DATA_URL = VELUX_ACTIVE_BASE_URL + "api/homesdata";
    public static final String VELUX_ACTIVE_HOME_STATUS_URL = VELUX_ACTIVE_BASE_URL + "api/homestatus";
    public static final String VELUX_ACTIVE_SET_STATE_URL = VELUX_ACTIVE_BASE_URL + "syncapi/v1/setstate";
    public static final String VELUX_ACTIVE_API_APP_VERSION = "11201";
    public static final String VELUX_ACTIVE_API_USER_PREFIX = "velux";
    public static final int DISCOVERY_INTERVAL_SECONDS = 300;
    public static final int DISCOVERY_INITIAL_DELAY_SECONDS = 10;
}
