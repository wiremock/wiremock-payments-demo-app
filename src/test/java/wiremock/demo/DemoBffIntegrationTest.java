package wiremock.demo;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@WireMockTest
public class DemoBffIntegrationTest {

    static DemoBff demoBff;

    @BeforeAll
    static void init(WireMockRuntimeInfo wireMockRuntimeInfo) {
        demoBff = new DemoBff(wireMockRuntimeInfo.getHttpBaseUrl());
        demoBff.start();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        RestAssured.baseURI = "http://localhost:" + demoBff.getPort();
    }

    @Test
    void successfully_pay_for_product(WireMockRuntimeInfo wireMockRuntimeInfo) {
        var mockPaymentService = wireMockRuntimeInfo.getWireMock();

        var chargeId = UUID.randomUUID().toString();
        mockPaymentService.register(post(urlPathEqualTo("/charges"))
            .willReturn(okJson("""
                {
                    "status": "OK",
                    "chargeId": "%s"
                }
                """.formatted(chargeId))
            ));

        given()
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
