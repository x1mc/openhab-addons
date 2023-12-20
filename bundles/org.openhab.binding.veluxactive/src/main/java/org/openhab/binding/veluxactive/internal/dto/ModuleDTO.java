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
package org.openhab.binding.veluxactive.internal.dto;

/**
 * The {@link ModuleDTO} class represents a combination of the module data
 * received from the velux API calls gethomesdata and gethomestatus
 *
 * @author Jared Lyon - Initial contribution
 */
public class ModuleDTO {

    public String id;

    public String name;

    public String type;

    public String manufacturer;

    public Boolean reachable;

    public String bridge;

    public String firmware_revision;

    public String firmware_revision_netatmo;

    public String firmware_revision_thirdparty;

    public String hardware_version;

    public Boolean is_raining;

    public String battery_state;

    public Integer current_position;

    public Integer target_position;
}
