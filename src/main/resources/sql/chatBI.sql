-- 基金表 (兼容ProductInfo实体类)
DROP TABLE IF EXISTS funds_products;
CREATE TABLE funds_products (
                       id INT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键ID',
                       product_code VARCHAR(20) UNIQUE NOT NULL COMMENT '基金代码',
                       product_name VARCHAR(100) NOT NULL COMMENT '基金名称',
                       fund_type VARCHAR(20) NOT NULL COMMENT '基金类型',
                       fund_company VARCHAR(50) NOT NULL COMMENT '基金公司',
                       manager VARCHAR(30) NOT NULL COMMENT '基金经理',
                       net_value DECIMAL(10,4) NOT NULL COMMENT '单位净值',
                       accum_net_value DECIMAL(10,4) NOT NULL COMMENT '累计净值',
                       establish_date DATE NOT NULL COMMENT '成立日期',
                       fund_size DECIMAL(15,2) NOT NULL COMMENT '基金规模(元)',
                       risk_level VARCHAR(20) NOT NULL COMMENT '风险等级',
                       fee_rate DECIMAL(5,3) NOT NULL COMMENT '费率(%)',
                       purchase_status VARCHAR(20) DEFAULT '开放申购' COMMENT '申购状态',
                       min_purchase DECIMAL(10,2) NOT NULL COMMENT '最低申购金额(元)',
                       daily_return DECIMAL(5,2) COMMENT '日收益率(%)',
                       weekly_return DECIMAL(5,2) COMMENT '周收益率(%)',
                       monthly_return DECIMAL(5,2) COMMENT '月收益率(%)',
                       quarter_return DECIMAL(5,2) COMMENT '季度收益率(%)',
                       half_year_return DECIMAL(5,2) COMMENT '半年收益率(%)',
                       annual_return DECIMAL(5,2) COMMENT '年收益率(%)',

    -- 添加CHECK约束
                       CONSTRAINT chk_fund_type CHECK (fund_type IN ('股票型', '债券型', '混合型', '货币型', 'QDII')),
                       CONSTRAINT chk_risk_level CHECK (risk_level IN ('R1', 'R2', 'R3', 'R4', 'R5')),
                       CONSTRAINT chk_purchase_status CHECK (purchase_status IN ('开放申购', '暂停申购'))
);

-- 理财产品表 (兼容ProductInfo实体类)
DROP TABLE IF EXISTS financial_products;
CREATE TABLE financial_products (
                                    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键ID',
                                    product_code VARCHAR(20) UNIQUE NOT NULL COMMENT '产品代码',
                                    product_name VARCHAR(100) NOT NULL COMMENT '产品名称',
                                    issuer VARCHAR(50) NOT NULL COMMENT '发行机构',
                                    product_type VARCHAR(20) NOT NULL COMMENT '产品类型',
                                    expect_return DECIMAL(5,2) NOT NULL COMMENT '预期收益率(%)',
                                    actual_return DECIMAL(5,2) COMMENT '实际收益率(%)',
                                    duration INT NOT NULL COMMENT '产品期限(天)',
                                    start_date DATE NOT NULL COMMENT '起售日期',
                                    end_date DATE COMMENT '到期日期',
                                    min_invest DECIMAL(12,2) NOT NULL COMMENT '最低投资额(元)',
                                    risk_level VARCHAR(20) NOT NULL COMMENT '风险等级',
                                    risk_control CLOB COMMENT '风控措施',

    -- 添加CHECK约束
                                    CONSTRAINT chk_product_type CHECK (product_type IN ('固定收益', '净值型', '结构性', '权益类')),
                                    CONSTRAINT chk_risk_level_fp CHECK (risk_level IN ('R1', 'R2', 'R3', 'R4', 'R5'))
);

-- 创建索引
CREATE INDEX idx_fund_type ON funds_products (fund_type);
CREATE INDEX idx_risk_level ON funds_products (risk_level);
CREATE INDEX idx_product_type_fp ON financial_products (product_type);
CREATE INDEX idx_risk_level_fp ON financial_products (risk_level);

-- 用户信息表
drop table if exists user_info;
CREATE TABLE user_info (
                           user_id VARCHAR(20) PRIMARY KEY COMMENT '用户ID',
                           user_name VARCHAR(50) NOT NULL COMMENT '姓名',
                           age INT COMMENT '年龄',
                           occupation VARCHAR(20) COMMENT '职业' CHECK (occupation IN ('学生','白领','企业主','退休')),
                           risk_level VARCHAR(20) COMMENT '风险等级' CHECK (risk_level IN ('R1','R2','R3','R4','R5')),
                           total_assets DECIMAL(15,2) COMMENT '总资产（元）',
                           last_login_date DATE COMMENT '最近登录时间',
                           preferred_category VARCHAR(100) COMMENT '偏好品类（如基金、黄金）'
);

-- 资产组合表
drop table if exists portfolio;
CREATE TABLE portfolio (
                           portfolio_id VARCHAR(30) PRIMARY KEY COMMENT '组合ID',
                           user_id VARCHAR(20) NOT NULL COMMENT '用户ID',
                           product_code VARCHAR(20) NOT NULL COMMENT '产品代码（关联基金/理财）',
                           product_type VARCHAR(20) COMMENT '产品类型' CHECK (product_type IN ('FINANCIAL','FUND')),
                           hold_amount DECIMAL(15,2) COMMENT '持有金额（元）',
                           purchase_date DATE COMMENT '购买日期',
                           yield_rate DECIMAL(5,2) COMMENT '当前收益率(%)',
                           FOREIGN KEY (user_id) REFERENCES user_info(user_id)
);

-- 交易记录表
drop table if exists transaction;
CREATE TABLE transaction (
                             trans_id VARCHAR(30) PRIMARY KEY COMMENT '交易流水号',
                             user_id VARCHAR(20) NOT NULL COMMENT '用户ID',
                             trans_type VARCHAR(20) COMMENT '交易类型' CHECK (trans_type IN ('申购','赎回','转账','分红')),
                             trans_amount DECIMAL(15,2) COMMENT '交易金额（元）',
                             trans_time TIMESTAMP COMMENT '交易时间',
                             account_type VARCHAR(20) COMMENT '账户类型' CHECK (account_type IN ('储蓄卡','信用卡','理财账户')),
                             status VARCHAR(20) COMMENT '状态' CHECK (status IN ('成功','失败','处理中')),
                             FOREIGN KEY (user_id) REFERENCES user_info(user_id)
);

-- 行情数据表
drop table if exists market_data;
CREATE TABLE market_data (
                             product_code VARCHAR(20) PRIMARY KEY COMMENT '产品代码',
                             product_name VARCHAR(100) NOT NULL COMMENT '产品名称',
                             latest_price DECIMAL(10,4) COMMENT '最新价',
                             daily_change DECIMAL(5,2) COMMENT '日涨跌幅(%)',
                             update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
                             pe_ratio DECIMAL(10,2) COMMENT '市盈率（股票类）',
                             yield_curve CLOB COMMENT '收益率曲线（债券类）'
);

-- 评价表
drop table if exists product_review;
CREATE TABLE product_review (
    review_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '评价ID',
    user_id VARCHAR(20) NOT NULL COMMENT '用户ID',
    product_code VARCHAR(20) NOT NULL COMMENT '产品代码',
    rating TINYINT COMMENT '评分（1-5分）' CHECK (rating BETWEEN 1 AND 5),
    comment CLOB COMMENT '评价内容',
    review_date DATE COMMENT '评价日期'
);

CREATE INDEX idx_product_code ON product_review (product_code);

-- 用户资产表（合并持仓概览和持仓产品）
DROP TABLE IF EXISTS user_assets;
CREATE TABLE user_assets (
                             asset_id VARCHAR(30) PRIMARY KEY COMMENT '资产ID',
                             user_id VARCHAR(20) NOT NULL COMMENT '用户ID',
                             total_assets DECIMAL(15,2) NOT NULL COMMENT '总资产(元)',
                             market_volatility DECIMAL(5,4) NOT NULL COMMENT '市场波动率',
                             industry_avg_return DECIMAL(5,4) NOT NULL COMMENT '行业平均回报',
                             top_industries VARCHAR(200) COMMENT '持仓前三行业(JSON数组)',
                             top_holding VARCHAR(20) COMMENT '最大持仓产品代码',
                             industry_concentration DECIMAL(5,4) COMMENT '行业集中度',
                             portfolio_volatility DECIMAL(5,4) COMMENT '组合波动率',
                             account_size DECIMAL(15,2) COMMENT '账户规模(元)',
                             cash_ratio DECIMAL(5,4) COMMENT '现金比例',
                             stock_position DECIMAL(5,4) COMMENT '股票仓位',
                             product_code VARCHAR(20) NOT NULL COMMENT '产品代码',
                             product_name VARCHAR(100) NOT NULL COMMENT '产品名称',
                             product_type VARCHAR(20) NOT NULL COMMENT '产品类型',
                             holding_value DECIMAL(15,2) NOT NULL COMMENT '持有价值(元)',
                             holding_percentage DECIMAL(5,4) NOT NULL COMMENT '持仓占比',
                             annualized_return DECIMAL(5,4) NOT NULL COMMENT '年化回报率',
                             risk_level VARCHAR(20) NOT NULL COMMENT '风险等级',
                             update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间'
);

-- 插入用户资产数据
INSERT INTO user_assets (
    asset_id, user_id, total_assets, market_volatility, industry_avg_return,
    top_industries, top_holding, industry_concentration,
    portfolio_volatility, account_size, cash_ratio, stock_position,
    product_code, product_name, product_type,
    holding_value, holding_percentage, annualized_return, risk_level
) VALUES
-- 用户U1001的资产数据
('A1001', 'U1001', 80000.00, 0.045, 0.082,
 '["医疗","消费"]', 'F001', 0.65, 0.032, 80000.00, 0.15, 0.70,
 '[F001,F005]', '[南方医疗基金,广发消费基金]', '[FUND]', 50000.00, 0.625, 0.0823, 'R1'),

-- 用户U1002的资产数据
('A1003', 'U1002', 200000.00, 0.062, 0.095,
 '["金融","能源","科技"]', 'P003', 0.72, 0.048, 200000.00, 0.10, 0.85,
 'P003', '阳光金选1号', 'FINANCIAL', 200000.00, 1.000, 0.1234, 'R4'),

-- 用户U1003的资产数据
('A1004', 'U1003', 60000.00, 0.038, 0.075,
 '["消费","医疗","债券"]', 'F003', 0.58, 0.028, 60000.00, 0.20, 0.60,
 'F003', '易方达稳健收益', 'FUND', 40000.00, 0.6667, 0.0345, 'R2'),

('A1005', 'U1003', 60000.00, 0.038, 0.075,
 '["消费","医疗","债券"]', 'F003', 0.58, 0.028, 60000.00, 0.20, 0.60,
 'P004', '建信龙宝', 'FINANCIAL', 20000.00, 0.3333, 0.0278, 'R2'),

-- 用户U1004的资产数据
('A1006', 'U1004', 100000.00, 0.025, 0.055,
 '["债券","黄金","货币"]', 'P001', 0.42, 0.018, 100000.00, 0.25, 0.15,
 'P001', '安鑫悦享90天', 'FINANCIAL', 100000.00, 1.000, 0.0392, 'R1'),

-- 用户U1005的资产数据
('A1007', 'U1005', 15000.00, 0.085, 0.105,
 '["科技","消费","新能源"]', 'F010', 0.78, 0.062, 15000.00, 0.05, 0.90,
 'F010', '汇添富消费', 'FUND', 15000.00, 1.000, 0.1567, 'R3');

-- 语义指纹-SQL模板映射表
DROP TABLE IF EXISTS sql_template_mapping;
CREATE TABLE sql_template_mapping (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    question VARCHAR(200) UNIQUE NOT NULL COMMENT '问题',
    sql_template TEXT NOT NULL COMMENT 'SQL模板',
    description VARCHAR(200) COMMENT '用途描述',
    intent_tag VARCHAR(20) NOT NULL DEFAULT 'detail_query' COMMENT '意图标签'
);

-- 插入初始映射关系 (添加intent_tag)
INSERT INTO sql_template_mapping (question, sql_template, description, intent_tag) VALUES
('基金基本信息', 'SELECT * FROM funds_products WHERE product_code = ''{fund_code}''', '查询指定基金详情', 'detail_query'),
('按风险等级筛选产品', 'SELECT * FROM financial_products WHERE risk_level = ''{risk_level}''', '按风险等级筛选基金/理财', 'filter'),
('用户持仓查询', 'SELECT * FROM portfolio WHERE user_id = ''{user_id}''', '查询用户持仓情况', 'detail_query'),
('基金收益排行', 'SELECT * FROM funds_products WHERE fund_type=''{fund_type}'' ORDER BY {return_type} DESC LIMIT {limit}', '基金按收益类型排序', 'ranking'),
('产品行情数据', 'SELECT * FROM market_data WHERE product_code = ''{product_code}''', '查询产品市场行情', 'detail_query'),
('用户交易记录', 'SELECT * FROM transaction WHERE user_id=''{user_id}'' AND trans_time BETWEEN ''{start_date}'' AND ''{end_date}''', '按时间范围查交易记录', 'detail_query'),
('产品评价查询', 'SELECT * FROM product_review WHERE product_code = ''{product_code}''', '查询产品用户评价', 'detail_query'),
('高风险用户持仓', 'SELECT p.* FROM portfolio p JOIN user_info u ON p.user_id=u.user_id WHERE u.risk_level=''R5''', '查询高风险用户持仓', 'filter'),
('基金公司产品统计', 'SELECT fund_company, COUNT(*) AS product_count FROM funds_products GROUP BY fund_company', '按基金公司统计产品数量', 'statistics'),
('活跃用户查询', 'SELECT * FROM user_info WHERE last_login_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) ORDER BY last_login_date DESC', '查询最近活跃用户', 'filter'),
('基金类型分布', 'SELECT fund_type, COUNT(*) AS type_count FROM funds_products GROUP BY fund_type', '统计各类基金数量', 'statistics'),
('用户持仓汇总', 'SELECT user_id, SUM(holding_value) AS total_value FROM portfolio GROUP BY user_id', '用户持仓总价值统计', 'statistics'),
('基金详情查询', 'SELECT * FROM funds_products WHERE product_name LIKE ''%{keyword}%''', '关键词搜索基金产品', 'detail_query'),
('交易量TOP10产品', 'SELECT product_code, SUM(trans_amount) AS total_amount FROM transaction GROUP BY product_code ORDER BY total_amount DESC LIMIT 10', '交易量前十产品统计', 'statistics'),
('用户风险评估', 'SELECT risk_level, COUNT(*) AS user_count FROM user_info GROUP BY risk_level', '用户风险等级分布', 'statistics'),
('基金公司产品线', 'SELECT fund_company, GROUP_CONCAT(DISTINCT fund_type) AS product_lines FROM funds_products GROUP BY fund_company', '各公司产品类型分布', 'statistics'),
('产品收益率对比', 'SELECT product_code, (current_price - purchase_price)/purchase_price AS yield FROM market_data WHERE product_code IN (''{code1}'', ''{code2}'')', '两个产品收益率对比', 'comparison'),
('用户持仓产品类型', 'SELECT p.fund_type, COUNT(*) AS holding_count FROM portfolio h JOIN funds_products p ON h.product_code = p.product_code WHERE user_id = ''{user_id}'' GROUP BY p.fund_type', '用户持仓类型分布', 'statistics'),
('新发行产品查询', 'SELECT * FROM funds_products WHERE launch_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)', '查询最近一月新产品', 'filter'),
('同类产品对比', 'SELECT * FROM funds_products WHERE fund_type = ''{fund_type}'' AND risk_level = ''{risk_level}'' ORDER BY {sort_field} desc', '同类产品多维度对比', 'comparison');

-- 生成用户测试数据
INSERT INTO user_info (user_id, user_name, age, occupation, risk_level, total_assets, last_login_date, preferred_category) VALUES
('U1001', '张明', 32, '白领', 'R3', 185000.00, '2023-12-15', '基金,黄金'),
('U1002', '李华', 45, '企业主', 'R4', 850000.00, '2023-12-18', '理财,股票'),
('U1003', '王芳', 28, '白领', 'R2', 95000.00, '2023-12-10', '基金,存款'),
('U1004', '赵强', 60, '退休', 'R1', 420000.00, '2023-12-05', '理财,黄金'),
('U1005', '陈思', 22, '学生', 'R3', 35000.00, '2023-12-20', '基金,黄金'),
('U1006', '杨光', 38, '企业主', 'R5', 1200000.00, '2023-12-16', '股票,结构性产品'),
('U1007', '周婷', 50, '白领', 'R2', 280000.00, '2023-12-12', '货币基金,存款'),
('U1008', '吴磊', 25, '白领', 'R4', 110000.00, '2023-12-19', '混合基金,QDII'),
('U1009', '郑琳', 65, '退休', 'R1', 580000.00, '2023-12-08', '固定收益,黄金'),
('U1010', '孙宇', 30, '企业主', 'R3', 750000.00, '2023-12-14', '债券,权益类');

-- 生成资产组合数据
INSERT INTO portfolio (portfolio_id, user_id, product_code, product_type, hold_amount, purchase_date, yield_rate) VALUES
('PTF1001', 'U1001', 'F001', 'FUND', 50000.00, '2023-06-15', 8.23),
('PTF1002', 'U1001', 'F005', 'FUND', 30000.00, '2023-09-01', 1.85),
('PTF1003', 'U1002', 'P003', 'FINANCIAL', 200000.00, '2023-02-01', 12.34),
('PTF1004', 'U1003', 'F003', 'FUND', 40000.00, '2023-07-22', 3.45),
('PTF1005', 'U1003', 'P004', 'FINANCIAL', 20000.00, '2023-10-10', 2.78),
('PTF1006', 'U1004', 'P001', 'FINANCIAL', 100000.00, '2023-09-05', 3.92),
('PTF1007', 'U1005', 'F010', 'FUND', 15000.00, '2023-08-18', 15.67),
('PTF1008', 'U1006', 'P005', 'FINANCIAL', 500000.00, '2023-03-10', 25.89),
('PTF1009', 'U1007', 'F005', 'FUND', 50000.00, '2022-12-01', 3.60),
('PTF1010', 'U1008', 'F008', 'FUND', 80000.00, '2023-05-20', 18.45),
('PTF1011', 'U1009', 'P010', 'FINANCIAL', 200000.00, '2023-05-15', 8.76),
('PTF1012', 'U1010', 'F006', 'FUND', 300000.00, '2023-01-05', 42.15);

-- 生成交易记录
INSERT INTO transaction (trans_id, user_id, trans_type, trans_amount, trans_time, account_type, status) VALUES
('T20231215001', 'U1001', '申购', 20000.00, '2023-12-15 10:23:45', '储蓄卡', '成功'),
('T20231216002', 'U1002', '赎回', 50000.00, '2023-12-16 14:35:22', '理财账户', '成功'),
('T20231210003', 'U1003', '申购', 10000.00, '2023-12-10 09:12:33', '信用卡', '成功'),
('T20231205004', 'U1004', '分红', 3560.00, '2023-12-05 11:45:18', '理财账户', '成功'),
('T20231220005', 'U1005', '申购', 5000.00, '2023-12-20 13:27:54', '储蓄卡', '处理中'),
('T20231216006', 'U1006', '转账', 200000.00, '2023-12-16 16:20:43', '理财账户', '成功'),
('T20231212007', 'U1007', '赎回', 30000.00, '2023-12-12 10:55:21', '储蓄卡', '成功'),
('T20231219008', 'U1008', '申购', 20000.00, '2023-12-19 15:33:47', '信用卡', '成功'),
('T20231208009', 'U1009', '分红', 7820.00, '2023-12-08 09:42:16', '理财账户', '成功'),
('T20231214010', 'U1010', '申购', 100000.00, '2023-12-14 11:18:29', '储蓄卡', '成功');

-- 生成行情数据
INSERT INTO market_data (product_code, product_name, latest_price, daily_change, pe_ratio, yield_curve) VALUES
('F001', '南方医疗基金', 2.3501, 0.45, 15.34, NULL),
('F002', '南方消费升级', 1.8865, -0.32, 22.56, NULL),
('F003', '易方达稳健收益', 1.1268, 0.12, NULL, '{"1Y":3.45,"3Y":4.12,"5Y":4.56}'),
('F004', '嘉实全球精选', 1.5492, -1.23, 18.90, NULL),
('F005', '广发消费基金', 1.0000, 0.01, NULL, NULL),
('F006', '招商中证白酒', 1.4745, 1.25, 28.45, NULL),
('F007', '工银瑞信双利', 1.2356, 0.08, NULL, '{"1Y":3.78,"3Y":4.25,"5Y":4.68}'),
('F008', '广发科技先锋', 1.9978, -0.78, 35.67, NULL),
('F009', '富国天惠成长', 3.4678, 0.34, 19.78, NULL),
('F010', '汇添富消费', 2.8134, 0.89, 24.56, NULL),
('P001', '安鑫悦享90天', 1.0392, NULL, NULL, '{"90D":3.92}'),
('P002', '招银睿远平衡', 1.0478, 0.12, NULL, NULL),
('P003', '阳光金选1号', 1.0545, 0.25, NULL, NULL),
('P004', '建信龙宝', 1.0315, 0.05, NULL, NULL),
('P005', '中信科创成长', 1.0845, 0.65, NULL, NULL);

-- 生成产品评价
INSERT INTO product_review (user_id, product_code, rating, comment, review_date) VALUES
('U1001', 'F001', 5, '收益稳定，经理操作稳健', '2023-11-20'),
('U1003', 'F003', 4, '波动较小，适合保守型投资者', '2023-10-15'),
('U1005', 'F010', 5, '消费赛道长期看好，超额收益明显', '2023-12-05'),
('U1002', 'P003', 3, '达到预期收益但波动较大', '2023-11-30'),
('U1004', 'P001', 4, '90天产品灵活性好，收益率超预期', '2023-12-10'),
('U1007', 'F005', 5, '零钱管理神器，随时可取', '2023-09-18'),
('U1008', 'F008', 4, '科技主题有潜力，但近期回调较大', '2023-12-12'),
('U1010', 'F006', 5, '白酒行业复苏，表现强势', '2023-12-18'),
('U1009', 'P010', 3, '收益稳定但期限较长', '2023-10-22'),
('U1006', 'P005', 4, '高收益伴随高风险，适合长期配置', '2023-12-15');
