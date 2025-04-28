/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2024  Comprehensive Cancer Center Mainfranken, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
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

package dev.dnpm.etl.processor.input

import de.ukw.ccc.bwhc.dto.Consent
import de.ukw.ccc.bwhc.dto.MtbFile
import dev.dnpm.etl.processor.PatientId
import dev.dnpm.etl.processor.consent.ICheckConsent
import dev.dnpm.etl.processor.consent.ConsentStatus
import dev.dnpm.etl.processor.services.RequestProcessor
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping(path = ["mtbfile"])
class MtbFileRestController(
    private val requestProcessor: RequestProcessor, private val constService: ICheckConsent
) {

    private val logger = LoggerFactory.getLogger(MtbFileRestController::class.java)

    @GetMapping
    fun info(): ResponseEntity<String> {
        return ResponseEntity.ok("Test")
    }

    @PostMapping
    fun mtbFile(@RequestBody mtbFile: MtbFile): ResponseEntity<Void> {

        var consentStatus = constService.isConsented(mtbFile.patient.id)
        if (mtbFile.consent.status == Consent.Status.ACTIVE && (consentStatus.equals(ConsentStatus.CONSENTED) || consentStatus.equals(
                ConsentStatus.IGNORED
            ))
        ) {
            logger.debug("Accepted MTB File for processing")
            requestProcessor.processMtbFile(mtbFile)
        } else {
            var msg = "Accepted MTB File and process deletion"
            if (!consentStatus.equals(ConsentStatus.CONSENTED) || consentStatus.equals(ConsentStatus.IGNORED)) {
                msg = "Accepted MTB File. But consent is missing, therefore process deletion."
            }
            if (mtbFile.consent.status == Consent.Status.REJECTED) consentStatus =
                ConsentStatus.CONSENT_REJECTED
            logger.debug(msg)
            val patientId = PatientId(mtbFile.patient.id)
            requestProcessor.processDeletion(patientId, consentStatus)
        }
        return ResponseEntity.accepted().build()
    }

    @DeleteMapping(path = ["{patientId}"])
    fun deleteData(@PathVariable patientId: String): ResponseEntity<Void> {
        logger.debug("Accepted patient ID to process deletion")
        requestProcessor.processDeletion(PatientId(patientId), ConsentStatus.IGNORED)
        return ResponseEntity.accepted().build()
    }

}