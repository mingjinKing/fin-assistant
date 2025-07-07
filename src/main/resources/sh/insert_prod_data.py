import random
from datetime import datetime, timedelta

# 基金公司列表
fund_companies = ['华夏','易方达','南方','博时','嘉实','天弘','广发','汇添富','富国','招商']
# 银行列表
banks = ['工商银行','建设银行','农业银行','中国银行','招商银行','交通银行','浦发银行','中信银行','民生银行','光大银行']

def generate_fund_data(num):
    data = []
    for i in range(1, num+1):
        fund_code = f"F{i:03d}"
        fund_type = random.choice(['股票型','债券型','混合型','货币型','QDII'])
        risk_level = random.choice(['R1','R2','R3','R4','R5'])

        # 根据基金类型确定净值范围
        if fund_type == '货币型':
            net_val = round(1 + random.random()*0.1, 4)
            accum_val = net_val
        else:
            net_val = round(random.uniform(0.8, 5.0), 4)
            accum_val = round(net_val * random.uniform(1.5, 3.0), 4)

        # 构建数据记录
        record = (
            fund_code,
            f"{random.choice(fund_companies)}{random.choice(['成长','消费','科技','医疗','新能源'])}基金",
            fund_type,
            random.choice(fund_companies) + "基金",
            f"经理{random.choice(['张','王','李','赵','陈'])}{random.choice(['伟','强','娜','敏','华'])}",
            net_val,
            accum_val,
            (datetime.now() - timedelta(days=random.randint(365, 365*10))).strftime('%Y-%m-%d'),
            round(random.uniform(1, 500), 2),
            risk_level,
            round(random.uniform(0.1, 2.0), 3),
            random.choice(['开放申购','暂停申购']),
            random.choice([100, 500, 1000, 5000]),
            round(random.uniform(-2, 2), 2),
            round(random.uniform(-5, 5), 2),
            round(random.uniform(-8, 10), 2),
            round(random.uniform(-10, 25), 2),
            round(random.uniform(-15, 40), 2),
            round(random.uniform(-20, 60), 2)
        )
        data.append(record)
    return data

def generate_product_data(num):
    data = []
    product_types = ['固定收益', '净值型', '结构性', '权益类']
    risk_controls = [
        "AA+级债券配置，资金池监管",
        "60%固收+40%权益，动态再平衡",
        "挂钩沪深300指数，保本80%",
        "货币市场工具为主，高流动性",
        "聚焦科创板IPO战略配售",
        "国债+高等级信用债组合",
        "同业存单+短期融资券",
        "挂钩中证500指数+量化对冲",
        "卫星导航产业链股权投资",
        "城投债+资产支持证券"
    ]

    for i in range(1, num+1):
        product_code = f"P{i:03d}"
        product_type = random.choice(product_types)

        # 根据产品类型确定预期收益率范围
        if product_type == '固定收益':
            expect_return = round(random.uniform(3.0, 5.0), 2)
        elif product_type == '净值型':
            expect_return = round(random.uniform(4.0, 6.0), 2)
        elif product_type == '结构性':
            expect_return = round(random.uniform(5.0, 7.0), 2)
        else:  # 权益类
            expect_return = round(random.uniform(6.0, 9.0), 2)

        actual_return = round(expect_return * random.uniform(0.95, 1.05), 2)
        duration = random.choice([30, 90, 180, 365, 545, 720])
        start_date = (datetime.now() - timedelta(days=random.randint(30, 365))).strftime('%Y-%m-%d')
        end_date = (datetime.strptime(start_date, '%Y-%m-%d') + timedelta(days=duration)).strftime('%Y-%m-%d')

        # 构建数据记录
        record = (
            product_code,
            f"{random.choice(['安鑫','睿远','阳光','龙宝','科创','添利','季季盈','指数','北斗','富竹'])}" +
            f"{random.choice(['悦享','平衡','金选','稳盈','成长','增强','优选','稳健','丰收'])}{duration}天",
            random.choice(banks) + ("理财" if random.random() > 0.3 else "财富"),
            product_type,
            expect_return,
            actual_return,
            duration,
            start_date,
            end_date,
            random.choice([1000, 5000, 10000, 50000, 100000]),
            random.choice(['R1','R2','R3','R4','R5']),
            random.choice(risk_controls)
        )
        data.append(record)
    return data

# 生成50条基金+50条理财数据
funds_data = generate_fund_data(50)
products_data = generate_product_data(50)

# 生成SQL文件
with open('init_data.sql', 'w', encoding='utf-8') as f:
    f.write("INSERT INTO funds (product_code, prod_name, fund_type, fund_company, manager, net_value, accum_net_value, establish_date, fund_size, risk_level, fee_rate, purchase_status, min_purchase, daily_return, weekly_return, monthly_return, quarter_return, half_year_return, annual_return) VALUES\n")
    f.write(",\n".join(str(d) for d in funds_data) + ";\n\n")

    f.write("INSERT INTO financial_products (product_code, product_name, issuer, product_type, expect_return, actual_return, duration, start_date, end_date, min_invest, risk_level, risk_control) VALUES\n")
    f.write(",\n".join(str(d) for d in products_data) + ";\n")