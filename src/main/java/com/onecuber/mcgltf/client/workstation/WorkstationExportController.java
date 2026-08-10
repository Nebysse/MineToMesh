package com.onecuber.mcgltf.client.workstation;

import com.onecuber.mcgltf.job.ExportJobManager;
import com.onecuber.mcgltf.job.ExportSummary;
import com.onecuber.mcgltf.job.ExportTelemetry;
import com.onecuber.mcgltf.job.JobState;
import com.onecuber.mcgltf.job.ManagedJob;
import com.onecuber.mcgltf.network.ExportGrantedPayload;
import com.onecuber.mcgltf.network.ExportRejectedPayload;
import com.onecuber.mcgltf.output.ExportName;
import com.onecuber.mcgltf.workstation.WorkstationCoordinates;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;

public final class WorkstationExportController {
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
                WorkstationCoordinates coordinates, String dimension,
                ExportName name, ExportTelemetry telemetry) throws Exception;
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

    private BlockPos boundStation;
    private String boundDimension;
    private State state = State.READY;
    private ManagedJob ownedJob;
    private Optional<ExportSummary> summary = Optional.empty();
    private String rejectionKey = "";

    public WorkstationExportController(JobStarter starter, JobManagerPort jobs) {
        this.starter = Objects.requireNonNull(starter, "starter");
        this.jobs = Objects.requireNonNull(jobs, "jobs");
    }

    public void bind(BlockPos station, String dimension) {
        this.boundStation = Objects.requireNonNull(station, "station");
        this.boundDimension = Objects.requireNonNull(dimension, "dimension");
        this.state = State.READY;
        this.ownedJob = null;
        this.summary = Optional.empty();
        this.rejectionKey = "";
    }

    public void requested(String exportName) {
        this.state = State.WAITING_FOR_GRANT;
        this.summary = Optional.empty();
    }

    public boolean accept(ExportGrantedPayload grant) {
        Objects.requireNonNull(grant, "grant");
        if (state != State.WAITING_FOR_GRANT
                || boundStation == null
                || boundDimension == null
                || !boundStation.equals(grant.stationPos())
                || !boundDimension.equals(grant.dimension())) {
            return false;
        }
        ManagedJob active = jobs.activeJob().orElse(null);
        if (active != null && !active.isTerminal()) {
            state = State.FAILED;
            rejectionKey = "mcgltf.error.workstation.already_running";
            return false;
        }
        try {
            ExportName name = ExportName.parse(grant.exportName());
            WorkstationCoordinates coordinates = new WorkstationCoordinates(
                    grant.first(), grant.second());
            ManagedJob job = starter.start(
                    coordinates, grant.dimension(), name, telemetry);
            if (!jobs.start(job)) {
                state = State.FAILED;
                rejectionKey = "mcgltf.error.workstation.already_running";
                return false;
            }
            ownedJob = job;
            state = State.EXPORTING;
            return true;
        } catch (Exception exception) {
            state = State.FAILED;
            rejectionKey = exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage();
            return false;
        }
    }

    public void reject(ExportRejectedPayload payload) {
        Objects.requireNonNull(payload, "payload");
        if (state == State.WAITING_FOR_GRANT
                && boundStation.equals(payload.stationPos())) {
            state = State.FAILED;
            rejectionKey = payload.reasonKey();
        }
    }

    public void unbind() {
        this.boundStation = null;
        this.boundDimension = null;
        this.state = State.READY;
        this.ownedJob = null;
        this.summary = Optional.empty();
        this.rejectionKey = "";
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
        jobs.tick();
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
