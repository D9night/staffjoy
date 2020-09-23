package xyz.staffjoy.faraday.core.balancer;

import java.util.List;

import static java.util.concurrent.ThreadLocalRandom.current;

/**
 * 随机负载均衡器
 */
public class RandomLoadBalancer implements LoadBalancer {
    //选择目的主机
    @Override
    public String chooseDestination(List<String> destnations) {
        //随机产生主机index
        int hostIndex = destnations.size() == 1 ? 0 : current().nextInt(0, destnations.size());
        return destnations.get(hostIndex);
    }
}
