package wiremock.demo.paymentservice;

public record ChargeRequest(String customerId, long amount, String currency) {
}
