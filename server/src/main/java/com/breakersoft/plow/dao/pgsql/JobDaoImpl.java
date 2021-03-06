package com.breakersoft.plow.dao.pgsql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TJSONProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.stereotype.Repository;

import com.breakersoft.plow.FilterableJob;
import com.breakersoft.plow.Folder;
import com.breakersoft.plow.Job;
import com.breakersoft.plow.JobE;
import com.breakersoft.plow.JobId;
import com.breakersoft.plow.Project;
import com.breakersoft.plow.dao.AbstractDao;
import com.breakersoft.plow.dao.JobDao;
import com.breakersoft.plow.exceptions.JobSpecException;
import com.breakersoft.plow.thrift.JobSpecT;
import com.breakersoft.plow.thrift.JobState;
import com.breakersoft.plow.thrift.TaskState;
import com.breakersoft.plow.util.JdbcUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Repository
public final class JobDaoImpl extends AbstractDao implements JobDao {

    private static final Logger logger = LoggerFactory.getLogger(JobDaoImpl.class);

    public static final RowMapper<Job> MAPPER = new RowMapper<Job>() {

        @Override
        public Job mapRow(ResultSet rs, int rowNum)
                throws SQLException {
            JobE job = new JobE();
            job.setJobId((UUID) rs.getObject(1));
            job.setProjectId((UUID) rs.getObject(2));
            job.setFolderId((UUID) rs.getObject(3));
            job.setName(rs.getString(4));
            return job;
        }
    };

    private static final String GET =
            "SELECT " +
                "pk_job,"+
                "pk_project, " +
                "pk_folder, " +
                "str_name " +
            "FROM " +
                "plow.job ";

    @Override
    public Job get(String name, JobState state) {
        return jdbc.queryForObject(
                GET + "WHERE str_name=? AND int_state=?",
                MAPPER, name, state.ordinal());
    }

    @Override
    public Job getActive(String name) {
        return jdbc.queryForObject(
                GET + "WHERE str_active_name=?", MAPPER, name);
    }

    @Override
    public Job getActive(UUID id) {
        return jdbc.queryForObject(
                GET + "WHERE pk_job=? AND int_state!=?", MAPPER,
                id, JobState.FINISHED.ordinal());
    }

    @Override
    public Job getByActiveNameOrId(String identifer) {
        try {
            return getActive(UUID.fromString(identifer));
        } catch (IllegalArgumentException e) {
            return getActive(identifer);
        }
    }

    @Override
    public Job get(UUID id) {
        return jdbc.queryForObject(
                GET + "WHERE pk_job=?",
                MAPPER, id);
    }

    @Override
    public void setPaused(Job job, boolean value) {
        jdbc.update("UPDATE plow.job SET bool_paused=? WHERE pk_job=?",
                value, job.getJobId());
    }

    private static final String INSERT[] = {
        JdbcUtils.Insert("plow.job",
                "pk_job", "pk_project", "str_name", "str_active_name",
                "str_username", "int_uid", "int_state", "bool_paused",
                "str_log_path", "hstore_attrs", "hstore_env", "bool_post")
    };

    @Override
    public FilterableJob create(final Project project, final JobSpecT spec, final boolean isPostJob) {

        final UUID jobId = UUID.randomUUID();
        final String name = createJobName(spec, isPostJob);

        jdbc.update(new PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(final Connection conn) throws SQLException {
                final PreparedStatement ret = conn.prepareStatement(INSERT[0]);


                boolean paused = spec.isPaused();
                if (isPostJob) {
                    paused = false;
                }

                ret.setObject(1, jobId);
                ret.setObject(2, project.getProjectId());
                ret.setString(3, name);
                ret.setString(4, name);
                ret.setString(5, spec.username);
                ret.setInt(6, spec.getUid());
                ret.setInt(7, JobState.INITIALIZE.ordinal());
                ret.setBoolean(8, paused);
                ret.setString(9, spec.logPath);
                ret.setObject(10, spec.attrs);
                ret.setObject(11, spec.env);
                ret.setBoolean(12, isPostJob);
                return ret;
            }
        });

        jdbc.update("INSERT INTO plow.job_count (pk_job) VALUES (?)", jobId);
        jdbc.update("INSERT INTO plow.job_dsp (pk_job) VALUES (?)", jobId);
        jdbc.update("INSERT INTO plow.job_stat (pk_job) VALUES (?)", jobId);

        // Serialize the spec into json.  Don't let a failure here stop
        // the job from launching.  This keeps the job spec around mainly
        // for troubleshooting.
        try {
            final TSerializer serializer = new TSerializer(new TJSONProtocol.Factory());
            final String json = serializer.toString(spec);

            jdbc.update("UPDATE plow.job_history SET str_thrift_spec=? WHERE pk_job=?",
                    json, jobId);
        } catch (Exception e) {
            logger.warn("Failed to serialize thrift job spec to json: " + e, e);
        }


        final FilterableJob job = new FilterableJob();
        job.setJobId(jobId);
        job.setProjectId(project.getProjectId());
        job.setFolderId(null); // Don't know folder yet
        job.setName(name);
        job.username = spec.username;
        job.attrs = spec.attrs;
        return job;
    }

    private String createJobName(final JobSpecT spec, final boolean isPostJob) {
        if (isPostJob) {
            return String.format("%s__post_%d", spec.getName(), System.currentTimeMillis());
        }
        else {
            return spec.getName();
        }
    }

    @Override
    public void tiePostJob(JobId parentJob, JobId postJob) {
        jdbc.update("INSERT INTO plow.job_post (pk_job_first, pk_job_second) VALUES (?, ?)",
                parentJob.getJobId(), postJob.getJobId());
    }

    private static final String UPDATE_ATTRS =
        "UPDATE " +
            "plow.job " +
        "SET " +
            "hstore_attrs = ? " +
        "WHERE " +
            "pk_job=?";

    @Override
    public void setAttrs(final Job job, final Map<String,String> attrs) {
        jdbc.update(new PreparedStatementCreator() {
             @Override
             public PreparedStatement createPreparedStatement(final Connection conn) throws SQLException {
                 final PreparedStatement ret = conn.prepareStatement(UPDATE_ATTRS);
                 ret.setObject(1, attrs);
                 ret.setObject(2, job.getJobId());
                 return ret;
             }
         });
    }

    @Override
    public Map<String,String> getAttrs(final Job job) {
        return jdbc.queryForObject(
                "SELECT hstore_attrs FROM plow.job WHERE job.pk_job=?",
                new RowMapper<Map<String,String>>() {

                   @Override
                   public Map<String, String> mapRow(ResultSet rs, int rowNum)
                           throws SQLException {
                       @SuppressWarnings("unchecked")
                       Map<String,String> result = (Map<String, String>) rs.getObject(1);
                       return result;
                   }

        }, job.getJobId());
    }

    @Override
    public void updateFolder(Job job, Folder folder) {
        jdbc.update("UPDATE plow.job SET pk_folder=? WHERE pk_job=?",
                folder.getFolderId(), job.getJobId());
    }

    @Override
    public boolean setJobState(Job job, JobState state) {
        return jdbc.update("UPDATE plow.job SET int_state=? WHERE pk_job=?",
                state.ordinal(), job.getJobId()) == 1;
    }

    @Override
    public boolean shutdown(Job job) {
        return jdbc.update("UPDATE plow.job SET int_state=?, " +
                    "str_active_name=NULL, time_stopped=plow.txTimeMillis() WHERE pk_job=? AND int_state=?",
                JobState.FINISHED.ordinal(), job.getJobId(), JobState.RUNNING.ordinal()) == 1;
    }

    @Override
    public boolean flipPostJob(Job job) {
        return jdbc.update("UPDATE plow.job SET int_state=? WHERE pk_job=(SELECT pk_job_second FROM plow.job_post WHERE pk_job_first=?)",
                        JobState.RUNNING.ordinal(), job.getJobId()) == 1;
    }

    @Override
    public void updateFrameStatesForLaunch(Job job) {
        jdbc.update("UPDATE plow.task SET int_state=? WHERE pk_layer " +
                "IN (SELECT pk_layer FROM plow.layer WHERE pk_job=?)",
                TaskState.WAITING.ordinal(), job.getJobId());
    }

    private static final String GET_FRAME_STATUS_COUNTS =
            "SELECT " +
                "COUNT(1) AS c, " +
                "task.int_state, " +
                "task.pk_layer  " +
            "FROM " +
                "plow.task," +
                "plow.layer " +
            "WHERE " +
                "task.pk_layer = layer.pk_layer " +
            "AND "+
                "layer.pk_job=? " +
            "GROUP BY " +
                "task.int_state,"+
                "task.pk_layer";

    @Override
    public void updateFrameCountsForLaunch(Job job) {

        Map<Integer, Integer> jobRollup = Maps.newHashMap();
        Map<String, List<Integer>> layerRollup = Maps.newHashMap();

        List<Map<String, Object>> taskCounts = jdbc.queryForList(
                GET_FRAME_STATUS_COUNTS, job.getJobId());

        if (taskCounts.isEmpty()) {
            throw new JobSpecException("The job contains no tasks.");
        }

        for (Map<String, Object> entry: taskCounts) {

            String layerId = entry.get("pk_layer").toString();
            int state = (Integer) entry.get("int_state");
            int count = ((Long)entry.get("c")).intValue();

            // Rollup counts for job.
            Integer stateCount = jobRollup.get(state);
            if (stateCount == null) {
                jobRollup.put(state, count);
            }
            else {
                jobRollup.put(state, count + stateCount);
            }

            // Rollup stats for layers.
            List<Integer> layerCounts = layerRollup.get(layerId);
            if (layerCounts == null) {
                layerRollup.put(layerId, Lists.newArrayList(state, count));
            }
            else {
                layerRollup.get(layerId).add(state);
                layerRollup.get(layerId).add(count);
            }
        }

        final StringBuilder sb = new StringBuilder(512);
        final List<Object> values = Lists.newArrayList();

        // Apply layer counts
        for (Map.Entry<String, List<Integer>> entry: layerRollup.entrySet()) {
            List<Integer> d = entry.getValue();
            values.clear();
            int total = 0;

            sb.setLength(0);
            sb.append("UPDATE plow.layer_count SET");
            for (int i=0; i < entry.getValue().size(); i=i+2) {
                sb.append(" int_");
                sb.append(TaskState.findByValue(d.get(i)).toString().toLowerCase());
                sb.append("=?,");
                values.add(d.get(i+1));
                total=total + d.get(i+1);
            }
            sb.deleteCharAt(sb.length() - 1);
            sb.append(" WHERE pk_layer=?");
            values.add(UUID.fromString(entry.getKey()));
            jdbc.update(sb.toString(), values.toArray());
            jdbc.update("UPDATE plow.layer_count SET int_total=? WHERE pk_layer=?",
                    total, UUID.fromString(entry.getKey()));
        }

        int total = 0;
        values.clear();
        sb.setLength(0);
        sb.append("UPDATE plow.job_count SET ");
        for (Map.Entry<Integer,Integer> entry: jobRollup.entrySet()) {
            sb.append("int_");
            sb.append(TaskState.findByValue(entry.getKey()).toString().toLowerCase());
            sb.append("=?,");
            values.add(entry.getValue());
            total=total + entry.getValue();
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append(" WHERE pk_job=?");
        values.add(job.getJobId());
        jdbc.update(sb.toString(), values.toArray());
        jdbc.update("UPDATE plow.job_count SET int_total=? WHERE pk_job=?",
                total, job.getJobId());

    }

    @Override
    public boolean isPaused(JobId job) {
        return jdbc.queryForObject("SELECT bool_paused FROM plow.job WHERE pk_job=?",
                Boolean.class, job.getJobId());
    }

    @Override
    public boolean hasWaitingFrames(Job job) {
        return jdbc.queryForObject(
                "SELECT job_count.int_waiting FROM plow.job_count WHERE pk_job=?",
                Integer.class, job.getJobId()) > 0;
    }

    private static final String HAS_PENDING_FRAMES =
            "SELECT " +
                "job_count.int_total - (job_count.int_eaten + job_count.int_succeeded) AS pending, " +
                "job.int_state " +
            "FROM " +
                "plow.job " +
                    "INNER JOIN " +
                        "plow.job_count " +
                    "ON " +
                        "job.pk_job = job_count.pk_job " +
            "WHERE " +
                "job.pk_job=?";
    @Override
    public boolean isFinished(JobId job) {
        SqlRowSet row =  jdbc.queryForRowSet(HAS_PENDING_FRAMES, job.getJobId());
        if (!row.first()) {
            return true;
        }
        if (row.getInt("int_state") == JobState.FINISHED.ordinal()) {
            return true;
        }
        if (row.getInt("pending") == 0) {
            return true;
        }
        return false;
    }

    @Override
    public void setMaxCores(Job job, int value) {
        jdbc.update("UPDATE plow.job_dsp SET int_cores_max=? WHERE pk_job=?",
                value, job.getJobId());
    }

    @Override
    public void setMinCores(Job job, int value) {
        jdbc.update("UPDATE plow.job_dsp SET int_cores_min=? WHERE pk_job=?",
                value, job.getJobId());
    }
}
