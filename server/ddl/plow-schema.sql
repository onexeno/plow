/**
 *
 * Table suffixes.
 * No suffix - state/configuration data.
 * _ping - data that is updated via RnDaemon pings
 * _dsp - data that is updated via dispatch
 * _count - counts maintained by triggers
 *
 */
CREATE SCHEMA plow;
CREATE LANGUAGE plpgsql;
CREATE EXTENSION hstore;

---
--- Return the current clock time in millis.
---
CREATE OR REPLACE FUNCTION plow.currentTimeMillis() RETURNS BIGINT AS $$
BEGIN
    return (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::bigint;
END;
$$ LANGUAGE plpgsql;


---
--- Returns the current time in millis, doesn't change during a transaction.
---
CREATE OR REPLACE FUNCTION plow.txTimeMillis() RETURNS BIGINT AS $$
BEGIN
    return (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint;
END;
$$ LANGUAGE plpgsql;

---
--- Cronds
---

CREATE TABLE plow.crond (
  pk_crond SERIAL NOT NULL,
  str_name TEXT NOT NULL,
  str_lock_node TEXT,
  b_locked BOOLEAN NOT NULL DEFAULT 'f',
  duration_timeout BIGINT NOT NULL DEFAULT 120000,
  time_started BIGINT NOT NULL DEFAULT plow.txTimeMillis(),
  time_stopped BIGINT NOT NULL DEFAULT plow.txTimeMillis()
) WITHOUT OIDS;


----------------------------------------------------------

---
--- Project
---

CREATE TABLE plow.project (
  pk_project UUID NOT NULL PRIMARY KEY,
  pk_folder_default UUID,
  str_code VARCHAR(16) NOT NULL,
  str_title VARCHAR(255) NOT NULL,
  bool_active BOOLEAN NOT NULL DEFAULT 't'
) WITHOUT OIDS;

CREATE UNIQUE INDEX project_str_code_idx ON plow.project (str_code);

----------------------------------------------------------

---
--- Folders
---

CREATE TABLE plow.folder (
  pk_folder UUID NOT NULL PRIMARY KEY,
  pk_project UUID NOT NULL,
  str_name VARCHAR(128) NOT NULL,
  int_order SMALLINT NOT NULL,
  time_created TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
) WITHOUT OIDS;


CREATE UNIQUE INDEX folder_name_pk_project_uniq_idx ON plow.folder (str_name, pk_project);
CREATE INDEX folder_pk_project_idx ON plow.folder (pk_project);


---

CREATE TABLE plow.folder_dsp (
  pk_folder UUID NOT NULL PRIMARY KEY,
  int_max_cores INTEGER NOT NULL DEFAULT -1,
  int_min_cores INTEGER NOT NULL DEFAULT 0,
  int_run_cores INTEGER NOT NULL DEFAULT 0,
  float_tier REAL NOT NULL DEFAULT 0.0
) WITHOUT OIDS;

CREATE INDEX folder_dsp_float_tier_idx ON plow.folder_dsp (float_tier);

----------------------------------------------------------

---
--- Jobs
---

CREATE TABLE plow.job (
  pk_job UUID NOT NULL PRIMARY KEY,
  pk_project UUID NOT NULL REFERENCES plow.project(pk_project),
  pk_folder UUID REFERENCES plow.folder(pk_folder),
  str_name VARCHAR(160) NOT NULL,
  str_active_name VARCHAR(160),
  str_username VARCHAR(100) NOT NULL,
  str_log_path TEXT,
  int_uid INTEGER NOT NULL,
  int_state SMALLINT NOT NULL DEFAULT 0,
  bool_paused BOOLEAN NOT NULL DEFAULT 'f',
  time_started BIGINT NOT NULL DEFAULT plow.txTimeMillis(),
  time_stopped BIGINT DEFAULT 0,
  hstore_attrs hstore,
  hstore_env hstore
) WITHOUT OIDS;

CREATE UNIQUE INDEX job_str_active_name_uniq_idx ON plow.job (str_active_name);
CREATE INDEX job_int_state_bool_paused_pk_project_idx ON plow.job (int_state, bool_paused, pk_project);
CREATE INDEX job_pk_project_idx ON plow.job (pk_project);

---

CREATE TABLE plow.job_dsp (
  pk_job UUID NOT NULL PRIMARY KEY,
  int_max_cores INTEGER NOT NULL DEFAULT -1,
  int_min_cores INTEGER NOT NULL DEFAULT 0,
  int_run_cores INTEGER NOT NULL DEFAULT 0,
  float_tier REAL NOT NULL DEFAULT 0.0
);

CREATE INDEX job_dsp_float_tier_idx ON plow.job_dsp (float_tier);

---

CREATE TABLE plow.job_count (
  pk_job UUID NOT NULL PRIMARY KEY,
  int_total INTEGER NOT NULL DEFAULT 0,
  int_succeeded INTEGER NOT NULL DEFAULT 0,
  int_running INTEGER NOT NULL DEFAULT 0,
  int_dead INTEGER NOT NULL DEFAULT 0,
  int_eaten INTEGER NOT NULL DEFAULT 0,
  int_waiting INTEGER NOT NULL DEFAULT 0,
  int_depend INTEGER NOT NULL DEFAULT 0
) WITHOUT OIDS;

CREATE INDEX job_count_int_waiting_idx ON plow.job_count (int_waiting);

---

CREATE TABLE plow.job_ping (
  pk_job UUID NOT NULL PRIMARY KEY,
  int_max_rss INTEGER NOT NULL DEFAULT 0
);

----------------------------------------------------------

---
--- Layers
---

CREATE table plow.layer (
  pk_layer UUID NOT NULL PRIMARY KEY,
  pk_job UUID NOT NULL,
  str_name TEXT NOT NULL,
  str_range TEXT,
  str_command TEXT[] NOT NULL,
  str_tags TEXT[] NOT NULL,
  int_chunk_size INTEGER NOT NULL,
  int_order INTEGER NOT NULL,
  int_min_cores SMALLINT NOT NULL,
  int_max_cores SMALLINT NOT NULL,
  int_min_ram INTEGER NOT NULL,
  bool_threadable BOOLEAN DEFAULT 'f' NOT NULL,
  hstore_env hstore
) WITHOUT OIDS;

CREATE INDEX layer_pk_job_idx ON plow.layer (pk_job);
CREATE INDEX layer_str_tags_gin_idx ON plow.layer USING gin(str_tags);

CREATE UNIQUE INDEX layer_str_name_pk_job_uniq_idx ON plow.layer (str_name, pk_job);

---

CREATE TABLE plow.layer_count (
  pk_layer UUID NOT NULL PRIMARY KEY,
  int_total INTEGER NOT NULL DEFAULT 0,
  int_succeeded INTEGER NOT NULL DEFAULT 0,
  int_running INTEGER NOT NULL DEFAULT 0,
  int_dead INTEGER NOT NULL DEFAULT 0,
  int_eaten INTEGER NOT NULL DEFAULT 0,
  int_waiting INTEGER NOT NULL DEFAULT 0,
  int_depend INTEGER NOT NULL DEFAULT 0
) WITHOUT OIDS;

CREATE INDEX layer_count_int_waiting_idx ON plow.layer_count (int_waiting);

---

CREATE TABLE plow.layer_dsp (
  pk_layer UUID NOT NULL PRIMARY KEY,
  int_run_cores INTEGER NOT NULL DEFAULT 0
);


CREATE TABLE plow.layer_ping (
  pk_layer UUID NOT NULL PRIMARY KEY,
  int_max_rss INTEGER NOT NULL DEFAULT 0,
  int_max_cpu_perc SMALLINT NOT NULL DEFAULT 0
);

---

CREATE TABLE plow.output (
    pk_output UUID NOT NULL PRIMARY KEY,
    pk_layer UUID NOT NULL,
    pk_job UUID NOT NULL,
    str_path TEXT NOT NULL,
    attrs hstore
);

CREATE INDEX output_pk_layer_idx ON plow.output (pk_layer);
CREATE INDEX output_pk_job_idx ON plow.output (pk_job);

----------------------------------------------------------

---
--- Tasks
---

CREATE TABLE plow.task (
  pk_task UUID NOT NULL PRIMARY KEY,
  pk_layer UUID NOT NULL,
  pk_job UUID NOT NULL,
  str_name VARCHAR(255),
  int_number INTEGER NOT NULL,
  int_state SMALLINT NOT NULL,
  int_depend_count INTEGER NOT NULL DEFAULT 0,
  int_task_order INTEGER NOT NULL,
  int_layer_order INTEGER NOT NULL,
  bool_reserved BOOLEAN DEFAULT 'f' NOT NULL,
  time_started BIGINT DEFAULT 0 NOT NULL,
  time_stopped BIGINT DEFAULT 0 NOT NULL,
  time_updated BIGINT DEFAULT 0 NOT NULL,
  int_retry SMALLINT DEFAULT -1 NOT NULL,
  int_cores SMALLINT DEFAULT 0 NOT NULL,
  int_ram INTEGER DEFAULT 0 NOT NULL
) WITHOUT OIDS;

CREATE INDEX task_pk_layer_idx ON plow.task (pk_layer);
CREATE INDEX task_pk_job_idx ON plow.task (pk_job);
CREATE INDEX task_int_state_idx ON plow.task (int_state);
CREATE INDEX task_time_updated_idx ON plow.task (time_updated);
CREATE UNIQUE INDEX task_str_name_pk_job_idx_uniq ON plow.task (str_name, pk_job);

CREATE INDEX task_order_idx ON plow.task(int_task_order, int_layer_order);

----------------------------------------------------------


---
--- Stores the ping data for a task.
---
CREATE TABLE plow.task_ping (
  pk_task UUID NOT NULL PRIMARY KEY,
  int_rss INTEGER DEFAULT 0 NOT NULL,
  int_max_rss INTEGER DEFAULT 0 NOT NULL,
  int_cpu_perc SMALLINT NOT NULL DEFAULT 0,
  int_max_cpu_perc SMALLINT NOT NULL DEFAULT 0,
  int_progress SMALLINT DEFAULT 0 NOT NULL,
  str_last_log_line TEXT,
  str_last_node_name TEXT
) WITHOUT OIDS;

---
--- Dependencies
---

CREATE TABLE plow.depend (
    pk_depend UUID NOT NULL PRIMARY KEY,
    uuid_sig UUID,
    int_type SMALLINT NOT NULL,
    bool_active BOOLEAN NOT NULL DEFAULT 't',
    pk_dependent_job UUID NOT NULL,
    pk_dependon_job UUID NOT NULL,
    pk_dependent_layer UUID,
    pk_dependon_layer UUID,
    pk_dependent_task UUID,
    pk_dependon_task UUID,
    str_dependent_job_name TEXT NOT NULL,
    str_dependon_job_name TEXT NOT NULL,
    str_dependent_layer_name TEXT,
    str_dependon_layer_name TEXT,
    str_dependent_task_name TEXT,
    str_dependon_task_name TEXT,
    time_created BIGINT NOT NULL DEFAULT plow.txTimeMillis(),
    time_modified BIGINT NOT NULL DEFAULT plow.txTimeMillis()
);

CREATE UNIQUE INDEX depend_uuid_sig_idx ON plow.depend (uuid_sig);

CREATE INDEX depend_dependent_job_idx ON plow.depend (pk_dependent_job);
CREATE INDEX depend_dependon_job_idx ON plow.depend (pk_dependon_job);
CREATE INDEX depend_dependent_layer_idx ON plow.depend (pk_dependent_layer);
CREATE INDEX depend_dependon_layer_idx ON plow.depend (pk_dependon_layer);
CREATE INDEX depend_dependent_task_idx ON plow.depend (pk_dependent_task);
CREATE INDEX depend_dependon_task_idx ON plow.depend (pk_dependon_task);

----------------------------------------------------------

---
--- Cluster
---

CREATE TABLE plow.cluster (
  pk_cluster UUID NOT NULL PRIMARY KEY,
  str_name VARCHAR(128) NOT NULL,
  str_tags TEXT[],
  bool_locked BOOLEAN DEFAULT 'f' NOT NULL,
  bool_default BOOLEAN DEFAULT 'f' NOT NULL
) WITHOUT OIDS;

CREATE UNIQUE INDEX cluster_str_name_uniq_idx ON plow.cluster (str_name);
CREATE INDEX cluster_str_tag_gin_idx on plow.cluster USING GIN (str_tags);

----------------------------------------------------------

---
--- Node
---

CREATE TABLE plow.node (
  pk_node UUID NOT NULL PRIMARY KEY,
  pk_cluster UUID NOT NULL REFERENCES plow.cluster(pk_cluster),
  str_name VARCHAR(128) NOT NULL,
  str_ipaddr VARCHAR(15) NOT NULL,
  int_state SMALLINT NOT NULL DEFAULT 0,
  bool_locked BOOLEAN NOT NULL DEFAULT 'f',
  time_created BIGINT NOT NULL DEFAULT plow.txTimeMillis(),
  time_updated BIGINT NOT NULL DEFAULT plow.txTimeMillis(),
  str_tags TEXT[]
) WITHOUT OIDS;

CREATE UNIQUE INDEX node_str_name_uniq_idx ON plow.node (str_name);
CREATE INDEX node_int_state_idx ON plow.node (int_state);

---

CREATE TABLE plow.node_sys (
  pk_node UUID NOT NULL PRIMARY KEY,
  int_phys_cores SMALLINT NOT NULL,
  int_log_cores SMALLINT NOT NULL,
  int_ram INTEGER NOT NULL,
  int_free_ram INTEGER NOT NULL,
  int_swap INTEGER NOT NULL,
  int_free_swap INTEGER NOT NULL,
  time_booted BIGINT NOT NULL,
  str_cpu_model TEXT NOT NULL,
  str_platform TEXT NOT NULL
) WITHOUT OIDS;

---

CREATE TABLE plow.node_dsp (
  pk_node UUID NOT NULL PRIMARY KEY,
  int_cores SMALLINT NOT NULL,
  int_ram INTEGER NOT NULL,
  int_idle_cores SMALLINT NOT NULL CHECK (int_idle_cores >= 0),
  int_free_ram INTEGER NOT NULL CHECK (int_free_ram >= 0)
) WITHOUT OIDS;

---

CREATE TABLE plow.quota (
  pk_quota UUID NOT NULL PRIMARY KEY,
  pk_cluster UUID NOT NULL,
  pk_project UUID NOT NULL,
  int_size INTEGER NOT NULL,
  int_burst INTEGER NOT NULL,
  int_run_cores INTEGER DEFAULT 0 NOT NULL CHECK (int_run_cores <= int_burst),
  bool_locked BOOLEAN DEFAULT 'f' NOT NULL
) WITHOUT OIDS;

CREATE UNIQUE INDEX quota_project_cluster_uniq_idx ON plow.quota (pk_project, pk_cluster);
CREATE INDEX quota_cluster_idx ON plow.quota (pk_cluster);


----------------------------------------------------------

---
--- Proc
---

CREATE TABLE plow.proc (
  pk_proc UUID NOT NULL PRIMARY KEY,
  pk_node UUID NOT NULL,
  pk_cluster UUID NOT NULL,
  pk_quota UUID NOT NULL,
  pk_job UUID NOT NULL,
  pk_task UUID,
  int_cores SMALLINT NOT NULL,
  int_ram INTEGER NOT NULL,
  bool_unbooked BOOLEAN DEFAULT 'f' NOT NULL,
  bool_backfill BOOLEAN DEFAULT 'f' NOT NULL,
  time_created BIGINT NOT NULL DEFAULT plow.txTimeMillis(),
  time_updated BIGINT NOT NULL DEFAULT plow.txTimeMillis()
) WITHOUT OIDS;

CREATE INDEX proc_pk_node_idx ON plow.proc (pk_node);
CREATE UNIQUE INDEX proc_pk_task_uniq_idx ON plow.proc (pk_task);
CREATE INDEX proc_pk_job_idx ON plow.proc (pk_job);

----------------------------------------------------------

---
--- Filter
---

CREATE TABLE plow.filter (
  pk_filter UUID NOT NULL PRIMARY KEY,
  pk_project UUID NOT NULL REFERENCES plow.project(pk_project),
  str_name VARCHAR(128) NOT NULL,
  int_order REAL NOT NULL DEFAULT -1,
  bool_enabled BOOLEAN DEFAULT 't' NOT NULL
) WITHOUT OIDS;

CREATE INDEX filter_pk_project_idx ON plow.filter (pk_project);
CREATE INDEX filter_int_order_idx ON plow.filter(int_order);

----------------------------------------------------------

---
--- Match
---

CREATE TABLE plow.matcher (
  pk_matcher UUID NOT NULL PRIMARY KEY,
  pk_filter UUID NOT NULL REFERENCES plow.filter(pk_filter),
  int_field SMALLINT NOT NULL,
  int_type SMALLINT NOT NULL,
  int_order BIGINT NOT NULL DEFAULT plow.txTimeMillis(),
  str_value TEXT,
  str_attr TEXT
) WITHOUT OIDS;

CREATE INDEX matcher_pk_filter_idx ON plow.matcher (pk_filter);
CREATE INDEX matcher_int_order_idx ON plow.matcher (int_order);

---
--- Action
---

CREATE TABLE plow.action (
  pk_action UUID NOT NULL PRIMARY KEY,
  pk_filter UUID NOT NULL REFERENCES plow.filter(pk_filter),
  int_type SMALLINT NOT NULL,
  int_order BIGINT NOT NULL DEFAULT plow.txTimeMillis(),
  str_value TEXT
) WITHOUT OIDS;

CREATE INDEX action_pk_filter_idx ON plow.action (pk_filter);

----------------------------------------------------------

CREATE OR REPLACE FUNCTION plow.after_proc_insert() RETURNS TRIGGER AS $$
BEGIN
  UPDATE plow.quota SET int_run_cores = int_run_cores + NEW.int_cores WHERE pk_quota=NEW.pk_quota;
  UPDATE plow.node_dsp SET int_idle_cores = int_idle_cores - NEW.int_cores WHERE pk_node=NEW.pk_node;
  UPDATE plow.folder_dsp SET int_run_cores = int_run_cores + NEW.int_cores WHERE pk_folder=
    (SELECT pk_folder FROM job WHERE pk_job=NEW.pk_job);
  UPDATE plow.job_dsp SET int_run_cores = int_run_cores + NEW.int_cores WHERE pk_job=NEW.pk_job;
  UPDATE plow.layer_dsp SET int_run_cores = int_run_cores + NEW.int_cores WHERE pk_layer=
    (SELECT pk_layer FROM task WHERE pk_task=NEW.pk_task);
  RETURN NEW;
END
$$
LANGUAGE plpgsql;

CREATE TRIGGER trig_after_proc_insert AFTER INSERT ON plow.proc
    FOR EACH ROW EXECUTE PROCEDURE plow.after_proc_insert();

CREATE OR REPLACE FUNCTION plow.after_proc_delete() RETURNS TRIGGER AS $$
BEGIN
  UPDATE plow.quota SET int_run_cores = int_run_cores - OLD.int_cores WHERE pk_quota=OLD.pk_quota;
  UPDATE plow.node_dsp SET int_idle_cores = int_idle_cores + OLD.int_cores WHERE pk_node=OLD.pk_node;
  UPDATE plow.folder_dsp SET int_run_cores = int_run_cores - OLD.int_cores WHERE pk_folder=
    (SELECT pk_folder FROM job WHERE pk_job=OLD.pk_job);
  UPDATE plow.job_dsp SET int_run_cores = int_run_cores - OLD.int_cores WHERE pk_job=OLD.pk_job;
  UPDATE plow.layer_dsp SET int_run_cores = int_run_cores - OLD.int_cores WHERE pk_layer=
    (SELECT pk_layer FROM task WHERE pk_task=OLD.pk_task);
  RETURN OLD;
END
$$
LANGUAGE plpgsql;

CREATE TRIGGER trig_after_proc_delete AFTER DELETE ON plow.proc
    FOR EACH ROW EXECUTE PROCEDURE plow.after_proc_delete();

---
--- plow.before_disp_update()
---
--- Handle setting the tier value on job_dsp and folder_dsp;
---
CREATE OR REPLACE FUNCTION plow.before_disp_update() RETURNS TRIGGER AS $$
BEGIN
	IF NEW.int_min_cores = 0 THEN
		NEW.float_tier := 0;
	ELSE
		NEW.float_tier := NEW.int_run_cores / NEW.int_min_cores::real;
	END IF;
	RETURN NEW;
END
$$
LANGUAGE plpgsql;

CREATE TRIGGER trig_before_job_disp_update BEFORE UPDATE ON plow.job_dsp
    FOR EACH ROW EXECUTE PROCEDURE plow.before_disp_update();

CREATE TRIGGER trig_before_folder_disp_update BEFORE UPDATE ON plow.folder_dsp
    FOR EACH ROW EXECUTE PROCEDURE plow.before_disp_update();

---
--- plow.after_task_state_change()
---
--- Handle incrementing/decrementating the frame state counters.
---
CREATE OR REPLACE FUNCTION plow.after_task_state_change() RETURNS TRIGGER AS $$
DECLARE
    old_state_col VARCHAR;
    new_state_col VARCHAR;
    states VARCHAR[];
BEGIN

  states := ARRAY['INITIALIZE', 'WAITING', 'RUNNING', 'DEAD', 'EATEN' , 'DEPEND', 'SUCCEEDED'];
  old_state_col := 'int_' || lower(states[old.int_state + 1]);
  new_state_col := 'int_' || lower(states[new.int_state + 1]);

  EXECUTE 'UPDATE plow.layer_count SET ' || old_state_col || '=' || old_state_col || ' -1, '
            || new_state_col || '=' || new_state_col || '+1 WHERE pk_layer=$1' USING new.pk_layer;

  EXECUTE 'UPDATE plow.job_count SET ' || old_state_col || '=' || old_state_col || ' -1, '
            || new_state_col || '=' || new_state_col || '+1 WHERE pk_job=$1' USING new.pk_job;

  RETURN NEW;

END
$$
LANGUAGE plpgsql;

CREATE TRIGGER trig_after_task_state_change AFTER UPDATE ON plow.task
    FOR EACH ROW WHEN (OLD.int_state != 0 AND OLD.int_state != NEW.int_state)
    EXECUTE PROCEDURE plow.after_task_state_change();

---
--- plow.before_task_depend_check()
---
--- Before task dependency check. Runs if the task has a depend count
--- greater than zero and flips the state to depend.
---
CREATE OR REPLACE FUNCTION plow.before_update_set_depend() RETURNS TRIGGER AS $$
BEGIN
  NEW.int_state := 5;
  NEW.time_updated := txTimeMillis();
  RETURN NEW;
END
$$
LANGUAGE plpgsql;

CREATE TRIGGER trig_before_update_set_depend BEFORE UPDATE ON plow.task
    FOR EACH ROW WHEN (NEW.int_depend_count > 0 AND NEW.int_state=1)
    EXECUTE PROCEDURE plow.before_update_set_depend();

---
--- plow.before_update_task_depend_check()
---
--- Before task dependency check. Runs if the task has a depend count
--- greater than zero and flips the state to depend.
---
CREATE OR REPLACE FUNCTION plow.before_update_set_waiting() RETURNS TRIGGER AS $$
BEGIN
  NEW.int_state := 1;
  NEW.time_updated := txTimeMillis();
  RETURN NEW;
END
$$
LANGUAGE plpgsql;

CREATE TRIGGER trig_before_update_set_waiting BEFORE UPDATE ON plow.task
    FOR EACH ROW WHEN (NEW.int_depend_count=0 AND NEW.int_state=5)
    EXECUTE PROCEDURE plow.before_update_set_waiting();

----------------------------------------------------------

---
--- Cluster Counts
---
CREATE OR REPLACE VIEW
  plow.cluster_totals
AS
  SELECT
    node.pk_cluster,
    SUM(1) AS node_total,
    SUM(bool_locked::integer) AS node_locked_total,
    SUM(CASE int_state WHEN 0 THEN 1 ELSE 0 END) AS node_up_total,
    SUM(CASE int_state WHEN 1 THEN 1 ELSE 0 END) AS node_down_total,
    SUM(CASE int_state WHEN 2 THEN 1 ELSE 0 END) AS node_repair_total,
    SUM(node_dsp.int_cores) AS core_total,
    SUM(node_dsp.int_idle_cores) AS core_idle_total,
    SUM(CASE int_state WHEN 0 THEN node_dsp.int_cores ELSE 0 END) AS core_up_total,
    SUM(CASE int_state WHEN 1 THEN node_dsp.int_cores ELSE 0 END) AS core_down_total,
    SUM(CASE int_state WHEN 2 THEN node_dsp.int_cores ELSE 0 END) AS core_repair_total,
    SUM(bool_locked::integer * node_dsp.int_cores) AS core_locked_total
  FROM
    plow.node
  INNER JOIN
    plow.node_dsp ON node.pk_node = node_dsp.pk_node
  GROUP BY
    node.pk_cluster;




----------------------------------------------------------

---
--- Cronds
---
INSERT INTO plow.crond (str_name) VALUES ('ORPHAN_PROC_CHECK');

---
--- Test Project
---
INSERT INTO plow.project VALUES ('00000000-0000-0000-0000-000000000000', null, 'test', 'The Test Project');
INSERT INTO plow.folder VALUES ('00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000000', 'The Test Project', 0);
INSERT INTO plow.folder_dsp VALUES ('00000000-0000-0000-0000-000000000000', -1, 0, 0);
UPDATE plow.project SET pk_folder_default = '00000000-0000-0000-0000-000000000000' WHERE pk_project='00000000-0000-0000-0000-000000000000';

---
--- Test Cluster
---
INSERT INTO plow.cluster VALUES ('00000000-0000-0000-0000-000000000000', 'unassigned', '{"unassigned"}', 'f', 't');

INSERT INTO plow.quota VALUES ('00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000000','00000000-0000-0000-0000-000000000000', 10, 20, 0, 'f');


