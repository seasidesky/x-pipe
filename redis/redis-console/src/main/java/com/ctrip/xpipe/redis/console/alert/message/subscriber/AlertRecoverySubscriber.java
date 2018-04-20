package com.ctrip.xpipe.redis.console.alert.message.subscriber;

import com.ctrip.xpipe.command.AbstractCommand;
import com.ctrip.xpipe.concurrent.AbstractExceptionLogTask;
import com.ctrip.xpipe.redis.console.alert.AlertEntity;
import com.ctrip.xpipe.redis.console.alert.AlertMessageEntity;
import com.google.common.collect.Sets;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author chen.zhu
 * <p>
 * Apr 20, 2018
 */

@Component
public class AlertRecoverySubscriber extends AbstractAlertEntitySubscriber {

    private Set<AlertEntity> unRecoveredAlerts = Sets.newConcurrentHashSet();

    @PostConstruct
    public void scheduledRecoverAlertReport() {
        scheduled.scheduleWithFixedDelay(new AbstractExceptionLogTask() {
            @Override
            protected void doRun() throws Exception {
                if(unRecoveredAlerts.isEmpty()) {
                    return;
                }

                RecoveredAlertCleaner cleaner = new RecoveredAlertCleaner();
                cleaner.future().addListener(commandFuture -> {
                    if(commandFuture.isSuccess()) {
                        Set<AlertEntity> recovered = commandFuture.getNow();
                        new ReportRecoveredAlertTask(recovered).execute(executors);
                    }
                });
                cleaner.execute(executors);
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    @Override
    protected void doProcessAlert(AlertEntity alert) {
        if(!alert.getAlertType().reportRecovery()) {
            return;
        }

        while(!unRecoveredAlerts.add(alert)) {
            unRecoveredAlerts.remove(alert);
        }
    }

    class RecoveredAlertCleaner extends AbstractCommand<Set<AlertEntity>> {

        @Override
        protected void doExecute() throws Exception {
            Set<AlertEntity> recovered = Sets.newHashSet();
            if(unRecoveredAlerts == null || unRecoveredAlerts.isEmpty()) {
                future().setSuccess(recovered);
                return;
            }
            for(AlertEntity alert : unRecoveredAlerts) {
                if(alertRecovered(alert)) {
                    recovered.add(alert);
                }
            }
            unRecoveredAlerts.removeAll(recovered);
            future().setSuccess(recovered);
        }

        @Override
        protected void doReset() {

        }

        @Override
        public String getName() {
            return RecoveredAlertCleaner.class.getSimpleName();
        }
    }

    class ReportRecoveredAlertTask extends AbstractCommand<Void> {

        private Set<AlertEntity> alerts;

        public ReportRecoveredAlertTask(Set<AlertEntity> alerts) {
            this.alerts = alerts;
        }

        @Override
        protected void doExecute() throws Exception {
            if(alerts == null || alerts.isEmpty()) {
                return;
            }
            for(AlertEntity alert : alerts) {
                AlertMessageEntity message = getMessage(alert, false);
                emailMessage(message);
            }
            future().setSuccess();
        }

        @Override
        protected void doReset() {

        }

        @Override
        public String getName() {
            return ReportRecoveredAlertTask.class.getSimpleName();
        }
    }
}
