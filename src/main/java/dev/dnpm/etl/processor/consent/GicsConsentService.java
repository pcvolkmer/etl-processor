package dev.dnpm.etl.processor.consent;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import dev.dnpm.etl.processor.config.AppFhirConfig;
import dev.dnpm.etl.processor.config.GIcsConfigProperties;
import java.util.Date;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4.model.StringType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.TerminatedRetryException;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;


public class GicsConsentService implements IGetConsent {

    private final Logger log = LoggerFactory.getLogger(GicsConsentService.class);

    public static final String IS_CONSENTED_ENDPOINT = "/$isConsented";
    public static final String IS_POLICY_STATES_FOR_PERSON_ENDPOINT = "/$currentPolicyStatesForPerson";
    private final RetryTemplate retryTemplate;
    private final RestTemplate restTemplate;
    private final FhirContext fhirContext;
    private final HttpHeaders httpHeader;
    private final GIcsConfigProperties gIcsConfigProperties;
    private String url;

    public GicsConsentService(GIcsConfigProperties gIcsConfigProperties,
        RetryTemplate retryTemplate, RestTemplate restTemplate, AppFhirConfig appFhirConfig) {

        this.retryTemplate = retryTemplate;
        this.restTemplate = restTemplate;
        this.fhirContext = appFhirConfig.fhirContext();
        httpHeader = buildHeader(gIcsConfigProperties.getUsername(),
            gIcsConfigProperties.getPassword());
        this.gIcsConfigProperties = gIcsConfigProperties;
        log.info("GicsConsentService initialized...");
    }

    public String getGicsUri(String endpoint) {
        if (url == null) {
            final String gIcsBaseUri = gIcsConfigProperties.getUri();
            if (StringUtils.isBlank(gIcsBaseUri)) {
                throw new IllegalArgumentException(
                    "gICS base URL is empty - should call gICS with false configuration.");
            }
            url = UriComponentsBuilder.fromUriString(gIcsBaseUri).path(endpoint)
                .toUriString();
        }
        return url;
    }

    @NotNull
    private static HttpHeaders buildHeader(String gPasUserName, String gPasPassword) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);

        if (StringUtils.isBlank(gPasUserName) || StringUtils.isBlank(gPasPassword)) {
            return headers;
        }

        headers.setBasicAuth(gPasUserName, gPasPassword);
        return headers;
    }

    protected static Parameters getIsConsentedRequestParam(GIcsConfigProperties configProperties,
        String personIdentifierValue) {
        var result = new Parameters();
        result.addParameter(new ParametersParameterComponent().setName("personIdentifier").setValue(
            new Identifier().setValue(personIdentifierValue)
                .setSystem(configProperties.getPersonIdentifierSystem())));
        result.addParameter(new ParametersParameterComponent().setName("domain")
            .setValue(new StringType().setValue(configProperties.getBroadConsentDomainName())));
        result.addParameter(new ParametersParameterComponent().setName("policy").setValue(
            new Coding().setCode(configProperties.getBroadConsentPolicyCode())
                .setSystem(configProperties.getBroadConsentPolicySystem())));

        /*
         * is mandatory parameter, but we ignore it via additional configuration parameter
         * 'ignoreVersionNumber'.
         */
        result.addParameter(new ParametersParameterComponent().setName("version")
            .setValue(new StringType().setValue("1.1")));

        /* add config parameter with:
         * ignoreVersionNumber -> true ->> Reason is we cannot know which policy version each patient
         * has possibly signed or not, therefore we are happy with any version found.
         * unknownStateIsConsideredAsDecline -> true
         */
        var config = new ParametersParameterComponent().setName("config").addPart(
            new ParametersParameterComponent().setName("ignoreVersionNumber")
                .setValue(new BooleanType().setValue(true))).addPart(
            new ParametersParameterComponent().setName("unknownStateIsConsideredAsDecline")
                .setValue(new BooleanType().setValue(false)));
        result.addParameter(config);

        return result;
    }

    protected String callGicsApi(Parameters parameter, String endpoint) {
        var parameterAsXml = fhirContext.newXmlParser().encodeResourceToString(parameter);

        HttpEntity<String> requestEntity = new HttpEntity<>(parameterAsXml, this.httpHeader);
        ResponseEntity<String> responseEntity;
        try {
            var url = getGicsUri(endpoint);

            responseEntity = retryTemplate.execute(
                ctx -> restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class));
        } catch (RestClientException e) {
            var msg = String.format("Get consents status request failed reason: '%s",
                e.getMessage());
            log.error(msg);
            return null;

        } catch (TerminatedRetryException terminatedRetryException) {
            var msg = String.format(
                "Get consents status process has been terminated. termination reason: '%s",
                terminatedRetryException.getMessage());
            log.error(msg);
            return null;

        }
        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            return responseEntity.getBody();
        } else {
            var msg = String.format(
                "Trusted party system reached but request failed! code: '%s' response: '%s'",
                responseEntity.getStatusCode(), responseEntity.getBody());
            log.error(msg);
            return null;
        }
    }

    @Override
    public TtpConsentStatus getTtpBroadConsentStatus(String personIdentifierValue) {
        var parameter = GicsConsentService.getIsConsentedRequestParam(gIcsConfigProperties,
            personIdentifierValue);

        var consentStatusResponse = callGicsApi(parameter,
            GicsConsentService.IS_CONSENTED_ENDPOINT);
        return evaluateConsentResponse(consentStatusResponse);
    }

    protected Bundle currentConsentForPersonAndTemplate(String personIdentifierValue,
        ConsentDomain targetConsentDomain, Date requestDate) {

        String consentDomain = getConsentDomain(targetConsentDomain);

        var requestParameter = GicsConsentService.buildRequestParameterCurrentPolicyStatesForPerson(
            gIcsConfigProperties, personIdentifierValue, requestDate, consentDomain);

        var consentDataSerialized = callGicsApi(requestParameter,
            GicsConsentService.IS_POLICY_STATES_FOR_PERSON_ENDPOINT);

        if (consentDataSerialized == null) {
            // error occurred - should not process further!
            throw new IllegalStateException(
                "consent data request failed - stopping processing! - try again or fix other problems first.");
        }
        var iBaseResource = fhirContext.newJsonParser()
            .parseResource(consentDataSerialized);
        if (iBaseResource instanceof OperationOutcome) {
            // log error  - very likely a configuration error
            String errorMessage =
                "Consent request failed! Check outcome:\n " + consentDataSerialized;
            log.error(errorMessage);
            throw new IllegalStateException(errorMessage);
        } else if (iBaseResource instanceof Bundle) {
            return (Bundle) iBaseResource;
        } else {
            String errorMessage = "Consent request failed! Unexpected response received! ->  "
                + consentDataSerialized;
            log.error(errorMessage);
            throw new IllegalStateException(errorMessage);
        }
    }

    @NotNull
    private String getConsentDomain(ConsentDomain targetConsentDomain) {
        String consentDomain;
        switch (targetConsentDomain) {
            case BroadConsent -> consentDomain = gIcsConfigProperties.getBroadConsentDomainName();
            case Modelvorhaben64e ->
                consentDomain = gIcsConfigProperties.getGenomDeConsentDomainName();
            default -> throw new IllegalArgumentException(
                "target ConsentDomain is missing but must be provided!");
        }
        return consentDomain;
    }

    protected static Parameters buildRequestParameterCurrentPolicyStatesForPerson(
        GIcsConfigProperties gIcsConfigProperties, String personIdentifierValue, Date requestDate,
        String targetDomain) {
        var requestParameter = new Parameters();
        requestParameter.addParameter(new ParametersParameterComponent().setName("personIdentifier")
            .setValue(new Identifier().setValue(personIdentifierValue)
                .setSystem(gIcsConfigProperties.getPersonIdentifierSystem())));

        requestParameter.addParameter(new ParametersParameterComponent().setName("domain")
            .setValue(new StringType().setValue(targetDomain)));

        Parameters nestedConfigParameters = new Parameters();
        nestedConfigParameters.addParameter(
                new ParametersParameterComponent().setName("idMatchingType").setValue(
                    new Coding().setSystem(
                            "https://ths-greifswald.de/fhir/CodeSystem/gics/IdMatchingType")
                        .setCode("AT_LEAST_ONE"))).addParameter("ignoreVersionNumber", false)
            .addParameter("unknownStateIsConsideredAsDecline", false)
            .addParameter("requestDate", new DateType().setValue(requestDate));

        requestParameter.addParameter(new ParametersParameterComponent().setName("config").addPart()
            .setResource(nestedConfigParameters));

        return requestParameter;
    }

    private TtpConsentStatus evaluateConsentResponse(String consentStatusResponse) {
        if (consentStatusResponse == null) {
            return TtpConsentStatus.FAILED_TO_ASK;
        }
        try {
            var response = fhirContext.newJsonParser().parseResource(consentStatusResponse);

            if (response instanceof Parameters responseParameters) {

                var responseValue = responseParameters.getParameter("consented").getValue();
                var isConsented = responseValue.castToBoolean(responseValue);
                if (!isConsented.hasValue()) {
                    return TtpConsentStatus.FAILED_TO_ASK;
                }
                if (isConsented.booleanValue()) {
                    return TtpConsentStatus.BROAD_CONSENT_GIVEN;
                } else {
                    return TtpConsentStatus.BROAD_CONSENT_MISSING_OR_REJECTED;
                }
            } else if (response instanceof OperationOutcome outcome) {
                log.error("failed to get consent status from ttp. probably configuration error. "
                    + "outcome: '{}'", fhirContext.newJsonParser().encodeToString(outcome));

            }
        } catch (DataFormatException dfe) {
            log.error("failed to parse response to FHIR R4 resource.", dfe);
        }
        return TtpConsentStatus.FAILED_TO_ASK;
    }

    @Override
    public Bundle getConsent(String patientId, Date requestDate, ConsentDomain consentDomain) {
        switch (consentDomain) {
            case BroadConsent -> {
                return currentConsentForPersonAndTemplate(patientId, ConsentDomain.BroadConsent,
                    requestDate);
            }
            case Modelvorhaben64e -> {
                return currentConsentForPersonAndTemplate(patientId,
                    ConsentDomain.Modelvorhaben64e, requestDate);
            }
        }

        return new Bundle();
    }
}
