package com.bazaarvoice.auth.hmac.client;

import com.bazaarvoice.auth.hmac.common.RequestConfiguration;
import com.bazaarvoice.auth.hmac.common.SignatureGenerator;
import com.bazaarvoice.auth.hmac.common.TimeUtils;
import com.bazaarvoice.auth.hmac.common.Version;
import org.glassfish.jersey.client.ClientRequest;
import org.glassfish.jersey.message.internal.OutboundMessageContext;

import javax.ws.rs.core.UriBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

/**
 * Encodes outbound HTTP requests with security credentials so that they can be authenticated
 * by the receiving server.
 */
public class RequestEncoder {
    private final String apiKey;
    private final String secretKey;
    private final SignatureGenerator signatureGenerator;
    private final RequestConfiguration requestConfiguration;

    public RequestEncoder(String apiKey,
                          String secretKey,
                          SignatureGenerator signatureGenerator,
                          RequestConfiguration requestConfiguration) {

        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.signatureGenerator = signatureGenerator;
        this.requestConfiguration = requestConfiguration;
    }

    public void encode(ClientRequest request) {
        String timestamp = TimeUtils.getCurrentTimestamp();
        addApiKey(request);
        addTimestamp(request, timestamp);
        addSignature(request, timestamp);
        addVersion(request, Version.V1);
    }

    private void addApiKey(ClientRequest request) {
        URI uriWithApiKey = UriBuilder.fromUri(request.getUri())
                .queryParam(this.requestConfiguration.getApiKeyQueryParamName(), apiKey)
                .build();

        request.setUri(uriWithApiKey);
    }

    private void addSignature(ClientRequest request, String timestamp) {
        String signature = buildSignature(request, timestamp);
        request.getHeaders().putSingle(this.requestConfiguration.getSignatureHttpHeader(), signature);
    }

    private void addTimestamp(ClientRequest request, String timestamp) {
        request.getHeaders().putSingle(this.requestConfiguration.getTimestampHttpHeader(), timestamp);
    }

    private void addVersion(ClientRequest request, Version version) {
        request.getHeaders().putSingle(this.requestConfiguration.getVersionHttpHeader(), version.toString());
    }

    private String buildSignature(ClientRequest request, String timestamp) {
        String method = getMethod(request);
        String path = getPath(request);
        byte[] content = getContent(request);
        return signatureGenerator.generate(secretKey, method, timestamp, path, content);
    }

    private String getMethod(ClientRequest request) {
        return request.getMethod();
    }

    private String getPath(ClientRequest request) {
        // Get the path and any query parameters (e.g. /api/v1/pizza?sort=toppings&apiKey=someKey)
        return String.format("%s?%s", request.getUri().getPath(), request.getUri().getQuery());
    }

    private byte[] getContent(ClientRequest request) {
        return getSerializedEntity(request);
    }

    private byte[] getSerializedEntity(ClientRequest request) {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        request.setStreamProvider(new OutboundMessageContext.StreamProvider() {
            @Override
            public OutputStream getOutputStream(int contentLength) throws IOException {
                return outputStream;
            }
        });
        return outputStream.toByteArray();
    }

}
