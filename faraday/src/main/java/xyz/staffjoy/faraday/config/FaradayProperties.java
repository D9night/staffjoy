package xyz.staffjoy.faraday.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;

/**
 * Faraday configuration properties
 * 配置属性
 */
@ConfigurationProperties("faraday")
public class FaradayProperties {
    /**
     * Faraday servlet filter order.
     * highest_precedence
     */
    private int filterOrder = HIGHEST_PRECEDENCE + 100;
    /**
     * Enable programmatic mapping or not,
     * false only in dev environment, in dev we use mapping via configuration file
     * 动态配置 开发环境通过配置文件
     */
    private boolean enableProgrammaticMapping = true;
    /**
     * Properties responsible for collecting metrics during HTTP requests forwarding.
     * 收集http请求调用链
     */
    @NestedConfigurationProperty
    private MetricsProperties metrics = new MetricsProperties();
    /**
     * Properties responsible for tracing HTTP requests proxying processes.
     * 追踪http请求代理过程
     */
    @NestedConfigurationProperty
    private TracingProperties tracing = new TracingProperties();
    /**
     * List of proxy mappings.
     */
    @NestedConfigurationProperty
    private List<MappingProperties> mappings = new ArrayList<>();

    public int getFilterOrder() {
        return filterOrder;
    }

    public void setFilterOrder(int filterOrder) {
        this.filterOrder = filterOrder;
    }

    public MetricsProperties getMetrics() {
        return metrics;
    }

    public void setMetrics(MetricsProperties metrics) {
        this.metrics = metrics;
    }

    public TracingProperties getTracing() {
        return tracing;
    }

    public void setTracing(TracingProperties tracing) {
        this.tracing = tracing;
    }

    public List<MappingProperties> getMappings() {
        return mappings;
    }

    public void setMappings(List<MappingProperties> mappings) {
        this.mappings = mappings;
    }

    public boolean isEnableProgrammaticMapping() {
        return this.enableProgrammaticMapping;
    }

    public void setEnableProgrammaticMapping(boolean enableProgrammaticMapping) {
        this.enableProgrammaticMapping = enableProgrammaticMapping;
    }
}
