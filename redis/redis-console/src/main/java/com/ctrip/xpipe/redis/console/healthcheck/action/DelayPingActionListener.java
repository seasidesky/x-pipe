package com.ctrip.xpipe.redis.console.healthcheck.action;

import com.ctrip.xpipe.api.factory.ObjectFactory;
import com.ctrip.xpipe.api.observer.Observable;
import com.ctrip.xpipe.api.observer.Observer;
import com.ctrip.xpipe.concurrent.AbstractExceptionLogTask;
import com.ctrip.xpipe.endpoint.HostPort;
import com.ctrip.xpipe.redis.console.healthcheck.*;
import com.ctrip.xpipe.redis.console.healthcheck.delay.DelayActionContext;
import com.ctrip.xpipe.redis.console.healthcheck.ping.PingActionContext;
import com.ctrip.xpipe.redis.console.spring.ConsoleContextConfig;
import com.ctrip.xpipe.utils.MapUtils;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author chen.zhu
 * <p>
 * Sep 06, 2018
 */
@Component
public class DelayPingActionListener implements HealthCheckActionListener<ActionContext> {

    private static final Logger logger = LoggerFactory.getLogger(DelayPingActionListener.class);

    private Map<RedisHealthCheckInstance, HealthStatus> allHealthStatus = Maps.newConcurrentMap();

    @Resource(name = ConsoleContextConfig.SCHEDULED_EXECUTOR)
    private ScheduledExecutorService scheduled;

    @Resource(name = ConsoleContextConfig.GLOBAL_EXECUTOR)
    private ExecutorService executors;

    @Autowired
    private List<HealthEventProcessor> healthEventProcessors;

    @Autowired
    private HealthCheckInstanceManager instanceManager;

    @Override
    public void onAction(ActionContext context) {
        if (context instanceof DelayActionContext) {
            onAction((DelayActionContext) context);
        }
        if (context instanceof PingActionContext) {
            onAction((PingActionContext) context);
        }
    }

    @Override
    public boolean worksfor(ActionContext t) {
        return t instanceof DelayActionContext || t instanceof PingActionContext;
    }

    @Override
    public void stopWatch(HealthCheckAction action) {
        HealthStatus healthStatus = allHealthStatus.remove(action.getActionInstance());
        if(healthStatus != null) {
            healthStatus.stop();
        }
    }

    public HEALTH_STATE getState(HostPort hostPort) {
        try {
            return allHealthStatus.get(instanceManager.findRedisHealthCheckInstance(hostPort)).getState();
        } catch (Exception e) {
            return HEALTH_STATE.UNKNOWN;
        }
    }

    @PreDestroy
    public void preDestroy() {
        for(HealthStatus healthStatus : allHealthStatus.values()) {
            healthStatus.stop();
        }
    }

    private void onAction(DelayActionContext delayActionContext) {
        long delayNano = delayActionContext.getResult();
        createOrGet(delayActionContext.instance()).delay(TimeUnit.NANOSECONDS.toMillis(delayNano));
    }


    private void onAction(PingActionContext pingActionContext) {
        if (pingActionContext.getResult()) {
            createOrGet(pingActionContext.instance()).pong();
        }
    }

    private HealthStatus createOrGet(RedisHealthCheckInstance instance) {

        return MapUtils.getOrCreate(allHealthStatus, instance, new ObjectFactory<HealthStatus>() {
            @Override
            public HealthStatus create() {

                HealthStatus healthStatus = new HealthStatus(instance, scheduled);

                healthStatus.addObserver(new Observer() {
                    @Override
                    public void update(Object args, Observable observable) {
                        onInstanceStateChange((AbstractInstanceEvent) args);
                    }
                });
                healthStatus.start();
                return healthStatus;
            }
        });
    }

    protected void onInstanceStateChange(Object args) {

        logger.info("[onInstanceStateChange]{}", args);

        //TODO delete instance not exist

        for (HealthEventProcessor processor : healthEventProcessors) {

            executors.execute(new AbstractExceptionLogTask() {
                @Override
                protected void doRun() throws Exception {
                    processor.onEvent((AbstractInstanceEvent) args);
                }
            });
        }
    }
}