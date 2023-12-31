package wiremock.demo;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER;
import static com.github.tomakehurst.wiremock.http.Fault.EMPTY_RESPONSE;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@WireMockTest
public class DemoBffIntegrationTest {

    static DemoBff demoBff;
    static WireMock mockPaymentService;

    @BeforeAll
    static void init(WireMockRuntimeInfo wireMockRuntimeInfo) {
        demoBff = new DemoBff(wireMockRuntimeInfo.getHttpBaseUrl());
        demoBff.start();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        RestAssured.baseURI = "http://localhost:" + demoBff.getPort();

        mockPaymentService = wireMockRuntimeInfo.getWireMock();
    }

    @Test
    void successfully_pay_for_product() {
        mockPaymentService.register(post(urlPathEqualTo("/charges"))
                // language=JSON
                .willReturn(okJson("""
                {
                    "status": "OK",
                    "chargeId": "%s"
                }
                """.formatted(UUID.randomUUID()))
            ));

        given()
                // language=JSON
                .body("""
                    {
                      "customerId": "1234567890",
                      "productId": "12eb9101-6cd5-4378-8283-8924a64ddb05",
                      "quantity": 3,
                      "currency": "GBP"
                    }
                    """)
                .contentType("application/json")
                .when()
                .post("/payments")
                .then()
                .statusCode(201)
                .body("status", is("OK"));

        mockPaymentService.verifyThat(
                postRequestedFor(urlPathEqualTo("/charges"))
                        .withRequestBody(matchingJsonPath("$.amount", equalTo("33")))
        );
    }

    @Test
    void api_error() {
        mockPaymentService.register(post(urlPathEqualTo("/charges"))
                .willReturn(serviceUnavailable()));

        given()
                // language=JSON
                .body("""
                    {
                      "customerId": "1234567890",
                      "productId": "12eb9101-6cd5-4378-8283-8924a64ddb05",
                      "quantity": 3,
                      "currency": "GBP"
                    }
                    """)
                .contentType("application/json")
                .when()
                .post("/payments")
                .then()
                .statusCode(500)
                .body("status", is("Payment service error"));
    }

    @Test
    void network_error() {
        mockPaymentService.register(post(urlPathEqualTo("/charges"))
                .willReturn(aResponse().withFault(CONNECTION_RESET_BY_PEER)));

        given()
                // language=JSON
                .body("""
                    {
                      "customerId": "1234567890",
                      "productId": "12eb9101-6cd5-4378-8283-8924a64ddb05",
                      "quantity": 3,
                      "currency": "GBP"
                    }
                    """)
                .contentType("application/json")
                .when()
                .post("/payments")
                .then()
                .statusCode(500)
                .body("status", is("Payment service fault"));
    }

    @Test
    void retries_payment_when_on_error_and_fault() {
        // First a proxy error
        mockPaymentService.register(post(urlPathEqualTo("/charges"))
                .inScenario("fail-twice-then-succeed")
                .whenScenarioStateIs(STARTED)
                .willSetStateTo("first-failure")
                .willReturn(aResponse().withStatus(502))
        );

        // Then a network fault
        mockPaymentService.register(post(urlPathEqualTo("/charges"))
                .inScenario("fail-twice-then-succeed")
                .whenScenarioStateIs("first-failure")
                .willSetStateTo("success")
                .willReturn(aResponse().withFault(EMPTY_RESPONSE))
        );

        // Then succeed
        mockPaymentService.register(post(urlPathEqualTo("/charges"))
                .inScenario("fail-twice-then-succeed")
                .whenScenarioStateIs("success")
                // language=JSON
                .willReturn(okJson("""
                {
                    "status": "OK",
                    "chargeId": "%s"
                }
                """.formatted(UUID.randomUUID()))
                ));

        given()
                // language=JSON
                .body("""
                    {
                      "customerId": "1234567890",
                      "productId": "12eb9101-6cd5-4378-8283-8924a64ddb05",
                      "quantity": 3,
                      "currency": "GBP"
                    }
                    """)
                .contentType("application/json")
                .when()
                .post("/payments")
                .then()
                .statusCode(201)
                .body("status", is("OK"));
    }
}
