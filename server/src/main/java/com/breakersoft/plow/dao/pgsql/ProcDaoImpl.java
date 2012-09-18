package com.breakersoft.plow.dao.pgsql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.breakersoft.plow.Task;
import com.breakersoft.plow.Proc;
import com.breakersoft.plow.ProcE;
import com.breakersoft.plow.dao.AbstractDao;
import com.breakersoft.plow.dao.ProcDao;
import com.breakersoft.plow.dispatcher.DispatchProc;
import com.breakersoft.plow.util.JdbcUtils;

@Repository
public class ProcDaoImpl extends AbstractDao implements ProcDao {

    public static final RowMapper<Proc> MAPPER = new RowMapper<Proc>() {
        @Override
        public Proc mapRow(ResultSet rs, int rowNum)
                throws SQLException {
            ProcE proc = new ProcE();
            proc.setProcId((UUID)rs.getObject(1));
            proc.setQuotaId((UUID)rs.getObject(1));
            proc.setNodeId((UUID)rs.getObject(3));
            proc.setFrameId((UUID)rs.getObject(4));
            return proc;
        }
    };

    private static final String GET =
            "SELECT " +
                "pk_proc,"+
                "pk_quota,"+
                "pk_host,"+
                "pk_frame,"+
            "FROM " +
                "plow.proc ";

    private static final String GET_BY_ID = GET +"WHERE pk_proc = ?";
    @Override
    public Proc getProc(UUID procId) {
        return jdbc.queryForObject(GET_BY_ID, MAPPER, procId);
    }

    private static final String GET_BY_FR = GET +"WHERE pk_frame = ?";
    @Override
    public Proc getProc(Task frame) {
        return jdbc.queryForObject(GET_BY_FR, MAPPER, frame.getTaskId());
    }

    private static final String INSERT =
            JdbcUtils.Insert("plow.proc",
                    "pk_proc",
                    "pk_quota",
                    "pk_host",
                    "pk_frame",
                    "int_cores"+
                    "int_mem");

    @Override
    public void create(DispatchProc proc) {
        proc.setProcId(UUID.randomUUID());
        jdbc.update(INSERT,
                proc.getProcId(),
                proc.getQuotaId(),
                proc.getNodeId(),
                proc.getTaskId(),
                proc.getMinCores(),
                proc.getMinMemory());
    }

}