package com.lhj.jizhang.common.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(TraceIdFilter.class);
    private static final String MASKED_VALUE = "***";
    private static final String EMPTY_VALUE = "-";
    private static final Pattern VALID_TRACE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "token", "accesstoken", "refreshtoken", "authorization",
            "appsecret", "secret", "secretbase64", "sessionkey", "openid", "unionid"
    );

    private final ObjectMapper objectMapper;
    private final boolean includeBody;
    private final int maxPayloadLength;

    public TraceIdFilter(
            ObjectMapper objectMapper,
            @Value("${app.logging.http.include-body:false}") boolean includeBody,
            @Value("${app.logging.http.max-payload-length:4096}") int maxPayloadLength
    ) {
        this.objectMapper = objectMapper;
        this.includeBody = includeBody;
        this.maxPayloadLength = Math.max(maxPayloadLength, 256);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        MDC.put("traceId", traceId);
        response.setHeader("X-Trace-Id", traceId);
        long startTime = System.nanoTime();
        if (includeBody && request.getRequestURI().startsWith("/api/")) {
            logWithPayload(request, response, filterChain, startTime);
            return;
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            logAccess(request, response, startTime);
            MDC.remove("traceId");
        }
    }

    private void logWithPayload(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain,
            long startTime
    ) throws ServletException, IOException {
        ContentCachingRequestWrapper requestWrapper =
                new ContentCachingRequestWrapper(request, maxPayloadLength + 1);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            try {
                LOGGER.info("HTTP REQUEST {} {} params={} body={}", request.getMethod(), request.getRequestURI(),
                        formatParameters(requestWrapper), formatRequestBody(requestWrapper));
                LOGGER.info("HTTP RESPONSE {} {} status={} durationMs={} body={}", request.getMethod(),
                        request.getRequestURI(), responseWrapper.getStatus(), elapsedMillis(startTime),
                        formatResponseBody(responseWrapper));
            } finally {
                try {
                    responseWrapper.copyBodyToResponse();
                } finally {
                    MDC.remove("traceId");
                }
            }
        }
    }

    private void logAccess(HttpServletRequest request, HttpServletResponse response, long startTime) {
        LOGGER.info("HTTP {} {} status={} durationMs={}", request.getMethod(), request.getRequestURI(),
                response.getStatus(), elapsedMillis(startTime));
    }

    private String formatParameters(HttpServletRequest request) {
        if (request.getParameterMap().isEmpty()) {
            return EMPTY_VALUE;
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        request.getParameterMap().forEach((name, values) ->
                parameters.put(name, isSensitiveField(name, true) ? MASKED_VALUE : values));
        try {
            return truncate(objectMapper.writeValueAsString(parameters));
        } catch (JsonProcessingException exception) {
            return "[parameters unavailable]";
        }
    }

    private String formatRequestBody(ContentCachingRequestWrapper request) {
        return formatBody(request.getContentAsByteArray(), request.getContentType(), request.getCharacterEncoding(), true);
    }

    private String formatResponseBody(ContentCachingResponseWrapper response) {
        return formatBody(response.getContentAsByteArray(), response.getContentType(), response.getCharacterEncoding(), false);
    }

    private String formatBody(byte[] content, String contentType, String characterEncoding, boolean requestBody) {
        if (content.length == 0) {
            return EMPTY_VALUE;
        }
        if (!isJson(contentType)) {
            return "[content omitted: " + (contentType == null ? "unknown" : contentType) + "]";
        }
        String payload = new String(content, resolveCharset(contentType, characterEncoding));
        try {
            JsonNode root = objectMapper.readTree(payload);
            redact(root, requestBody);
            return truncate(objectMapper.writeValueAsString(root));
        } catch (JsonProcessingException exception) {
            return "[invalid JSON omitted]";
        }
    }

    private void redact(JsonNode node, boolean requestBody) {
        if (node instanceof ObjectNode objectNode) {
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isSensitiveField(field.getKey(), requestBody)) {
                    objectNode.put(field.getKey(), MASKED_VALUE);
                } else {
                    redact(field.getValue(), requestBody);
                }
            }
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> redact(child, requestBody));
        }
    }

    private boolean isSensitiveField(String fieldName, boolean requestBody) {
        String normalizedName = fieldName.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
        return SENSITIVE_FIELDS.contains(normalizedName)
                || normalizedName.contains("password")
                || normalizedName.endsWith("token")
                || normalizedName.endsWith("secret")
                || requestBody && "code".equals(normalizedName);
    }

    private boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            return MediaType.APPLICATION_JSON.includes(mediaType)
                    || mediaType.getSubtype().toLowerCase(Locale.ROOT).endsWith("+json");
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private Charset resolveCharset(String contentType, String characterEncoding) {
        try {
            Charset mediaTypeCharset = MediaType.parseMediaType(contentType).getCharset();
            if (mediaTypeCharset != null) {
                return mediaTypeCharset;
            }
            return StandardCharsets.UTF_8;
        } catch (IllegalArgumentException exception) {
            try {
                return characterEncoding == null ? StandardCharsets.UTF_8 : Charset.forName(characterEncoding);
            } catch (IllegalArgumentException ignored) {
                return StandardCharsets.UTF_8;
            }
        }
    }

    private String truncate(String value) {
        if (value.length() <= maxPayloadLength) {
            return value;
        }
        return value.substring(0, maxPayloadLength) + "...[truncated]";
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId != null && VALID_TRACE_ID.matcher(traceId).matches()) {
            return traceId;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private long elapsedMillis(long startTime) {
        return (System.nanoTime() - startTime) / 1_000_000;
    }
}
