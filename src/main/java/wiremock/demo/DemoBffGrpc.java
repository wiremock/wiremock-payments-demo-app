package wiremock.demo;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.json.JavalinJackson;
import org.wiremock.demo.grpc.ChargeRequest;
import org.wiremock.demo.grpc.ChargeResponse;
import org.wiremock.demo.grpc.PaymentServiceGrpc;

public class DemoBffGrpc {

    private final ProductCatalogue productCatalogue = new ProductCatalogue();
    private final PaymentServiceGrpc.PaymentServiceBlockingStub paymentService;

    private Javalin webapp;

    public DemoBffGrpc(String hostname, int port) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(hostname, port).usePlaintext().build();
        paymentService = PaymentServiceGrpc.newBlockingStub(channel);
    }

    void start() {
        webapp = Javalin.create(config -> config.jsonMapper(new JavalinJackson()))
                .post("/payments", this::handleCreatePayment)
                .start(0);
    }

    private void handleCreatePayment(Context ctx) {
        var productPaymentRequest = ctx.bodyAsClass(ProductPaymentRequest.class);
        var price = productCatalogue.getPrice(productPaymentRequest.productId(), productPaymentRequest.currency());
        long total = price * productPaymentRequest.quantity();

        ChargeRequest chargeRequest = org.wiremock.demo.grpc.ChargeRequest.newBuilder()
                .setCustomerId(productPaymentRequest.customerId())
                .setAmount(total)
                .setCurrency(productPaymentRequest.currency())
                .build();

        ChargeResponse chargeResponse = paymentService.createCharge(chargeRequest);

        ctx.status(201);
        ctx.json(new PaymentResult(chargeResponse.getStatus()));
    }

    public int getPort() {
        return webapp.port();
    }
}
