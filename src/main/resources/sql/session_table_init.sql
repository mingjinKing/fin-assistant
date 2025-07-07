DROP TABLE IF EXISTS dialog_stage;
-- 对话阶段主表
CREATE TABLE dialog_stage (
    stage_id INT PRIMARY KEY AUTO_INCREMENT,
    stage_name VARCHAR(20) NOT NULL,  -- 探索期/认知期/决策期/行动期
    transition_condition VARCHAR(200)  -- 阶段转移条件
);

DROP table if exists dialog_template;
-- 对话模板表
CREATE TABLE dialog_template (
     template_id BIGINT PRIMARY KEY AUTO_INCREMENT,
     stage_id INT NOT NULL,
     template_type ENUM('OPENING', 'FOLLOWUP', 'TRANSITION') NOT NULL,
     template_text VARCHAR(500) NOT NULL,
     interest_dim VARCHAR(50),  -- 关联的兴趣维度
     next_state VARCHAR(50),
     embedding_vector BLOB,  -- 768维向量
     FOREIGN KEY (stage_id) REFERENCES dialog_stage(stage_id)
);

DROP table if exists user_dialog_state;
-- 用户对话状态表
CREATE TABLE user_dialog_state (
    user_id VARCHAR(50) PRIMARY KEY,
    current_stage INT NOT NULL DEFAULT 1,
    last_template_id BIGINT,
    context_data TEXT,  -- JSON格式的上下文
    FOREIGN KEY (current_stage) REFERENCES dialog_stage(stage_id),
    FOREIGN KEY (last_template_id) REFERENCES dialog_template(template_id)
);

drop table if exists chat_record;
-- H2数据库建表语句
CREATE TABLE chat_record (
     id BIGINT PRIMARY KEY AUTO_INCREMENT,
     session_id VARCHAR(255) NOT NULL,
     user_message VARCHAR(2000),
     ai_response CLOB,
     timestamp TIMESTAMP NOT NULL
);

-- 添加索引优化查询性能
CREATE INDEX idx_session_id ON chat_record(session_id);
CREATE INDEX idx_timestamp ON chat_record(timestamp);

-- 阶段定义
INSERT INTO dialog_stage (stage_name, transition_condition) VALUES
('探索期', '检测到产品关键词或明确需求'),
('认知期', '用户选择具体产品或表达深入了解意愿'),
('决策期', '用户提及多个产品或要求对比'),
('行动期', '用户表现出购买意向或要求推荐');

-- 探索期模板
INSERT INTO dialog_template (stage_id, template_type, template_text, interest_dim) VALUES
(1, 'OPENING', '您好！我是您的理财助手。您最近在关注哪类理财产品呢？', 'product_category'),
(1, 'FOLLOWUP', '您更看重产品的{安全性|收益性|流动性}哪个方面呢？', 'risk_preference'),
(1, 'FOLLOWUP', '您对{货币基金|债券基金|指数基金}这类产品有了解吗？', 'knowledge_level');

-- 认知期模板
INSERT INTO dialog_template (stage_id, template_type, template_text, interest_dim) VALUES
(2, 'OPENING', '您选择的{产品名}主要投资于{投资方向}，属于{风险等级}风险产品', 'product_detail'),
(2, 'FOLLOWUP', '需要我详细解释{专业术语}的含义吗？', 'knowledge_gap'),
(2, 'FOLLOWUP', '这款产品的历史年化收益是{收益率}，最大回撤是{回撤率}', 'performance_metric');

-- 决策期模板
INSERT INTO dialog_template (stage_id, template_type, template_text, interest_dim) VALUES
(3, 'OPENING', '您正在对比的{产品A}和{产品B}，在收益方面{产品A}更高，在风险方面{产品B}更稳定', 'comparison'),
(3, 'FOLLOWUP', '从您的风险承受能力来看，{产品名}可能更适合您', 'personalization'),
(3, 'FOLLOWUP', '这两款产品的主要区别在于{差异点}，您更看重哪一点？', 'decision_factor');

-- 行动期模板
INSERT INTO dialog_template (stage_id, template_type, template_text, interest_dim) VALUES
(4, 'OPENING', '根据您的需求，我推荐{产品名}，现在购买可享受{优惠}', 'recommendation'),
(4, 'FOLLOWUP', '需要我为您生成专属购买链接吗？', 'action_prompt'),
(4, 'TRANSITION', '购买完成后，我可以帮您设置收益提醒服务', 'retention');