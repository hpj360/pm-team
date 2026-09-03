"""store: 幂等 upsert（AC-1/2）+ 只读连接 + 信号存取。"""

from datetime import datetime, timedelta

from conftest import make_bars

from quant.data.store import Store


def test_upsert_bars_idempotent(store):
    df = make_bars(10)
    n1 = store.upsert_bars("cn:600519.SH", df, adj_type="qfq")
    n2 = store.upsert_bars("cn:600519.SH", df, adj_type="qfq")
    count = store.conn.execute("SELECT count(*) FROM bars").fetchone()[0]
    assert (n1, n2, count) == (10, 10, 10)  # 重复执行不产生重复行（AC-1）


def test_adj_type_metadata(store):
    store.upsert_bars("cn:600519.SH", make_bars(3), adj_type="qfq")
    adj = store.conn.execute("SELECT DISTINCT adj_type FROM bars").fetchone()[0]
    assert adj == "qfq"  # 复权口径元数据落库


def test_readonly_connection(store, tmp_path):
    store.upsert_bars("crypto:BTC-USDT", make_bars(5))
    db_path = store.path
    store.close()  # duckdb 同进程不允许同库读写/只读双连接，先关写连接
    ro = Store.open_readonly(db_path)
    try:
        df = ro.get_bars("crypto:BTC-USDT")
        assert len(df) == 5
    finally:
        ro.close()


def test_signal_roundtrip(store):
    now = datetime.now()
    sid = store.add_signal(now, "600519.SH", "cn", "close", "<", 1500, 1499.0, "buy", "r1")
    recent = store.recent_signals("600519.SH", "buy", now - timedelta(hours=1))
    assert sid >= 1 and len(recent) == 1
    empty = store.recent_signals("600519.SH", "buy", now + timedelta(hours=1))
    assert empty.empty


def test_navs_upsert_idempotent(store):
    from conftest import make_navs
    df = make_navs([1.0, 1.01, 1.02])
    store.upsert_navs("fund:000001.OF", df)
    store.upsert_navs("fund:000001.OF", df)
    count = store.conn.execute("SELECT count(*) FROM navs").fetchone()[0]
    assert count == 3
