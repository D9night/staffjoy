package xyz.staffjoy.faraday.core.trace;

/**
 * 追踪拦截
 */
public interface TraceInterceptor {

    //一接收请求即调用进行追踪拦截
    void onRequestReceived(String traceId, IncomingRequest request);

    //没有匹配到相关请求进行追踪拦截
    void onNoMappingFound(String traceId, IncomingRequest request);

    //转发开始时进行追踪拦截
    void onForwardStart(String traceId, ForwardRequest request);

    void onForwardError(String traceId, Throwable error);

    void onForwardComplete(String traceId, ReceivedResponse response);
}
