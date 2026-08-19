package com.nebysse.minetomesh.client.wand;

import com.nebysse.minetomesh.job.ExportJobManager;
import com.nebysse.minetomesh.job.ExportOptions;
import com.nebysse.minetomesh.job.ExportSummary;
import com.nebysse.minetomesh.job.ExportTelemetry;
import com.nebysse.minetomesh.job.ManagedJob;
import com.nebysse.minetomesh.network.ExportWandGrantedPayload;
import com.nebysse.minetomesh.network.ExportWandRejectedPayload;
import com.nebysse.minetomesh.output.ExportName;
import com.nebysse.minetomesh.world.BlockPoint;
import com.nebysse.minetomesh.world.Selection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;

public final class ExportWandController {
    public enum State {
        READY,
        WAITING_FOR_GRANT,
        EXPORTING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public interface JobStarter {
        ManagedJob start(
                Selection selection,
                ExportName name,
                ExportOptions options,
                ExportTelemetry telemetry) throws Exception;
    }

    public interface JobManagerPort {
        boolean start(ManagedJob job);

        void cancel(String reason);

        Optional<ManagedJob> activeJob();

        void tick();
    }

    private final JobStarter starter;
    private final JobManagerPort jobs;
    private final ExportTelemetry telemetry = new ExportTelemetry();

    private UUID boundWandId;
    private String boundDimension;
    private State state = State.READY;
    private ManagedJob ownedJob;
    private Optional<ExportSummary> summary = Optional.empty();
    private String rejectionKey = "";

    public ExportWandController(JobStarter starter, JobManagerPort jobs) {
        this.starter = Objects.requireNonNull(starter, "starter");
        this.jobs = Objects.requireNonNull(jobs, "jobs");
    }

    public void bind(UUID wandId, String dimension) {
        boundWandId = Objects.requireNonNull(wandId, "wandId");
        boundDimension = Objects.requireNonNull(dimension, "dimension");
        state = State.READY;
        ownedJob = null;
        summary = Optional.empty();
        rejectionKey = "";
    }

    public void requested(String exportName) {
        Objects.requireNonNull(exportName, "exportName");
        state = State.WAITING_FOR_GRANT;
        summary = Optional.empty();
        rejectionKey = "";
    }

    public boolean accept(ExportWandGrantedPayload grant) {
        Objects.requireNonNull(grant, "grant");
        if (state != State.WAITING_FOR_GRANT
                || boundWandId == null
                || boundDimension == null
                || !boundWandId.equals(grant.wandId())
                || !boundDimension.equals(grant.dimension())) {
            return false;
        }
        ManagedJob active = jobs.activeJob().orElse(null);
        if (active != null && !active.isTerminal()) {
            state = State.FAILED;
            rejectionKey = "minetomesh.error.wand.already_running";
            return false;
        }
        try {
            ExportName name = ExportName.parse(grant.exportName());
            Selection selection = selectionFrom(grant);
            ManagedJob job = starter.start(selection, name,
                    new ExportOptions(grant.includePlayers()), telemetry);
            if (!jobs.start(job)) {
                state = State.FAILED;
                rejectionKey = "minetomesh.error.wand.already_running";
                return false;
            }
            ownedJob = job;
            state = State.EXPORTING;
            return true;
        } catch (Exception exception) {
            state = State.FAILED;
            rejectionKey = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            return false;
        }
    }

    public void reject(ExportWandRejectedPayload payload) {
        Objects.requireNonNull(payload, "payload");
        if (state == State.WAITING_FOR_GRANT
                && boundWandId != null
                && boundWandId.equals(payload.wandId())) {
            state = State.FAILED;
            rejectionKey = payload.reasonKey();
        }
    }

    public void unbind() {
        boundWandId = null;
        boundDimension = null;
        state = State.READY;
        ownedJob = null;
        summary = Optional.empty();
        rejectionKey = "";
    }

    public void screenClosed() {
        ManagedJob active = jobs.activeJob().orElse(null);
        if (ownedJob != null && active == ownedJob && !ownedJob.isTerminal()) {
            jobs.cancel("screen_closed");
        }
        ownedJob = null;
        if (state == State.WAITING_FOR_GRANT || state == State.EXPORTING) {
            state = State.CANCELLED;
        }
    }

    public void tick() {
        if (ownedJob != null && ownedJob.isTerminal()) {
            summary = ownedJob.summary();
            state = switch (ownedJob.state()) {
                case COMPLETED -> State.COMPLETED;
                case CANCELLED -> State.CANCELLED;
                default -> State.FAILED;
            };
            ownedJob = null;
        }
    }

    public State state() {
        return state;
    }

    public Optional<ExportSummary> summary() {
        return summary;
    }

    public String rejectionKey() {
        return rejectionKey;
    }

    public ExportTelemetry telemetry() {
        return telemetry;
    }

    private static Selection selectionFrom(ExportWandGrantedPayload grant) {
        return Selection.of(
                blockPoint(grant.dimension(), grant.first()),
                blockPoint(grant.dimension(), grant.second()));
    }

    private static BlockPoint blockPoint(String dimension, BlockPos position) {
        return new BlockPoint(
                dimension, position.getX(), position.getY(), position.getZ());
    }

    public static JobManagerPort fromManager(ExportJobManager manager) {
        Objects.requireNonNull(manager, "manager");
        return new JobManagerPort() {
            @Override
            public boolean start(ManagedJob job) {
                return manager.start(job);
            }

            @Override
            public void cancel(String reason) {
                manager.cancel(reason);
            }

            @Override
            public Optional<ManagedJob> activeJob() {
                return manager.activeJob();
            }

            @Override
            public void tick() {
                manager.tick();
            }
        };
    }
}
