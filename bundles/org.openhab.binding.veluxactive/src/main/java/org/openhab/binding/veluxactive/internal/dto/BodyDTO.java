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

import java.util.List;

/**
 * The {@link BodyDTO} class contains the fields common
 * to the body element in velux API responses
 *
 * @author Jared Lyon - Initial contribution
 */
public class BodyDTO {

    public HomeDTO home;

    public List<HomeDTO> homes;
}