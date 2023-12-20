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
package org.openhab.binding.veluxactive.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link VeluxActiveAccountConfiguration} class contains fields mapping
 * to the account thing configuration parameters.
 *
 * @author Jared Lyon - Initial contribution
 */
@NonNullByDefault
public class VeluxActiveAccountConfiguration {
    public @Nullable String username;
    public @Nullable String password;
    public @Nullable String clientId;
    public @Nullable String clientSecret;
    public @Nullable Integer refreshIntervalNormal;
    public @Nullable Integer refreshIntervalQuick;
    public @Nullable Integer apiTimeout;
    public @Nullable Boolean discoveryEnabled;
}
