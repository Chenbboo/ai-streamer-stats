#!/usr/bin/env python3
"""MWreport 7月数据迁移到 ai-streamer-stats"""
import subprocess
import json
import re

# 配置
MWREPORT_HOST = "43.155.4.216"
MWREPORT_USER = "root"
MWREPORT_PASS = "3fff594452499eae"
MWREPORT_DB = "weekly_report"
LOCAL_MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
LOCAL_USER = "root"
LOCAL_PASS = "123456"
LOCAL_DB = "ry-vue"
SSH_KEY = r"C:\Users\97971\Desktop\香港服务器密钥.216_id_ed25519"

def run_remote_sql(sql):
    """在远程服务器执行SQL"""
    cmd = [
        "ssh", "-o", "StrictHostKeyChecking=no", "-i", SSH_KEY,
        f"root@{MWREPORT_HOST}",
        f"mysql -u{MWREPORT_USER} -p{MWREPORT_PASS} {MWREPORT_DB} -e \"{sql}\""
    ]
    result = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8')
    return result.stdout

def run_local_sql(sql):
    """在本地执行SQL"""
    import os
    env = os.environ.copy()
    env['MYSQL_PWD'] = LOCAL_PASS
    cmd = [LOCAL_MYSQL, f"-u{LOCAL_USER}", "--default-character-set=utf8mb4", LOCAL_DB, "-e", sql]
    result = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8', env=env)
    if result.returncode != 0:
        print(f"  SQL Error: {result.stderr[:200]}")
    return result.stdout

def escape_sql(s):
    """转义SQL字符串"""
    if s is None:
        return "NULL"
    return "'" + str(s).replace("\\", "\\\\").replace("'", "\\'") + "'"

def main():
    print("=== MWreport 数据迁移 ===\n")

    # 1. 获取主播列表
    print("1. 获取主播列表...")
    raw = run_remote_sql("SELECT id, name, monthly_diamond_kpi, monthly_interaction_kpi FROM anchors WHERE is_active=1;")
    streamers = {}
    for line in raw.strip().split('\n')[1:]:  # 跳过表头
        parts = line.split('\t')
        if len(parts) >= 4:
            sid, name, diamond_kpi, interaction_kpi = parts[0], parts[1], parts[2], parts[3]
            streamers[name] = {
                'id': int(sid),
                'diamond_kpi': int(diamond_kpi),
                'interaction_kpi': int(interaction_kpi)
            }
            print(f"   {name}: KPI={diamond_kpi}")

    # 2. 插入主播到本地（保持原始名称，tiktok_handle 去掉空格）
    # 使用 ON DUPLICATE KEY UPDATE 确保 stage_name 更新为带空格的原始名称
    print("\n2. 插入主播...")
    for name, info in streamers.items():
        handle = name.replace(' ', '')  # "zhen zhen" -> "zhenzhen"
        sql = f"""INSERT INTO live_streamer (user_id, stage_name, tiktok_handle, status, create_by, create_time)
                  VALUES ({info['id']}, {escape_sql(name)}, {escape_sql('@' + handle)}, '0', 'admin', sysdate())
                  ON DUPLICATE KEY UPDATE stage_name = {escape_sql(name)}, tiktok_handle = {escape_sql('@' + handle)};"""
        run_local_sql(sql)
        print(f"   {name} -> @{handle}")

    # 3. 获取主播ID映射（远程name -> 本地streamer_id）
    # 远程 name 可能有空格（如 "zhen zhen"），本地 stage_name 也是 "zhen zhen"
    print("\n3. 获取主播ID映射...")
    raw = run_local_sql("SELECT streamer_id, stage_name, tiktok_handle FROM live_streamer;")
    streamer_map = {}
    for line in raw.strip().split('\n')[1:]:
        parts = line.split('\t')
        if len(parts) >= 3:
            stage_name = parts[1].strip()
            handle = parts[2].strip().lstrip('@')
            streamer_id = int(parts[0])
            # 映射所有可能的变体
            streamer_map[stage_name] = streamer_id           # "zhen zhen"
            streamer_map[stage_name.lower()] = streamer_id   # "zhen zhen"
            streamer_map[handle] = streamer_id                # "zhenzhen"
            streamer_map[handle.replace(' ', '')] = streamer_id  # "zhenzhen"
    print(f"   {streamer_map}")

    # 4. 获取打赏记录
    print("\n4. 迁移打赏记录...")
    raw = run_remote_sql("""SELECT a.name, tr.dt, tf.fan_name, tr.amount
                           FROM tip_records tr
                           JOIN tip_fans tf ON tf.id=tr.tip_fan_id
                           JOIN anchors a ON a.id=tf.anchor_id
                           WHERE tr.dt >= '2026-07-01'
                           ORDER BY a.name, tr.dt;""")

    tip_records = []
    for line in raw.strip().split('\n')[1:]:
        parts = line.split('\t')
        if len(parts) >= 4:
            tip_records.append({
                'streamer': parts[0],
                'dt': parts[1],
                'fan_name': parts[2],
                'amount': int(parts[3])
            })
    print(f"   共 {len(tip_records)} 条打赏记录")

    # 插入客户和打赏记录
    customer_cache = {}  # (streamer_id, nickname) -> customer_id
    for i, rec in enumerate(tip_records):
        streamer_id = streamer_map.get(rec['streamer'])
        if not streamer_id:
            print(f"   跳过未知主播: {rec['streamer']} (可用: {list(streamer_map.keys())})")
            continue

        nickname = rec['fan_name']
        cache_key = (streamer_id, nickname)

        # 插入客户（如果不存在）
        if cache_key not in customer_cache:
            sql = f"""INSERT INTO live_customer (streamer_id, nickname, first_seen_date, last_seen_date, create_by, create_time)
                      SELECT {streamer_id}, {escape_sql(nickname)}, '{rec['dt']}', '{rec['dt']}', 'admin', sysdate()
                      FROM DUAL WHERE NOT EXISTS (
                          SELECT 1 FROM live_customer WHERE nickname = {escape_sql(nickname)} AND streamer_id = {streamer_id}
                      );"""
            run_local_sql(sql)

            # 获取customer_id
            raw = run_local_sql(f"SELECT customer_id FROM live_customer WHERE nickname = {escape_sql(nickname)} AND streamer_id = {streamer_id} LIMIT 1;")
            lines = raw.strip().split('\n')
            if len(lines) >= 2:
                customer_cache[cache_key] = int(lines[1])

        customer_id = customer_cache.get(cache_key)
        if not customer_id:
            continue

        # 插入打赏记录
        sql = f"""INSERT INTO live_gift_record (biz_date, streamer_id, customer_id, xu, rank_no, confirm_status, ai_confidence, create_time, update_by, update_time)
                  VALUES ('{rec['dt']}', {streamer_id}, {customer_id}, {rec['amount']}, 0, '1', '1', sysdate(), 'admin', sysdate())
                  ON DUPLICATE KEY UPDATE xu = VALUES(xu), update_time = sysdate();"""
        run_local_sql(sql)

        if (i + 1) % 50 == 0:
            print(f"   已处理 {i + 1}/{len(tip_records)}")

    print(f"   打赏记录完成")

    # 5. 获取聊天记录
    print("\n5. 迁移聊天记录...")
    raw = run_remote_sql("""SELECT a.name, ci.dt, cf.fan_name
                           FROM chat_interactions ci
                           JOIN chat_fans cf ON cf.id=ci.chat_fan_id
                           JOIN anchors a ON a.id=cf.anchor_id
                           WHERE ci.dt >= '2026-07-01'
                           ORDER BY a.name, ci.dt;""")

    chat_records = []
    for line in raw.strip().split('\n')[1:]:
        parts = line.split('\t')
        if len(parts) >= 3:
            chat_records.append({
                'streamer': parts[0],
                'dt': parts[1],
                'fan_name': parts[2]
            })
    print(f"   共 {len(chat_records)} 条聊天记录")

    for i, rec in enumerate(chat_records):
        streamer_id = streamer_map.get(rec['streamer'])
        if not streamer_id:
            print(f"   跳过未知主播: {rec['streamer']}")
            continue
        if not streamer_id:
            continue

        nickname = rec['fan_name']
        cache_key = (streamer_id, nickname)

        # 插入客户（如果不存在）
        if cache_key not in customer_cache:
            sql = f"""INSERT INTO live_customer (streamer_id, nickname, first_seen_date, last_seen_date, create_by, create_time)
                      SELECT {streamer_id}, {escape_sql(nickname)}, '{rec['dt']}', '{rec['dt']}', 'admin', sysdate()
                      FROM DUAL WHERE NOT EXISTS (
                          SELECT 1 FROM live_customer WHERE nickname = {escape_sql(nickname)} AND streamer_id = {streamer_id}
                      );"""
            run_local_sql(sql)

            raw = run_local_sql(f"SELECT customer_id FROM live_customer WHERE nickname = {escape_sql(nickname)} AND streamer_id = {streamer_id} LIMIT 1;")
            lines = raw.strip().split('\n')
            if len(lines) >= 2:
                customer_cache[cache_key] = int(lines[1])

        customer_id = customer_cache.get(cache_key)
        if not customer_id:
            continue

        # 插入聊天联系人
        sql = f"""INSERT INTO live_chat_contact (biz_date, streamer_id, customer_id, create_time)
                  VALUES ('{rec['dt']}', {streamer_id}, {customer_id}, sysdate())
                  ON DUPLICATE KEY UPDATE create_time = sysdate();"""
        run_local_sql(sql)

        if (i + 1) % 50 == 0:
            print(f"   已处理 {i + 1}/{len(chat_records)}")

    print(f"   聊天记录完成")

    # 6. 统计
    print("\n=== 迁移完成 ===")
    raw = run_local_sql("""SELECT '主播' AS t, COUNT(*) AS cnt FROM live_streamer
                           UNION ALL SELECT '客户', COUNT(*) FROM live_customer
                           UNION ALL SELECT '打赏记录', COUNT(*) FROM live_gift_record
                           UNION ALL SELECT '聊天联系人', COUNT(*) FROM live_chat_contact;""")
    print(raw)

if __name__ == "__main__":
    main()
