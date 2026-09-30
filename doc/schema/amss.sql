-- amss 农业机械安全监理与跨区机收作业服务管理 -- schema (chen-014)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：chen_014

CREATE TABLE IF NOT EXISTS t_amss_chk_line (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(64) DEFAULT NULL COMMENT '检验结论判据代号',
  rule_name varchar(128) DEFAULT NULL COMMENT '判据名称',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '一档不合格项数封顶值',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '二档不合格项数封顶值',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '三档不合格项数封顶值',
  eff_start datetime DEFAULT NULL COMMENT '启用之日',
  eff_end datetime DEFAULT NULL COMMENT '交棒之日(不含)',
  priority int DEFAULT NULL COMMENT '抢档顺位(数值越大越优先)',
  status int DEFAULT NULL COMMENT '判据的情形 0现行 1已停用',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全技术检验结论判据线';

CREATE TABLE IF NOT EXISTS t_amss_coop_doc (
  id bigint NOT NULL COMMENT '主键',
  site_no varchar(64) DEFAULT NULL COMMENT '农机服务组织底档字号',
  site_name varchar(128) DEFAULT NULL COMMENT '服务组织全称',
  site_type varchar(32) DEFAULT NULL COMMENT '组织类别类',
  road_name varchar(128) DEFAULT NULL COMMENT '管辖监理站(省—市—县)',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '单卡在队面积一档上限(亩)',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '二档上限(亩)',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '三档上限(亩)',
  status int DEFAULT NULL COMMENT '底档情形 0在用 1已收回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='农机服务组织底档';

CREATE TABLE IF NOT EXISTS t_amss_diff_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '通行费免收对账批次号',
  row_no int DEFAULT NULL COMMENT '原清单行次',
  item_code varchar(64) DEFAULT NULL COMMENT '差异项代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本行争议差额(元)',
  status int DEFAULT NULL COMMENT '行进展 0待核 1已核销 2挂账',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通行费免收与作业证发放对账行';

CREATE TABLE IF NOT EXISTS t_amss_duty_task (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '检修复查与上门服务派单事项号',
  due_at datetime DEFAULT NULL COMMENT '本项应当到场那天的最后一刻',
  amount decimal(12,2) DEFAULT NULL COMMENT '单条提前派单的自然日数',
  content varchar(255) DEFAULT NULL COMMENT '派单事由与受派班组记要',
  status int DEFAULT NULL COMMENT '条目情形 0待派 1已派 2派不出去',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='三夏三秋检修复查与上门服务派单条目';

CREATE TABLE IF NOT EXISTS t_amss_out_card (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '变型拖拉机牌证缴回事务卡号',
  veh_no varchar(64) NOT NULL COMMENT '变型拖拉机号牌（一台车一个号）',
  active_veh_no varchar(64) GENERATED ALWAYS AS (if((`status` = 2 or `del_flag` = 1),NULL,`veh_no`)) STORED COMMENT '活卡占位：在办挂车号、了结或删除腾空，配唯一索引保一车一活卡',
  stage int DEFAULT 0 COMMENT '当前缴回段镜像 0..3（收牌/验车/清账/了结），权威段序以履历线推出',
  status int DEFAULT 1 COMMENT '卡的落定 0未起 1在办 2已了结',
  content varchar(255) DEFAULT NULL COMMENT '缴回记事',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次过口动作',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_out_card_biz_no (biz_no),
  UNIQUE KEY uk_out_card_active_veh (active_veh_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='变型拖拉机牌证缴回事务卡';

CREATE TABLE IF NOT EXISTS t_amss_out_line (
  id bigint NOT NULL COMMENT '主键',
  card_id bigint NOT NULL COMMENT '所属缴回事务卡',
  stage int NOT NULL COMMENT '过口落定段 0收牌 1验车 2清账 3了结（退回归目的段、缘由归清账段）',
  action int NOT NULL COMMENT '过口动作 1向前 2退回重来 3清账缘由续记',
  ver_no int NOT NULL COMMENT '段内说法版本（退回开新版、旧版沉底）；缘由线记缘由笔次',
  plate_qty int DEFAULT NULL COMMENT '号牌实收几枚（验车段三样之一，只挂验车线；清账段拿它与当场实收对）',
  license_status varchar(255) DEFAULT NULL COMMENT '行驶证与检验在册情况（验车段料）',
  claim_clear int DEFAULT NULL COMMENT '名下未结事故认定与罚款 0尚有未结 1已清（验车段料）',
  return_reason varchar(32) DEFAULT NULL COMMENT '缴回缘由 1报废 2灭失 3转外省 4主动交回',
  actor varchar(64) NOT NULL COMMENT '过口经办人',
  act_time datetime NOT NULL COMMENT '过口时刻',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注（退回原因等）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_out_line_once (card_id, stage, ver_no, action),
  KEY idx_out_line_card (card_id, act_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='变型拖拉机牌证缴回履历线（缴回履历）';

CREATE TABLE IF NOT EXISTS t_amss_pass_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '跨区作业证核准单号',
  node_no int DEFAULT NULL COMMENT '当前所在层 0..3',
  sign_mode int DEFAULT NULL COMMENT '同层核准办法 0任一人 1两名点齐',
  need_count int DEFAULT NULL COMMENT '本层应签人数',
  sign_count int DEFAULT NULL COMMENT '本层已签人数',
  status int DEFAULT NULL COMMENT '报批情形 0在核 1已核讫 2已打回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='跨区作业证逐级核准单';

CREATE TABLE IF NOT EXISTS t_amss_team_card (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '在队机手建档卡号',
  node_no int DEFAULT NULL COMMENT '本卡名录版本号',
  site_id int DEFAULT NULL COMMENT '所属农机服务组织底档',
  site_no varchar(64) DEFAULT NULL COMMENT '所属服务组织底档字号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本卡合并后在队服务面积(亩)',
  fine_amt decimal(12,2) DEFAULT NULL COMMENT '本年度新进队服务面积(亩)',
  content varchar(255) DEFAULT NULL COMMENT '核验串（随名录版本走）',
  grade_level int DEFAULT NULL COMMENT '在队服务覆盖程度',
  status int DEFAULT NULL COMMENT '进展 0待核对 1已核对 2已压死',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='跨区作业队在机手名录建档卡';

-- 初始底档数据（跨区作业队在机手名录）
-- 单卡在队面积上限：80 / 200 / 500 亩
INSERT IGNORE INTO t_amss_coop_doc (id, site_no, site_name, site_type, road_name, th1_max, th2_max, th3_max, status, del_flag, create_by, create_time)
VALUES (0, 'ZZ00', '江原县农机服务组织底档（临川县支农农机专业合作社）', '农机合作社', '江原省—江原市—临川县', 80.00, 200.00, 500.00, 0, 0, 'seed', NOW()),
       (1, 'ZZ01', '江原县农机服务组织底档（临川县丰收跨区作业队，已收回）', '跨区作业队', '江原省—江原市—临川县', 80.00, 200.00, 500.00, 1, 0, 'seed', NOW());


-- =====================================================================
-- 已部署旧库升级（CREATE TABLE IF NOT EXISTS 不会补列/补表，手工执行一次）
-- 1) 卡表补车号、活卡占位生成列与索引
-- ALTER TABLE t_amss_out_card
--   ADD COLUMN veh_no varchar(64) NOT NULL COMMENT '变型拖拉机号牌（一台车一个号）' AFTER biz_no,
--   ADD COLUMN active_veh_no varchar(64) GENERATED ALWAYS AS
--     (if((status = 2 or del_flag = 1), NULL, veh_no)) STORED
--     COMMENT '活卡占位：在办挂车号、了结或删除腾空',
--   ADD UNIQUE KEY uk_out_card_biz_no (biz_no),
--   ADD UNIQUE KEY uk_out_card_active_veh (active_veh_no);
-- 旧卡补车号（按实际业务回填，例）：
-- UPDATE t_amss_out_card SET veh_no = id WHERE veh_no IS NULL OR veh_no = '';
-- 2) 履历线表为新表，直接执行上文 CREATE TABLE t_amss_out_line 即可。
-- =====================================================================
