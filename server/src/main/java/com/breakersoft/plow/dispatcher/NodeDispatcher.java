package com.breakersoft.plow.dispatcher;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.breakersoft.plow.ExitStatus;
import com.breakersoft.plow.Signal;
import com.breakersoft.plow.dispatcher.command.BookNodeCommand;
import com.breakersoft.plow.dispatcher.domain.DispatchJob;
import com.breakersoft.plow.dispatcher.domain.DispatchNode;
import com.breakersoft.plow.dispatcher.domain.DispatchProc;
import com.breakersoft.plow.dispatcher.domain.DispatchProject;
import com.breakersoft.plow.dispatcher.domain.DispatchResult;
import com.breakersoft.plow.dispatcher.domain.DispatchStats;
import com.breakersoft.plow.dispatcher.domain.DispatchTask;
import com.breakersoft.plow.event.EventManager;
import com.breakersoft.plow.rnd.thrift.RunTaskCommand;
import com.breakersoft.plow.rndaemon.RndClient;
import com.breakersoft.plow.thrift.TaskState;


@Component
public class NodeDispatcher implements Dispatcher<DispatchNode>{

    private static final Logger logger =
            org.slf4j.LoggerFactory.getLogger(NodeDispatcher.class);

    @Autowired
    EventManager eventManager;

    @Autowired
    private DispatchService dispatchService;

    private final ExecutorService dispatchThreads = Executors.newFixedThreadPool(8);

    public NodeDispatcher() { }

    /**
     * Queues node to be dispatched.
     * @param node
     */
    public void book(DispatchNode node) {
        if (!DispatchConfig.IS_ENABLED.get()) {
            return;
        }
        dispatchThreads.execute(new BookNodeCommand(node, this));
        DispatchStats.totalDispatchCount.incrementAndGet();
    }

    /*
     * Standard node dispatch methods.
     */
    public void dispatch(DispatchResult result, DispatchNode node) {

        logger.info("Dispatching: {}", node);

        //TODO: check to see if node is scheduled.
        // This will modify the javg runtime of frames
        // the node can target, and sets the backfill boolean
        // Or it may dispatch the node to a set location.

        // Return a list of projects that have a subscription
        // to the new.
        final List<DispatchProject> projects =
                dispatchService.getSortedProjectList(node);

        if (projects.isEmpty()) {
            logger.info("No dispatchable projects");
            return;
        }

        //TODO: use project_count table to keep track of active
        // jobs, layers, and tasks at the project level so we can
        // return a filtered project list.

        for (DispatchProject project: projects) {
            dispatch(result, node, project);
            if (!result.continueDispatching()) {
                return;
            }
        }
    }

    public void dispatch(DispatchResult result, DispatchNode node, DispatchProject project) {

        // Return a list of jobs IDs that have pending frames for the job/node.
        final List<DispatchJob> jobs = dispatchService.getDispatchJobs(project, node);

        if (jobs.isEmpty()) {
            logger.info("No dispatchable jobs for {}", project);
            return;
        }

        for (DispatchJob job: jobs) {
            dispatch(result, node, job);
            if (!result.continueDispatching()) {
                return;
            }
        }
    }

    public void dispatch(DispatchResult result, DispatchNode node, DispatchJob job) {

        final List<DispatchTask> tasks =
                dispatchService.getDispatchableTasks(job, node);

        if (tasks.isEmpty()) {
            logger.info("No dispatchable tasks for job: {}", job.getJobId());
            return;
        }

        for (DispatchTask task: tasks) {
            if (!result.canDispatch(task)) {
                continue;
            }
            dispatch(result, node, task);
            if (!result.continueDispatching()) {
                break;
            }
        }
    }

    public void dispatch(DispatchResult result, DispatchNode node, DispatchTask task) {

        if (!dispatchService.reserveTask(task)) {
            return;
        }

        DispatchProc proc = null;
        try {
            // Allocate the proc from the node.  This can throw if
            // the task is already running some where.
            proc = dispatchService.allocateProc(node, task);

            if (dispatchService.startTask(task, proc)) {
                RunTaskCommand command =
                        dispatchService.getRuntaskCommand(task);

                if (!result.isTest) {
                    RndClient client = new RndClient(node.getName());
                    client.runProcess(command);
                }
                result.dispatched(proc, task);
            }
            else {
                /*
                 * This is possible if a API command modifies the task.
                 */
                dispatchFailed(result, proc, task, "Critical, was able to reserve task but not start it.");
            }
        }
        catch (Exception e) {
            logger.warn("Failed to dispatch node " + node.getName() + "," + e, e);
            dispatchFailed(result, proc, task, e.getMessage());
        }
    }

    public void dispatchFailed(DispatchResult result, DispatchProc proc, DispatchTask task, String message) {
        logger.info("Unable to dispatch {}/{}, {}", new Object[] {proc, task, message});
        result.dispatch = false;
        dispatchService.deallocateProc(proc, message);

        if (task != null) {
            if (task.started) {
                dispatchService.stopTask(task, TaskState.WAITING, ExitStatus.FAIL, Signal.ABORTED_TASK);
            }
            else {
                dispatchService.unreserveTask(task);
            }
        }
    }
}
