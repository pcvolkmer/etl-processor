/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2023  Comprehensive Cancer Center Mainfranken, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
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

package dev.dnpm.etl.processor.pseudonym

import dev.dnpm.etl.processor.PatientId
import dev.dnpm.etl.processor.PatientPseudonym
import dev.dnpm.etl.processor.config.PseudonymizeConfigProperties

class PseudonymizeService(
    private val generator: Generator,
    private val configProperties: PseudonymizeConfigProperties
) {

    fun patientPseudonym(patientId: PatientId): PatientPseudonym {
        return when (generator) {
            is GpasPseudonymGenerator -> PatientPseudonym(generator.generate(patientId.value))
            else -> PatientPseudonym("${configProperties.prefix}_${generator.generate(patientId.value)}")
        }
    }

    fun genomDeTan(patientId: PatientId): String {
        return generator.generateGenomDeTan(patientId.value)
    }

    fun prefix(): String {
        return configProperties.prefix
    }

}