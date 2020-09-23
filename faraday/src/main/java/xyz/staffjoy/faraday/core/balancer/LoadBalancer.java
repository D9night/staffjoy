package xyz.staffjoy.faraday.core.balancer;

import java.util.List;

/**
 * 负载均衡器接口
 */
public interface LoadBalancer {
    //选择目的地
    String chooseDestination(List<String> destnations);
}
