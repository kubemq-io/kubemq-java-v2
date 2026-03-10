
package com.totalplay.receiver.receiverkubekube.event;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.totalplay.receiver.receiverkubekube.config.BrmApiConfig;
import com.totalplay.receiver.receiverkubekube.config.SgaApiConfig;
import com.totalplay.receiver.receiverkubekube.model.Request;
import com.totalplay.receiver.receiverkubekube.model.RequestApplyPayment;
import com.totalplay.receiver.receiverkubekube.model.RequestUpdateSGA;
import com.totalplay.receiver.receiverkubekube.model.Respuesta;
import com.totalplay.receiver.receiverkubekube.utils.Utilidades;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PagoServiceC {

    private final BrmApiConfig brmConfig;
    private final SgaApiConfig sgaConfig;
    private final Utilidades utilidades;
    private final Templates templates;

    // P2: Consolidated ObjectMapper
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // P1: HTTP client with connection timeout
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ExecutorService loggingExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "async-logger");
        t.setDaemon(true);
        return t;
    });

    public PagoServiceC(BrmApiConfig brmConfig, SgaApiConfig sgaConfig,
                        Utilidades utilidades, Templates templates) {
        this.brmConfig = brmConfig;
        this.sgaConfig = sgaConfig;
        this.utilidades = utilidades;
        this.templates = templates;
    }

    @PreDestroy
    public void shutdown() {
        loggingExecutor.shutdown();
        try {
            loggingExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            loggingExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public boolean service(Request request) {
        // P1: Null checks to prevent infinite retry loops
        if (request == null) {
            log.error("Request is null");
            return false;
        }

        String requestId = getRequestIdentifier(request);
        log.info("Processing request: {}", requestId);

        boolean brmSuccess = true;
        boolean sgaSuccess = true;

        try {
            // P1: Null check before accessing nested objects
            RequestApplyPayment applyRequest = request.getRequestApply();
            if (applyRequest != null && !isLogTransactionOnly(applyRequest.getPaymentType())) {
                brmSuccess = processBrmPayment(request, applyRequest);
            }

            // P1: Null check before accessing nested objects
            RequestUpdateSGA sgaRequest = request.getRequestSGA();
            if (sgaRequest != null && !isLogTransactionOnly(sgaRequest.getIdTransaccionSGA())) {
                sgaSuccess = processSgaUpdate(request, sgaRequest);
            }

            boolean overall = brmSuccess;
            log.info("Request {} complete: brm={}, sga={}, overall={}",
                    requestId, brmSuccess, sgaSuccess, overall);
            return overall;

        } catch (Exception e) {
            log.error("Error processing request {}", requestId, e);
            return false;
        }
    }

    private boolean processBrmPayment(Request request, RequestApplyPayment applyRequest) {
        log.info("Processing BRM for account: {}", applyRequest.getAccount());

        try {
            Respuesta response = callBrmApi(applyRequest);

            Respuesta finalResponse = response;
            asyncLog(() -> utilidades.sendLog(request, finalResponse.toString(), brmConfig.getUrl()));
            if (response == null) {
                log.error("BRM API returned null");
                return false;
            }

            String result = response.getResult();
            if ("0".equals(result) || "C0003".equals(result)) {
                log.info("Pago ya realizado por el  account: {} response:{}", applyRequest.getAccount(),response);
                return true;
            }

            log.warn("BRM failed: result={}", result);
            return false;

        } catch (Exception e) {
            log.error("BRM API error ", e);
            return false;
        }
    }

    private boolean processSgaUpdate(Request request, RequestUpdateSGA sgaRequest) {
        log.info("Processing SGA: {} account:{}", sgaRequest.getIdTransaccionSGA(),request.getRequestApply().getAccount());

        try {
            String xmlRequest = templates.update(sgaRequest);
            String response = callSgaApi(xmlRequest);

            Respuesta respuestaSGA = new Respuesta();

            if (response != null && response.contains("resultado>0")) {
                log.info("SGA success: "+"-account:"+request.getRequestApply().getAccount()+"-response"+response);
                respuestaSGA.setResult("0");
                asyncLog(() -> utilidades.sendLog(request, respuestaSGA.toString(), sgaConfig.getActualiza()));
                return true;
            }

            log.warn("SGA failed:"+"-account:"+request.getRequestApply().getAccount()+"-response"+response);
            respuestaSGA.setResult("1");
            asyncLog(() -> utilidades.sendLog(request, response, sgaConfig.getActualiza()));
            return false;

        } catch (Exception e) {
            log.error("SGA API error", e);
            return false;
        }
    }

    // P1: HTTP timeout added
    private Respuesta callBrmApi(RequestApplyPayment request) throws Exception {
        try {
            String body = objectMapper.writeValueAsString(request);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(brmConfig.getUrl()))
                    .header("Content-Type", "application/json")
                    .header("idacceso", "AES792")
                    .timeout(Duration.ofSeconds(brmConfig.getTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());
            return objectMapper.readValue(response.body(), Respuesta.class);
        } catch (Exception e) {
            // 🔇 Silencioso a propósito (BRM / integraciones legacy)
            return null;
        }
    }

    private String callSgaApi(String xmlRequest) throws Exception {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(sgaConfig.getActualiza()))
                .header("Content-Type", "text/xml")
                .timeout(Duration.ofSeconds(sgaConfig.getTimeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(xmlRequest))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.warn("SGA returned HTTP {}", response.statusCode());
            return null;
        }

        return response.body();
    }

    private boolean isLogTransactionOnly(String value) {
        return "Solo LogTransaction".equals(value);
    }

    private String getRequestIdentifier(Request request) {
        if (request.getRequestApply() != null && request.getRequestApply().getAccount() != null) {
            return "account:" + request.getRequestApply().getAccount();
        }
        return "hash:" + System.identityHashCode(request);
    }

    private void asyncLog(Runnable action) {
        CompletableFuture.runAsync(action, loggingExecutor)
                .exceptionally(e -> {
                    log.warn("Async log failed: {}", e.getMessage());
                    return null;
                });
    }
}