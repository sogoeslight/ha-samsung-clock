"""Turn Samsung Clock alarm rows into concrete upcoming datetimes.

Pure Python (no Home Assistant imports) so it can be unit-tested standalone.

Each alarm row comes from the phone app exactly as Samsung Clock shows it
(English UI), e.g.:

    {"name": "Work", "time": "09:30", "enabled": true,
     "schedule": "Every Monday, Every Tuesday, Every Wednesday"}
    {"name": "", "time": "04:31", "enabled": true, "schedule": "Friday, 13 November"}
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, time, timedelta, tzinfo
import re

_WEEKDAYS = {
    name: i
    for i, names in enumerate(
        (
            ("monday", "mon"),
            ("tuesday", "tue", "tues"),
            ("wednesday", "wed"),
            ("thursday", "thu", "thur", "thurs"),
            ("friday", "fri"),
            ("saturday", "sat"),
            ("sunday", "sun"),
        )
    )
    for name in names
}
_MONTHS = {
    name: i
    for i, names in enumerate(
        (
            ("january", "jan"), ("february", "feb"), ("march", "mar"), ("april", "apr"),
            ("may",), ("june", "jun"), ("july", "jul"), ("august", "aug"),
            ("september", "sep", "sept"), ("october", "oct"), ("november", "nov"),
            ("december", "dec"),
        ),
        start=1,
    )
    for name in names
}
_DATE_RE = re.compile(r"^(?:[a-z]+,\s*)?(\d{1,2})\s+([a-z]+)\.?(?:\s+(\d{4}))?$")
_DATE_MDY_RE = re.compile(r"^(?:[a-z]+,\s*)?([a-z]+)\.?\s+(\d{1,2})(?:,?\s+(\d{4}))?$")
_TIME_RE = re.compile(r"^(\d{1,2}):(\d{2})$")


@dataclass(frozen=True)
class Schedule:
    weekdays: frozenset[int] = frozenset()  # 0 = Monday
    on_date: date | None = None


@dataclass(frozen=True)
class Occurrence:
    when: datetime
    name: str
    time: str


def _norm(text: str) -> str:
    return re.sub(r"\s+", " ", text.strip().lower())


def parse_schedule(text: str, today: date) -> Schedule | None:
    """Parse Samsung Clock's schedule text; `today` is the phone's local date when read."""
    s = _norm(text or "")
    if not s:
        return None
    if s in ("every day", "daily"):
        return Schedule(weekdays=frozenset(range(7)))
    if s in ("every weekday", "weekdays"):
        return Schedule(weekdays=frozenset(range(5)))
    if s in ("every weekend", "weekends"):
        return Schedule(weekdays=frozenset({5, 6}))
    if s == "today":
        return Schedule(on_date=today)
    if s == "tomorrow":
        return Schedule(on_date=today + timedelta(days=1))

    parts = [p.strip() for p in s.split(",") if p.strip()]
    if parts and all(p.startswith("every ") for p in parts):
        days = set()
        for p in parts:
            day = _WEEKDAYS.get(p.removeprefix("every ").strip())
            if day is None:
                return None
            days.add(day)
        return Schedule(weekdays=frozenset(days))

    for rx, order in ((_DATE_RE, "dm"), (_DATE_MDY_RE, "md")):
        m = rx.match(s)
        if not m:
            continue
        a, b, year = m.groups()
        day_s, month_s = (a, b) if order == "dm" else (b, a)
        month = _MONTHS.get(month_s)
        if month is None:
            continue
        try:
            if year:
                return Schedule(on_date=date(int(year), month, int(day_s)))
            d = date(today.year, month, int(day_s))
            if d < today:
                d = date(today.year + 1, month, int(day_s))
            return Schedule(on_date=d)
        except ValueError:
            return None
    return None


def _parse_time(text: str) -> time | None:
    m = _TIME_RE.match((text or "").strip())
    if not m:
        return None
    h, mi = int(m[1]), int(m[2])
    if h > 23 or mi > 59:
        return None
    return time(h, mi)


def upcoming(
    alarms: list[dict], now: datetime, read_date: date, days: int = 7
) -> list[Occurrence]:
    """All enabled alarm occurrences in (now, now + days], soonest first.

    `now` must be timezone-aware in the phone's/HA's local zone.
    `read_date` is the local date on which the list was read (resolves "Today"/years).
    """
    tz: tzinfo = now.tzinfo
    horizon = now + timedelta(days=days)
    out: list[Occurrence] = []
    for alarm in alarms:
        if not alarm.get("enabled"):
            continue
        t = _parse_time(alarm.get("time", ""))
        sched = parse_schedule(alarm.get("schedule", ""), read_date)
        if t is None or sched is None:
            continue
        name = alarm.get("name") or ""
        if sched.on_date is not None:
            candidates = [sched.on_date]
        else:
            candidates = [
                now.date() + timedelta(days=i)
                for i in range(days + 1)
                if (now.date() + timedelta(days=i)).weekday() in sched.weekdays
            ]
        for d in candidates:
            when = datetime.combine(d, t, tzinfo=tz)
            if now < when <= horizon:
                out.append(Occurrence(when=when, name=name, time=alarm.get("time", "")))
    out.sort(key=lambda o: o.when)
    return out


def unparsed(alarms: list[dict], read_date: date) -> list[str]:
    """Human-readable list of enabled alarms whose time/schedule could not be understood."""
    bad = []
    for a in alarms:
        if not a.get("enabled"):
            continue
        if _parse_time(a.get("time", "")) is None or parse_schedule(a.get("schedule", ""), read_date) is None:
            bad.append(f"{(a.get('name') or '').strip() or 'alarm'} {a.get('time', '')}: {a.get('schedule', '')}")
    return bad


def _key(alarm: dict) -> tuple:
    return ((alarm.get("name") or "").strip(), alarm.get("time"), _norm(alarm.get("schedule", "")))


def merge(old: list[dict], seen: list[dict], complete: bool) -> list[dict]:
    """Complete list replaces; a partial (scrolled) list only updates/adds the rows it saw."""
    if complete:
        return list(seen)
    by_key = {_key(a): a for a in old}
    for a in seen:
        by_key[_key(a)] = a
    return list(by_key.values())
