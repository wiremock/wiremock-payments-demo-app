package wiremock.demo;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.wiremock.demo.grpc.ChargeRequest;
import org.wiremock.demo.grpc.ChargeResponse;
import org.wiremock.demo.grpc.PaymentServiceGrpc;
import org.wiremock.grpc.GrpcExtensionFactory;
import org.wiremock.grpc.dsl.WireMockGrpcService;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.wiremock.grpc.dsl.WireMockGrpc.*;

public class DemoBffGrpcIntegrationTest {

    static DemoBffGrpc demoBff;

    @RegisterExtension
    public static WireMockExtension wm =
        WireMockExtension.newInstance()
            .options(
                wireMockConfig()
                    .dynamicPort()
                    .withRootDirectory("src/test/resources/wiremock")
                    .extensions(new GrpcExtensionFactory())
            )
            .build();

    static WireMockGrpcService mockPaymentService;

    @BeforeAll
    static void init() {
        demoBff = new DemoBffGrpc("localhost", wm.getPort());
        demoBff.start();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        RestAssured.baseURI = "http://localhost:" + demoBff.getPort();

        mockPaymentService = new WireMockGrpcService(new WireMock(wm.getPort()), PaymentServiceGrpc.SERVICE_NAME);
    }

    @Test
    void successfully_pay_for_product_via_json() {
        mockPaymentService.stubFor(method("createCharge")
                .withRequestMessage(equalToJson(
                        // language=JSON
                        """
                        {
                            "customerId": "${json-unit.ignore}",
                            "currency": "${json-unit.ignore}",
                            "amount": "${json-unit.ignore}"
                        }
                        """,
                        true, true))
                .willReturn(json(
                        // language=JSON
                        """
                        {
                            "status": "OK",
                            "chargeId": "%s"
                        }
                        """.formatted(UUID.randomUUID())))
        );

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

        wm.verify(postRequestedFor(urlPathEqualTo("/" + PaymentServiceGrpc.SERVICE_NAME + "/createCharge"))
                .withRequestBody(matchingJsonPath("$.amount", equalTo("33"))));
    }
    @Test
    void successfully_pay_for_product_via_message_objects() {
        mockPaymentService.stubFor(method("createCharge")
                .withRequestMessage(equalToMessage(ChargeRequest.newBuilder()
                        .setCustomerId("1234567890")
                        .setAmount(33)
                        .setCurrency("GBP")
                        .build()))
                .willReturn(message(ChargeResponse.newBuilder()
                        .setStatus("OK")
                        .build()))
        );

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

        wm.verify(postRequestedFor(urlPathEqualTo("/" + PaymentServiceGrpc.SERVICE_NAME + "/createCharge"))
                .withRequestBody(matchingJsonPath("$.amount", equalTo("33"))));
    }

    @Test
    void grpc_network_fault() {
        wm.stubFor(post(urlPathEqualTo("/" + PaymentServiceGrpc.SERVICE_NAME + "/createCharge"))
                        .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

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
                .statusCode(500);
    }


}
