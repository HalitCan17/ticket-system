package com.itsm.backend.spike;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.itsm.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.kie.kogito.internal.process.event.DefaultKogitoProcessEventListener;
import org.kie.api.event.process.ProcessCompletedEvent;
import org.kie.api.runtime.process.WorkflowProcessInstance;
import org.kie.kogito.auth.IdentityProvider;
import org.kie.kogito.auth.IdentityProviders;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.usertask.UserTaskService;
import org.kie.kogito.usertask.view.UserTaskView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * jBPM spike (Aşama 0): starts the single-user-task BPMN process generated from
 * {@code processes/spike/spike-approval.bpmn}, completes the task and checks the process ends.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, JbpmSpikeTest.CompletionRecorderConfig.class})
class JbpmSpikeTest {

    @Autowired
    @Qualifier("spikeApproval")
    Process<SpikeApprovalModel> spikeApproval;

    @Autowired
    UserTaskService userTaskService;

    @Autowired
    CompletionRecorder completionRecorder;

    @Test
    void startsProcessAndCompletesSingleUserTask() {
        SpikeApprovalModel input = spikeApproval.createModel();
        input.setTicketId("TCK-1");

        ProcessInstance<SpikeApprovalModel> instance = spikeApproval.createInstance(input);
        instance.start();

        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        // Task is assigned to group AGENT via the GroupId input in the BPMN.
        IdentityProvider agent = IdentityProviders.of("agent1", List.of("AGENT"));
        List<UserTaskView> tasks = userTaskService.list(agent).stream()
                .filter(t -> "TCK-1".equals(t.getInputs().get("ticketId")))
                .toList();
        assertThat(tasks).hasSize(1);
        UserTaskView task = tasks.get(0);
        assertThat(task.getTaskName()).isEqualTo("Review");

        // A user outside the group must not see the task.
        IdentityProvider customer = IdentityProviders.of("customer1", List.of("CUSTOMER"));
        assertThat(userTaskService.list(customer))
                .noneMatch(t -> t.getId().equals(task.getId()));

        // Default lifecycle: Ready --claim--> Reserved --complete--> Completed
        userTaskService.transition(task.getId(), "claim", Map.of(), agent).orElseThrow();
        UserTaskView completed = userTaskService
                .transition(task.getId(), "complete", Map.of("approved", true), agent)
                .orElseThrow();

        assertThat(completed.getStatus().getName()).isEqualTo("Completed");
        assertThat(completionRecorder.approvedByInstanceId).containsEntry(instance.id(), true);
        assertThat(spikeApproval.instances().findById(instance.id())).isEmpty();
    }

    /** Captures process completion and the final {@code approved} variable. */
    static class CompletionRecorder extends DefaultKogitoProcessEventListener {

        final Map<String, Object> approvedByInstanceId = new ConcurrentHashMap<>();

        @Override
        public void afterProcessCompleted(ProcessCompletedEvent event) {
            WorkflowProcessInstance pi = (WorkflowProcessInstance) event.getProcessInstance();
            if ("spikeApproval".equals(pi.getProcessId())) {
                approvedByInstanceId.put(pi.getId(), pi.getVariable("approved"));
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CompletionRecorderConfig {

        @Bean
        CompletionRecorder completionRecorder() {
            return new CompletionRecorder();
        }
    }
}
