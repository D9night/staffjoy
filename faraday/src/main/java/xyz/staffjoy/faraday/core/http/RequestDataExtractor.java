package xyz.staffjoy.faraday.core.http;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import xyz.staffjoy.faraday.exceptions.FaradayException;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;

import static java.util.Collections.list;
import static org.apache.commons.io.IOUtils.toByteArray;
import static org.apache.commons.lang3.StringUtils.EMPTY;

/**
 * 请求数据抽取器
 */
public class RequestDataExtractor {

    /**
     * 将请求body转为话byte[]
     * @param request
     * @return
     */
    public byte[] extractBody(HttpServletRequest request) {
        try {
            return toByteArray(request.getInputStream());
        } catch (IOException e) {
            throw new FaradayException("Error extracting body of HTTP request with URI: " + extractUri(request), e);
        }
    }

    /**
     * 抽取请求头
     * @param request
     * @return
     */
    public HttpHeaders extractHttpHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while(headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            List<String> value = list(request.getHeaders(name));
            headers.put(name, value);
        }
        return headers;
    }

    /**
     * 抽取请求的方法
     * @param request
     * @return
     */
    public HttpMethod extractHttpMethod(HttpServletRequest request) {
        return HttpMethod.resolve(request.getMethod());
    }

    /**
     * 抽取请求的uri
     * @param request
     * @return
     */
    public String extractUri(HttpServletRequest request) {
        return request.getRequestURI() + getQuery(request);
    }

    /**
     * 抽取请求的host
     * @param request
     * @return
     */
    public String extractHost(HttpServletRequest request) {
        return request.getServerName();
    }

    /**
     * 抽取请求的参数
     * @param request
     * @return
     */
    protected String getQuery(HttpServletRequest request) {
        return request.getQueryString() == null ? EMPTY : "?" + request.getQueryString();
    }
}
