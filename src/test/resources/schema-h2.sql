-- 测试用内存库（H2, MySQL 兼容模式）：与 doc/schema/amss.sql 的 t_amss_out_card 两表同构
CREATE TABLE t_amss_out_card (
  id BIGINT NOT NULL PRIMARY KEY,
  biz_no VARCHAR(64),
  vehicle_plate_no VARCHAR(64),
  stage INT,
  status INT,
  content VARCHAR(255),
  last_action VARCHAR(64),
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time TIMESTAMP,
  update_by VARCHAR(64),
  update_time TIMESTAMP,
  remark VARCHAR(500),
  active_plate VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN status = 1 AND del_flag = 0 THEN vehicle_plate_no ELSE NULL END),
  CONSTRAINT uk_amss_out_card_active UNIQUE (active_plate)
);

CREATE TABLE t_amss_out_card_stage (
  id BIGINT NOT NULL PRIMARY KEY,
  card_id BIGINT NOT NULL,
  stage_no INT NOT NULL,
  rev_no INT NOT NULL,
  current_flag INT NOT NULL DEFAULT 1,
  action_type VARCHAR(16) NOT NULL,
  plate_count INT,
  license_status VARCHAR(255),
  open_case_flag INT,
  return_reason VARCHAR(32),
  stage_data VARCHAR(1000),
  operator VARCHAR(64),
  happen_time TIMESTAMP,
  create_time TIMESTAMP,
  current_stage INT GENERATED ALWAYS AS (CASE WHEN current_flag = 1 AND action_type = 'PASS' THEN stage_no ELSE NULL END),
  CONSTRAINT uk_amss_out_card_stage_current UNIQUE (card_id, current_stage)
);
