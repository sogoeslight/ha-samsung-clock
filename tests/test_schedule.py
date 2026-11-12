"""Schedule parsing / next-occurrence tests using strings captured from a Galaxy S23 (English UI)."""

from datetime import date, datetime, timedelta, timezone
import importlib.util
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
_spec = importlib.util.spec_from_file_location(
    "scschedule", ROOT / "custom_components" / "samsung_clock_alarm" / "schedule.py"
)
sch = importlib.util.module_from_spec(_spec)
sys.modules["scschedule"] = sch
_spec.loader.exec_module(sch)

RIGA = timezone(timedelta(hours=2))
# Thursday 2026-11-12 21:40 local
NOW = datetime(2026, 11, 12, 21, 40, tzinfo=RIGA)
TODAY = NOW.date()

# Exactly what the phone sent (alarm list of Samsung Clock 12.6).
PHONE_ALARMS = [
    {"name": "", "time": "04:31", "enabled": True, "schedule": "Friday, 13 November"},
    {"name": "Work", "time": "09:30", "enabled": True,
     "schedule": "Every Monday, Every Tuesday, Every Wednesday, Every Thursday, Every Friday"},
    {"name": "Работа", "time": "09:35", "enabled": True,
     "schedule": "Every Monday, Every Tuesday, Every Wednesday, Every Thursday, Every Friday"},
    {"name": "Meeting", "time": "13:55", "enabled": True, "schedule": "Every Wednesday"},
    {"name": "Позвонить ", "time": "20:00", "enabled": False, "schedule": "Every day"},
    {"name": "Полночь-тест", "time": "23:59", "enabled": False, "schedule": "Every day"},
]


def test_parse_weekly():
    s = sch.parse_schedule("Every Monday, Every Tuesday, Every Wednesday, Every Thursday, Every Friday", TODAY)
    assert s == sch.Schedule(weekdays=frozenset({0, 1, 2, 3, 4}))
    assert sch.parse_schedule("Every Wednesday", TODAY).weekdays == frozenset({2})
    assert sch.parse_schedule("Every day", TODAY).weekdays == frozenset(range(7))


def test_parse_dates():
    assert sch.parse_schedule("Friday, 13 November", TODAY) == sch.Schedule(on_date=date(2026, 11, 13))
    assert sch.parse_schedule("Fri, 13 Nov", TODAY).on_date == date(2026, 11, 13)
    # A date earlier in the year means next year.
    assert sch.parse_schedule("Tuesday, 5 January", TODAY).on_date == date(2027, 1, 5)
    assert sch.parse_schedule("Today", TODAY).on_date == TODAY
    assert sch.parse_schedule("Tomorrow", TODAY).on_date == date(2026, 11, 13)


def test_parse_unknown_returns_none():
    assert sch.parse_schedule("Каждый понедельник", TODAY) is None
    assert sch.parse_schedule("", TODAY) is None


def test_next_occurrences_from_phone_list():
    occ = sch.upcoming(PHONE_ALARMS, NOW, TODAY, days=7)
    first = occ[0]
    assert first.when == datetime(2026, 11, 13, 4, 31, tzinfo=RIGA)
    # Friday: 04:31 one-off, then Work 09:30 and Работа 09:35
    assert [(o.when.strftime("%a %H:%M"), o.name) for o in occ[:3]] == [
        ("Fri 04:31", ""), ("Fri 09:30", "Work"), ("Fri 09:35", "Работа"),
    ]
    # Disabled alarms never appear.
    assert all(o.name not in ("Полночь-тест", "Позвонить ") for o in occ)
    # Meeting appears on Wednesday only.
    assert [o.when.date() for o in occ if o.name == "Meeting"] == [date(2026, 11, 18)]


def test_one_off_in_the_past_is_ignored_after_it_rang():
    later = datetime(2026, 11, 13, 5, 0, tzinfo=RIGA)
    occ = sch.upcoming(PHONE_ALARMS, later, TODAY, days=7)
    assert occ[0].when == datetime(2026, 11, 13, 9, 30, tzinfo=RIGA)


def test_weekly_alarm_later_today_and_not_earlier_today():
    alarms = [{"name": "x", "time": "21:45", "enabled": True, "schedule": "Every Thursday"},
              {"name": "y", "time": "21:00", "enabled": True, "schedule": "Every Thursday"}]
    occ = sch.upcoming(alarms, NOW, TODAY, days=8)
    assert [(o.name, o.when) for o in occ] == [
        ("x", datetime(2026, 11, 12, 21, 45, tzinfo=RIGA)),
        ("y", datetime(2026, 11, 19, 21, 0, tzinfo=RIGA)),
        ("x", datetime(2026, 11, 19, 21, 45, tzinfo=RIGA)),
    ]


def test_unparsed_alarms_are_reported():
    alarms = [{"name": "z", "time": "07:00", "enabled": True, "schedule": "Every 2nd Sunday"}]
    assert sch.upcoming(alarms, NOW, TODAY) == []
    assert sch.unparsed(alarms, TODAY) == ["z 07:00: Every 2nd Sunday"]


def test_merge_partial_list_keeps_unseen():
    old = PHONE_ALARMS
    seen = [{"name": "Work", "time": "09:30", "enabled": False,
             "schedule": "Every Monday, Every Tuesday, Every Wednesday, Every Thursday, Every Friday"}]
    merged = sch.merge(old, seen, complete=False)
    assert len(merged) == len(old)
    assert next(a for a in merged if a["name"] == "Work")["enabled"] is False
    assert sch.merge(old, seen, complete=True) == seen
