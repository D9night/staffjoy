package xyz.staffjoy.faraday.core.http;

import com.github.structlog4j.ILogger;
import com.github.structlog4j.SLoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.filter.OncePerRequestFilter;
import xyz.staffjoy.faraday.config.FaradayProperties;
import xyz.staffjoy.faraday.config.MappingProperties;
import xyz.staffjoy.faraday.core.interceptor.PreForwardRequestInterceptor;
import xyz.staffjoy.faraday.core.mappings.MappingsProvider;
import xyz.staffjoy.faraday.core.trace.ProxyingTraceInterceptor;
import xyz.staffjoy.faraday.exceptions.FaradayException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.lang.String.valueOf;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.util.CollectionUtils.isEmpty;

/**
 * 反向代理过滤器
 * 基于servletfilter实现
 * 即网关的核心代码
 * 请求响应处理的主流程逻辑
 * 基于域名的路由方式(路由映射表)(静态配置和动态配置)(见application-dev.yml)(host-service)
 * httpClient映射表是基于路由映射表计算出来的(service-httpclient)
 *
 * ReverseProxyFilter通过FilterRegistrationBean注册到Spring容器环境中，Spring会自动将这个Filter注册到Web容器中。
 * 参考faraday项目源码中的config/FaradayConfiguration这个Bean配置文件。
 */
public class ReverseProxyFilter extends OncePerRequestFilter {//继承是spring  每个请求处理一次

    protected static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";
    protected static final String X_FORWARDED_PROTO_HEADER = "X-Forwarded-Proto";
    protected static final String X_FORWARDED_HOST_HEADER = "X-Forwarded-Host";
    protected static final String X_FORWARDED_PORT_HEADER = "X-Forwarded-Port";

    private static final ILogger log = SLoggerFactory.getLogger(ReverseProxyFilter.class);

    protected final FaradayProperties faradayProperties;
    //请求数据提取器
    protected final RequestDataExtractor extractor;
    //
    protected final MappingsProvider mappingsProvider;
    //请求转发器
    protected final RequestForwarder requestForwarder;
    protected final ProxyingTraceInterceptor traceInterceptor;
    protected final PreForwardRequestInterceptor preForwardRequestInterceptor;

    public ReverseProxyFilter(
            FaradayProperties faradayProperties,
            RequestDataExtractor extractor,
            MappingsProvider mappingsProvider,
            RequestForwarder requestForwarder,
            ProxyingTraceInterceptor traceInterceptor,
            PreForwardRequestInterceptor requestInterceptor
    ) {
        this.faradayProperties = faradayProperties;
        this.extractor = extractor;
        this.mappingsProvider = mappingsProvider;
        this.requestForwarder = requestForwarder;
        this.traceInterceptor = traceInterceptor;
        this.preForwardRequestInterceptor = requestInterceptor;
    }

    /**
     *  网关内部拦截
     * @param request
     * @param response
     * @param filterChain
     * @throws ServletException
     * @throws IOException
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String originUri = extractor.extractUri(request);
        String originHost = extractor.extractHost(request);

        log.debug("Incoming request", "method", request.getMethod(),
                "host", originHost,
                "uri", originUri);

        HttpHeaders headers = extractor.extractHttpHeaders(request);
        HttpMethod method = extractor.extractHttpMethod(request);

        //代理追踪  调式用的日志信息
        String traceId = traceInterceptor.generateTraceId();
        //一接收请求即调用
        traceInterceptor.onRequestReceived(traceId, method, originHost, originUri, headers);

        //查询路由映射表，找到相关的Mapping 即路由信息
        MappingProperties mapping = mappingsProvider.resolveMapping(originHost, request);
        if (mapping == null) {
            traceInterceptor.onNoMappingFound(traceId, method, originHost, originUri, headers);

            log.debug(String.format("Forwarding: %s %s %s -> no mapping found", method, originHost, originUri));

            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().println("Unsupported domain");
            return;
        } else {
            log.debug(String.format("Forwarding: %s %s %s -> %s", method, originHost, originUri, mapping.getDestinations()));
        }

        byte[] body = extractor.extractBody(request);
        //请求头添加数据
        addForwardHeaders(request, headers);

        //重新构造网关内的请求数据  用来转发(forward)的的请求数据
        RequestData dataToForward = new RequestData(method, originHost, originUri, headers, body, request);
        //请求转发之前拦截器，注意和重定向redirect的区别  请求截获器，进行预处理
        preForwardRequestInterceptor.intercept(dataToForward, mapping);

        //请求需要重定向，且重定向url不为空
        if (dataToForward.isNeedRedirect() && !isBlank(dataToForward.getRedirectUrl())) {
            log.debug(String.format("Redirecting to -> %s", dataToForward.getRedirectUrl()));
            //发送重定向请求
            response.sendRedirect(dataToForward.getRedirectUrl());
            return;
        }

        //进行实际的转发请求，并生成响应
        ResponseEntity<byte[]> responseEntity =
                requestForwarder.forwardHttpRequest(dataToForward, traceId, mapping);
        //生成实际的响应
        this.processResponse(response, responseEntity);
    }

    /**
     * 请求头添加数据
     * @param request
     * @param headers
     */
    protected void addForwardHeaders(HttpServletRequest request, HttpHeaders headers) {
        List<String> forwordedFor = headers.get(X_FORWARDED_FOR_HEADER);//X-Forwarded-For
        if (isEmpty(forwordedFor)) {
            forwordedFor = new ArrayList<>(1);
        }
        forwordedFor.add(request.getRemoteAddr());
        headers.put(X_FORWARDED_FOR_HEADER, forwordedFor);
        headers.set(X_FORWARDED_PROTO_HEADER, request.getScheme());
        headers.set(X_FORWARDED_HOST_HEADER, request.getServerName());
        headers.set(X_FORWARDED_PORT_HEADER, valueOf(request.getServerPort()));
    }

    /**
     * 生成实际的响应
     * @param response
     * @param responseEntity
     */
    protected void processResponse(HttpServletResponse response, ResponseEntity<byte[]> responseEntity) {
        response.setStatus(responseEntity.getStatusCode().value());
        responseEntity.getHeaders().forEach((name, values) ->
                values.forEach(value -> response.addHeader(name, value))
        );
        if (responseEntity.getBody() != null) {
            try {
                //输出响应数据
                response.getOutputStream().write(responseEntity.getBody());
            } catch (IOException e) {
                throw new FaradayException("Error writing body of HTTP response", e);
            }
        }
    }
}
