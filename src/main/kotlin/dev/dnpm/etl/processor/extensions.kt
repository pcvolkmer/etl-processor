/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2025  Comprehensive Cancer Center Mainfranken, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.dnpm.etl.processor

import org.springframework.http.MediaType

/**
 * Custom MediaTypes
 *
 * @since 0.11.0
 */
object CustomMediaType {
    val APPLICATION_VND_DNPM_V2_MTB_JSON = MediaType("application", "vnd.dnpm.v2.mtb+json")
    const val APPLICATION_VND_DNPM_V2_JSON_MTB_VALUE = "application/vnd.dnpm.v2.mtb+json"

    val APPLICATION_VND_DNPM_V2_RD_JSON = MediaType("application", "vnd.dnpm.v2.rd+json")
    const val APPLICATION_VND_DNPM_V2_JSON_RD_VALUE = "application/vnd.dnpm.v2.rd+json"
}
