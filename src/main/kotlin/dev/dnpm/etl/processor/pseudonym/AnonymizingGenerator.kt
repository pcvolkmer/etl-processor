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

import org.apache.commons.codec.binary.Base32
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.math3.random.RandomDataGenerator


class AnonymizingGenerator : Generator {

    override fun generate(id: String): String {
        return Base32().encodeAsString(DigestUtils.sha256(id))
            .substring(0..41)
            .lowercase()
    }

    override fun generateGenomDeTan(id: String?): String? {
        val randomDataGenerator = RandomDataGenerator()
        return randomDataGenerator.nextSecureHexString(64).lowercase()
    }

}