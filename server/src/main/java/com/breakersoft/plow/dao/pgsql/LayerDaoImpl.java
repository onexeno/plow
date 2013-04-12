package com.breakersoft.plow.dao.pgsql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.breakersoft.plow.FrameRange;
import com.breakersoft.plow.FrameSet;
import com.breakersoft.plow.Job;
import com.breakersoft.plow.Layer;
import com.breakersoft.plow.LayerE;
import com.breakersoft.plow.dao.AbstractDao;
import com.breakersoft.plow.dao.LayerDao;
import com.breakersoft.plow.thrift.LayerSpecT;
import com.breakersoft.plow.util.JdbcUtils;

@Repository
public class LayerDaoImpl extends AbstractDao implements LayerDao {


    @SuppressWarnings("unused")
    private static final Logger logger =
        org.slf4j.LoggerFactory.getLogger(LayerDaoImpl.class);

    public static final RowMapper<Layer> MAPPER = new RowMapper<Layer>() {

        @Override
        public Layer mapRow(ResultSet rs, int rowNum)
                throws SQLException {
            LayerE layer = new LayerE();
            layer.setLayerId(UUID.fromString(rs.getString(1)));
            layer.setJobId(UUID.fromString(rs.getString(2)));
            layer.setName(rs.getString(3));
            return layer;
        }
    };

    private static final String GET =
            "SELECT " +
                "pk_layer,"+
                "pk_job, " +
                "str_name " +
            "FROM " +
                "plow.layer ";

    @Override
    public Layer get(Job job, String name) {
        try {
            return get(UUID.fromString(name));
        } catch (IllegalArgumentException e) {
            return jdbc.queryForObject(
                    GET + "WHERE pk_job=? AND str_name=?",
                    MAPPER, job.getJobId(), name);
        }
    }

    @Override
    public Layer get(Job job, int idx) {
        return jdbc.queryForObject(
                    GET + "WHERE pk_job=? AND int_order=? LIMIT 1",
                    MAPPER, job.getJobId(), idx);
    }

    @Override
    public Layer get(UUID id) {
        return jdbc.queryForObject(
                GET + "WHERE pk_layer=?",
                MAPPER, id);
    }

    private static final String INSERT =
        JdbcUtils.Insert("plow.layer",
                "pk_layer", "pk_job", "str_name", "str_range",
                "str_command", "str_tags", "int_chunk_size", "int_order",
                "int_min_cores", "int_max_cores", "int_min_ram",
                "bool_threadable");

    @Override
    public Layer create(final Job job, final LayerSpecT layer, final int order) {

        final UUID id = UUID.randomUUID();

        jdbc.update(new PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(final Connection conn) throws SQLException {
                final PreparedStatement ret = conn.prepareStatement(INSERT);
                ret.setObject(1, id);
                ret.setObject(2, job.getJobId());
                ret.setString(3, layer.getName());
                ret.setString(4, layer.getRange());
                ret.setArray(5, conn.createArrayOf("text", layer.getCommand().toArray()));
                ret.setArray(6, conn.createArrayOf("text", layer.getTags().toArray()));
                ret.setInt(7, layer.getChunk());
                ret.setInt(8, order);
                ret.setInt(9, layer.getMinCores());
                ret.setInt(10, layer.getMaxCores());
                ret.setInt(11, layer.getMinRamMb());
                ret.setBoolean(12, layer.isThreadable());
                return ret;
            }
        });

        jdbc.update("INSERT INTO plow.layer_count (pk_layer) VALUES (?)", id);
        jdbc.update("INSERT INTO plow.layer_dsp (pk_layer) VALUES (?)", id);
        jdbc.update("INSERT INTO plow.layer_ping (pk_layer) VALUES (?)", id);

        final LayerE result = new LayerE();
        result.setLayerId(id);
        result.setJobId(job.getJobId());
        return result;
    }

    @Override
    public boolean isFinished(Layer layer) {
        return jdbc.queryForInt(
                "SELECT int_total - (int_succeeded + int_eaten) AS t " +
                "FROM layer_count WHERE pk_layer=?", layer.getLayerId()) == 0;
    }

    private static final RowMapper<FrameRange> RANGE_MAPPER = new RowMapper<FrameRange>() {
        @Override
        public FrameRange mapRow(ResultSet rs, int rowNum)
                throws SQLException {
            FrameRange range = new FrameRange();
            range.frameSet = new FrameSet(rs.getString("str_range"));
            range.chunkSize = rs.getInt("int_chunk_size");
            range.numFrames = range.frameSet.size() / range.chunkSize;
            return range;
        }
    };

    @Override
    public boolean updateMaxRssMb(UUID layerId, int value) {
        return jdbc.update("UPDATE plow.layer_ping SET int_max_rss=? " +
                "WHERE pk_layer=? AND int_max_rss < ?",
                value, layerId, value) == 1;
    }

    @Override
    public boolean updateMaxCpuPerc(UUID layerId, int value) {
        return jdbc.update("UPDATE plow.layer_ping SET int_max_cpu_perc=? " +
                "WHERE pk_layer=? AND int_max_cpu_perc < ?",
                value, layerId, value) == 1;
    }

    @Override
    public FrameRange getFrameRange(Layer layer) {
        return jdbc.queryForObject(
                "SELECT str_range, int_chunk_size FROM plow.layer WHERE pk_layer=?",
                RANGE_MAPPER, layer.getLayerId());
    }

    private static final String INSERT_OUTPUT =
            JdbcUtils.Insert("plow.output",
                    "pk_output", "pk_layer", "pk_job",
                    "str_path", "attrs");

    @Override
    public void addOutput(final Layer layer, final String path, final Map<String,String> attrs) {
        jdbc.update(new PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(final Connection conn) throws SQLException {
                final PreparedStatement ret = conn.prepareStatement(INSERT_OUTPUT);
                ret.setObject(1, UUID.randomUUID());
                ret.setObject(2, layer.getLayerId());
                ret.setObject(3, layer.getJobId());
                ret.setString(4, path);
                ret.setObject(5, attrs);
                return ret;
            }
        });
    }

    @Override
    public void setMinCores(Layer layer, int cores) {
        jdbc.update("UPDATE plow.layer SET int_min_cores=? WHERE pk_layer=?",
                cores, layer.getLayerId());
    }

    @Override
    public void setMaxCores(Layer layer, int cores) {
        jdbc.update("UPDATE plow.layer SET int_max_cores=? WHERE pk_layer=?",
                cores, layer.getLayerId());
    }

    @Override
    public void setMinRam(Layer layer, int memory) {
        jdbc.update("UPDATE plow.layer SET int_min_ram=? WHERE pk_layer=?",
                memory, layer.getLayerId());
    }

    @Override
    public void setThreadable(Layer layer, boolean threadable) {
        jdbc.update("UPDATE plow.layer SET bool_threadable=? WHERE pk_layer=?",
                threadable, layer.getLayerId());
    }

    private static final String UPDATE_TAGS =
            "UPDATE plow.layer SET str_tags=? WHERE pk_layer=?";

    @Override
    public void setTags(final Layer layer, final Set<String> tags) {
         jdbc.update(new PreparedStatementCreator() {
             @Override
             public PreparedStatement createPreparedStatement(final Connection conn) throws SQLException {
                 final PreparedStatement ret = conn.prepareStatement(UPDATE_TAGS);
                 ret.setObject(1, JdbcUtils.toArray(conn, tags));
                 ret.setObject(2, layer.getLayerId());
                 return ret;
             }
         });
    }
}
