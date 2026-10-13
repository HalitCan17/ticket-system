package com.itsm.backend.servicecatalog;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.itsm.backend.security.CurrentUser;
import org.kie.kogito.Model;
import org.kie.kogito.auth.IdentityProvider;
import org.kie.kogito.auth.IdentityProviders;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.usertask.UserTaskService;
import org.kie.kogito.usertask.view.UserTaskView;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Our only door to the jBPM engine for service requests. Identities passed to the engine come from the verified JWT
 * ({@link CurrentUser}); the generated REST endpoints stay disabled.
 */
@Component
public class ApprovalFlow {

    static final String APPROVER_GROUP = "MANAGER";

    /** Typed by the generated model, which exists only after codegen; we use the generic {@link Model} API. */
    private final Process<? extends Model> process;
    private final UserTaskService userTasks;

    public ApprovalFlow(@Qualifier("serviceRequest") Process<? extends Model> process, UserTaskService userTasks) {
        this.process = process;
        this.userTasks = userTasks;
    }

    /** Starts the process; without approval it runs to the end (fulfilment) before returning. */
    public String start(long requestId, boolean requiresApproval) {
        return start(process, Map.of("requestId", requestId, "requiresApproval", requiresApproval));
    }

    private static <T extends Model> String start(Process<T> process, Map<String, Object> variables) {
        T model = process.createModel();
        model.fromMap(variables);
        ProcessInstance<T> instance = process.createInstance(model);
        instance.start();
        return instance.id();
    }

    /** Completes the approval task of the request as the deciding manager; the process then fulfils or rejects. */
    public void decide(CurrentUser manager, long requestId, boolean approved) {
        IdentityProvider identity = identity(manager);
        UserTaskView task = approvalTask(identity, requestId).orElseThrow(
                () -> new IllegalStateException("No approval task for service request " + requestId));
        userTasks.transition(task.getId(), "claim", Map.of(), identity).orElseThrow();
        userTasks.transition(task.getId(), "complete", Map.of("approved", approved), identity).orElseThrow();
    }

    /** Whether the manager can see the request's open approval task (used by tests and checks). */
    public boolean hasApprovalTask(CurrentUser user, long requestId) {
        return approvalTask(identity(user), requestId).isPresent();
    }

    /** Ends a waiting process when its requester withdraws the request. */
    public void abort(String processInstanceId) {
        if (processInstanceId != null) {
            process.instances().findById(processInstanceId).ifPresent(ProcessInstance::abort);
        }
    }

    public boolean isActive(String processInstanceId) {
        return processInstanceId != null && process.instances().findById(processInstanceId).isPresent();
    }

    private Optional<UserTaskView> approvalTask(IdentityProvider identity, long requestId) {
        String id = String.valueOf(requestId);
        return userTasks.list(identity).stream()
                .filter(t -> id.equals(String.valueOf(t.getInputs().get("requestId"))))
                .filter(t -> !isFinished(t))
                .findFirst();
    }

    private static boolean isFinished(UserTaskView task) {
        return task.getStatus() != null && task.getStatus().isTerminate();
    }

    private static IdentityProvider identity(CurrentUser user) {
        return IdentityProviders.of(user.id(), List.of(user.role().name()));
    }
}
