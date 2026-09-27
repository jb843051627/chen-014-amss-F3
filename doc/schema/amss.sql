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
  stage int DEFAULT NULL COMMENT '当前缴回段 0..3（收牌/验车/清账/了结）',
  status int DEFAULT NULL COMMENT '卡的落定 0未起 1在办 2已了结',
  content varchar(255) DEFAULT NULL COMMENT '缴回记事',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次过口动作',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='变型拖拉机牌证缴回事务卡';

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
  bill_no varchar(64) DEFAULT NULL COMMENT '在队机手建档卡号(系统给号,一卡多版共用)',
  node_no int DEFAULT NULL COMMENT '本卡名录版本号(首版1,重录逐版加一)',
  site_id bigint DEFAULT NULL COMMENT '所属农机服务组织底档',
  site_no varchar(64) DEFAULT NULL COMMENT '所属服务组织底档字号(落档凭字号核对)',
  owner_name varchar(64) DEFAULT NULL COMMENT '机主(机手)姓名,与底档字号合成去重死键',
  drive_code varchar(8) DEFAULT NULL COMMENT '准驾代号 G1/K1/L/R/S',
  work_type varchar(16) DEFAULT NULL COMMENT '作业类别 机收/机播/烘干/转运',
  harvester_model varchar(128) DEFAULT NULL COMMENT '收割机型(作业类别=机收时必填)',
  daily_capacity decimal(12,2) DEFAULT NULL COMMENT '日作业能力(亩/日,作业类别=机收时必填)',
  seed_rows int DEFAULT NULL COMMENT '播种行数(作业类别=机播时必填)',
  batch_capacity decimal(12,2) DEFAULT NULL COMMENT '批处理量(作业类别=烘干时必填)',
  haul_range varchar(255) DEFAULT NULL COMMENT '承运区间(作业类别=转运时必填)',
  traction_tool varchar(128) DEFAULT NULL COMMENT '牵引机具(作业类别=转运时必填)',
  qty decimal(12,2) DEFAULT NULL COMMENT '本卡合并后在队服务面积(亩)',
  fine_amt decimal(12,2) DEFAULT NULL COMMENT '本年度新进队服务面积(亩)',
  grade_level int DEFAULT NULL COMMENT '在队服务覆盖程度(系统折算回填,1/2/3档)',
  content varchar(255) DEFAULT NULL COMMENT '核验串(压死那一步成形,随名录版本走,成形不可覆)',
  status int DEFAULT NULL COMMENT '进展 0待核对 1已核对 2已压死',
  cur_flag int DEFAULT '1' COMMENT '现行版本标记 0历史版本 1现行版本(同卡同时只一行现行)',
  handler varchar(64) DEFAULT NULL COMMENT '本趟承办人(办一笔续一笔,与创建人各归各)',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建人(立卡署名,重录不挪)',
  create_time datetime DEFAULT NULL COMMENT '创建时间(本版摊开那一天)',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_team_card_bill_node (bill_no, node_no),
  KEY idx_team_card_site_owner_cur (site_no, owner_name, cur_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='跨区作业队在机手名录建档卡';

CREATE TABLE IF NOT EXISTS t_amss_user_site (
  id bigint NOT NULL COMMENT '主键',
  user_id bigint DEFAULT NULL COMMENT '经办账号(用户id)',
  site_id bigint DEFAULT NULL COMMENT '受托底档id',
  scope_type int DEFAULT NULL COMMENT '可见情形 0本辖 1跨站协办',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_user_site_user (user_id, site_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='经办账号可见底档授权(本辖与跨站协办)';

-- 初始底档数据（跨区作业队在机手名录）
-- 单卡在队面积上限：80 / 200 / 500 亩
INSERT IGNORE INTO t_amss_coop_doc (id, site_no, site_name, site_type, road_name, th1_max, th2_max, th3_max, status, del_flag, create_by, create_time)
VALUES (0, 'ZZ00', '江原县农机服务组织底档（临川县支农农机专业合作社）', '农机合作社', '江原省—江原市—临川县', 80.00, 200.00, 500.00, 0, 0, 'seed', NOW()),
       (1, 'ZZ01', '江原县农机服务组织底档（临川县丰收跨区作业队，已收回）', '跨区作业队', '江原省—江原市—临川县', 80.00, 200.00, 500.00, 1, 0, 'seed', NOW());

-- 名册菜单与按钮权限（挂管理员角色 488243256161730560）
INSERT IGNORE INTO t_sys_permission (id, name, descripion, url, is_blank, pid, perms, type, icon, order_num, visible, create_by, create_time)
VALUES (9101, '农机跨区名册', '农机跨区作业名册目录', '', 0, 0, '', 0, 'layui-icon-read', 90, 0, 'seed', NOW()),
       (9102, '组织底档', '组织底档台账', '/AmssCoopDocController/view', 0, 9101, 'amss:amssCoopDoc:view', 1, '', 1, 0, 'seed', NOW()),
       (9103, '底档集合', '底档集合查询', '/AmssCoopDocController/list', 0, 9102, 'amss:amssCoopDoc:list', 2, '', NULL, 0, 'seed', NOW()),
       (9104, '底档立档', '底档立档', '/AmssCoopDocController/add', 0, 9102, 'amss:amssCoopDoc:add', 2, '', NULL, 0, 'seed', NOW()),
       (9105, '底档收回', '底档收回封口', '/AmssCoopDocController/revoke', 0, 9102, 'amss:amssCoopDoc:revoke', 2, '', NULL, 0, 'seed', NOW()),
       (9112, '机手建档卡', '在机手名录建档卡台账', '/AmssTeamCardController/view', 0, 9101, 'amss:amssTeamCard:view', 1, '', 2, 0, 'seed', NOW()),
       (9113, '建档卡集合', '建档卡集合查询', '/AmssTeamCardController/list', 0, 9112, 'amss:amssTeamCard:list', 2, '', NULL, 0, 'seed', NOW()),
       (9114, '建档卡立卡', '建档卡立卡', '/AmssTeamCardController/add', 0, 9112, 'amss:amssTeamCard:add', 2, '', NULL, 0, 'seed', NOW()),
       (9115, '建档卡重录核对', '建档卡重录/核对/压死', '/AmssTeamCardController/edit', 0, 9112, 'amss:amssTeamCard:edit', 2, '', NULL, 0, 'seed', NOW()),
       (9116, '建档卡注销', '建档卡注销', '/AmssTeamCardController/remove', 0, 9112, 'amss:amssTeamCard:remove', 2, '', NULL, 0, 'seed', NOW());

INSERT IGNORE INTO t_sys_permission_role (id, role_id, permission_id, create_by, create_time)
SELECT 9200 + n, 488243256161730560, 9101 + n, 'seed', NOW()
FROM (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
      UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14 UNION SELECT 15) seq;

-- 可见范围示例：本站账号 fuce(433236479427350528) 只辖底档0；管理员角色不经此表，拿账号看全辖。
-- 跨站协办时另插 scope_type=1 的一行，受托哪一家才瞧得见那一家。
INSERT IGNORE INTO t_amss_user_site (id, user_id, site_id, scope_type, del_flag, create_by, create_time)
VALUES (1, 433236479427350528, 0, 0, 0, 'seed', NOW());

