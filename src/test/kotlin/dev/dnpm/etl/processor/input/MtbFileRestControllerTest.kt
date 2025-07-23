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

package dev.dnpm.etl.processor.input

import com.fasterxml.jackson.databind.ObjectMapper
import de.ukw.ccc.bwhc.dto.*
import de.ukw.ccc.bwhc.dto.Consent.Status
import de.ukw.ccc.bwhc.dto.Patient
import dev.dnpm.etl.processor.CustomMediaType
import dev.dnpm.etl.processor.consent.ConsentByMtbFile
import dev.dnpm.etl.processor.consent.GicsConsentService
import dev.dnpm.etl.processor.consent.TtpConsentStatus
import dev.dnpm.etl.processor.services.RequestProcessor
import dev.pcvolkmer.mv64e.mtb.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyValueClass
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.*

@ExtendWith(MockitoExtension::class)
class MtbFileRestControllerTest {

    private val objectMapper = ObjectMapper()

    @Nested
    inner class BwhcRequests {

        private lateinit var mockMvc: MockMvc

        private lateinit var requestProcessor: RequestProcessor


        @BeforeEach
        fun setup(
            @Mock requestProcessor: RequestProcessor
        ) {
            this.requestProcessor = requestProcessor
            val controller = MtbFileRestController(
                requestProcessor,
                ConsentByMtbFile()
            )
            this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
        }

        @Test
        fun shouldProcessPostRequest() {
            mockMvc.post("/mtbfile") {
                content = objectMapper.writeValueAsString(bwhcMtbFileContent(Status.ACTIVE))
                contentType = MediaType.APPLICATION_JSON
            }.andExpect {
                status {
                    isAccepted()
                }
            }

            verify(requestProcessor, times(1)).processMtbFile(any<MtbFile>())
        }

        @Test
        fun shouldProcessPostRequestWithRejectedConsent() {
            mockMvc.post("/mtbfile") {
                content = objectMapper.writeValueAsString(bwhcMtbFileContent(Status.REJECTED))
                contentType = MediaType.APPLICATION_JSON
            }.andExpect {
                status {
                    isAccepted()
                }
            }

            verify(requestProcessor, times(1)).processDeletion(
                anyValueClass(),
                org.mockito.kotlin.eq(TtpConsentStatus.BROAD_CONSENT_MISSING_OR_REJECTED)
            )
        }

        @Test
        fun shouldProcessDeleteRequest() {
            mockMvc.delete("/mtbfile/TEST_12345678").andExpect {
                status {
                    isAccepted()
                }
            }

            verify(requestProcessor, times(1)).processDeletion(
                anyValueClass(),
                org.mockito.kotlin.eq(TtpConsentStatus.UNKNOWN_CHECK_FILE)
            )
        }
    }

    @Nested
    inner class BwhcRequestsCheckConsentViaTtp {

        private lateinit var mockMvc: MockMvc

        private lateinit var requestProcessor: RequestProcessor

        private lateinit var gicsConsentService: GicsConsentService

        @BeforeEach
        fun setup(
            @Mock requestProcessor: RequestProcessor,
            @Mock gicsConsentService: GicsConsentService
        ) {
            this.requestProcessor = requestProcessor
            val controller = MtbFileRestController(requestProcessor, gicsConsentService)
            this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
            this.gicsConsentService = gicsConsentService
        }

        @ParameterizedTest
        @ValueSource(strings = ["ACTIVE", "REJECTED"])
        fun shouldProcessPostRequest(status: String) {

            whenever(gicsConsentService.getTtpBroadConsentStatus(any())).thenReturn(TtpConsentStatus.BROAD_CONSENT_GIVEN)

            mockMvc.post("/mtbfile") {
                content = objectMapper.writeValueAsString(bwhcMtbFileContent(Status.valueOf(status)))
                contentType = MediaType.APPLICATION_JSON
            }.andExpect {
                status {
                    isAccepted()
                }
            }

            verify(requestProcessor, times(1)).processMtbFile(any<MtbFile>())
        }


        @ParameterizedTest
        @ValueSource(strings = ["ACTIVE", "REJECTED"])
        fun shouldProcessPostRequestWithRejectedConsent(status: String) {

            whenever(gicsConsentService.getTtpBroadConsentStatus(any())).thenReturn(TtpConsentStatus.BROAD_CONSENT_MISSING_OR_REJECTED)

            mockMvc.post("/mtbfile") {
                content = objectMapper.writeValueAsString(bwhcMtbFileContent(Status.valueOf(status)))
                contentType = MediaType.APPLICATION_JSON
            }.andExpect {
                status {
                    isAccepted()
                }
            }

            // consent status from ttp should override file consent value
            verify(requestProcessor, times(1)).processDeletion(
                anyValueClass(),
                org.mockito.kotlin.eq(TtpConsentStatus.BROAD_CONSENT_MISSING_OR_REJECTED)
            )
        }

        @Test
        fun shouldProcessDeleteRequest() {

            mockMvc.delete("/mtbfile/TEST_12345678").andExpect {
                status {
                    isAccepted()
                }
            }

            verify(requestProcessor, times(1)).processDeletion(
                anyValueClass(),
                org.mockito.kotlin.eq(TtpConsentStatus.UNKNOWN_CHECK_FILE)
            )
            verify(gicsConsentService, times(0)).getTtpBroadConsentStatus(any())

        }
    }


    @Nested
    inner class BwhcRequestsWithAlias {

        private lateinit var mockMvc: MockMvc

        private lateinit var requestProcessor: RequestProcessor

        @BeforeEach
        fun setup(
            @Mock requestProcessor: RequestProcessor
        ) {
            this.requestProcessor = requestProcessor
            val controller = MtbFileRestController(
                requestProcessor,
                ConsentByMtbFile()
            )
            this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
        }

        @Test
        fun shouldProcessPostRequest() {
            mockMvc.post("/mtb") {
                content = objectMapper.writeValueAsString(bwhcMtbFileContent(Status.ACTIVE))
                contentType = MediaType.APPLICATION_JSON
            }.andExpect {
                status {
                    isAccepted()
                }
            }

            verify(requestProcessor, times(1)).processMtbFile(any<MtbFile>())
        }

        @Test
        fun shouldProcessPostRequestWithRejectedConsent() {
            mockMvc.post("/mtb") {
                content = objectMapper.writeValueAsString(bwhcMtbFileContent(Status.REJECTED))
                contentType = MediaType.APPLICATION_JSON
            }.andExpect {
                status {
                    isAccepted()
                }
            }

            verify(requestProcessor, times(1)).processDeletion(
                anyValueClass(), org.mockito.kotlin.eq(
                    TtpConsentStatus.BROAD_CONSENT_MISSING_OR_REJECTED
                )
            )
        }

        @Test
        fun shouldProcessDeleteRequest() {
            mockMvc.delete("/mtb/TEST_12345678").andExpect {
                status {
                    isAccepted()
                }
            }

            verify(requestProcessor, times(1)).processDeletion(
                anyValueClass(), org.mockito.kotlin.eq(
                    TtpConsentStatus.UNKNOWN_CHECK_FILE
                )
            )
        }
    }

    @Nested
    inner class Dnpm21RequestsCheckConsentViaTtp {

        private lateinit var mockMvc: MockMvc

        private lateinit var requestProcessor: RequestProcessor

        private lateinit var gicsConsentService: GicsConsentService

        @BeforeEach
        fun setup(
            @Mock requestProcessor: RequestProcessor,
            @Mock gicsConsentService: GicsConsentService
        ) {
            this.requestProcessor = requestProcessor
            val controller = MtbFileRestController(requestProcessor, gicsConsentService)
            this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
            this.gicsConsentService = gicsConsentService
        }

        @ParameterizedTest
        @ValueSource(strings = ["ACTIVE", "REJECTED"])
        fun shouldProcessPostRequest(status: String) {

            whenever(gicsConsentService.getTtpBroadConsentStatus(any())).thenReturn(TtpConsentStatus.BROAD_CONSENT_GIVEN)

            mockMvc.post("/mtbfile") {
                content = objectMapper.writeValueAsString(dnpm21MtbFileContent(Status.valueOf(status)))
                contentType = CustomMediaType.APPLICATION_VND_DNPM_V2_MTB_JSON
            }.andExpect {
                status {
                    isAccepted()
                }
            }

            verify(requestProcessor, times(1)).processMtbFile(any<Mtb>())
        }


        @ParameterizedTest
        @ValueSource(strings = ["ACTIVE", "REJECTED"])
        fun shouldProcessPostRequestWithRejectedConsent(status: String) {

            whenever(gicsConsentService.getTtpBroadConsentStatus(any())).thenReturn(TtpConsentStatus.BROAD_CONSENT_MISSING_OR_REJECTED)

            mockMvc.post("/mtbfile") {
                content = objectMapper.writeValueAsString(dnpm21MtbFileContent(Status.valueOf(status)))
                contentType = CustomMediaType.APPLICATION_VND_DNPM_V2_MTB_JSON
            }.andExpect {
                status {
                    isAccepted()
                }
            }

            // consent status from ttp should override file consent value
            verify(requestProcessor, times(1)).processDeletion(
                anyValueClass(),
                org.mockito.kotlin.eq(TtpConsentStatus.BROAD_CONSENT_MISSING_OR_REJECTED)
            )
        }

        @Test
        fun shouldProcessDeleteRequest() {

            mockMvc.delete("/mtbfile/TEST_12345678").andExpect {
                status {
                    isAccepted()
                }
            }

            verify(requestProcessor, times(1)).processDeletion(
                anyValueClass(),
                org.mockito.kotlin.eq(TtpConsentStatus.UNKNOWN_CHECK_FILE)
            )
            verify(gicsConsentService, times(0)).getTtpBroadConsentStatus(any())

        }
    }

    companion object {
        fun bwhcMtbFileContent(consentStatus: Status) = MtbFile.builder().withPatient(
            Patient.builder().withId("TEST_12345678").withBirthDate("2000-08-08").withGender(Patient.Gender.MALE)
                .build()
        ).withConsent(
            Consent.builder().withId("1").withStatus(consentStatus).withPatient("TEST_12345678").build()
        ).withEpisode(
            Episode.builder().withId("1").withPatient("TEST_12345678").withPeriod(PeriodStart("2023-08-08")).build()
        ).build()

        fun dnpm21MtbFileContent(consentStatus: Status) = Mtb.builder().patient(
            dev.pcvolkmer.mv64e.mtb.Patient.builder().id("TEST_12345678")
                .birthDate(Date.from(Instant.parse("2000-08-08T12:34:56Z"))).gender(
                    GenderCoding.builder().code(GenderCodingCode.MALE).build()
                ).build()
        ).metadata(
            if (consentStatus == Status.ACTIVE) {
                MvhMetadata.builder().modelProjectConsent(
                    ModelProjectConsent.builder().provisions(
                        listOf(
                            Provision.builder().date(Date()).type(ConsentProvision.PERMIT)
                                .purpose(ModelProjectConsentPurpose.SEQUENCING).build()
                        )
                    ).build()
                ).build()
            } else {
                MvhMetadata.builder().modelProjectConsent(ModelProjectConsent.builder().build()).build()
            }
        ).episodesOfCare(
            listOf(MtbEpisodeOfCare.builder().id("1").patient(Reference.builder().id("TEST_12345678").build()).build())
        ).build()
    }
}
