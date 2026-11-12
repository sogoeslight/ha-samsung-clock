"""Next Samsung Clock alarm sensor (one per phone)."""

from __future__ import annotations

from datetime import date, datetime, timedelta

from homeassistant.components.sensor import SensorDeviceClass, SensorEntity
from homeassistant.core import CALLBACK_TYPE, HomeAssistant, callback
from homeassistant.helpers.device_registry import DeviceInfo
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity_platform import AddConfigEntryEntitiesCallback
from homeassistant.helpers.event import async_track_point_in_time
from homeassistant.util import dt as dt_util

from . import SamsungClockConfigEntry
from .const import DOMAIN, SIGNAL_UPDATED, UPCOMING_DAYS
from .schedule import unparsed, upcoming


async def async_setup_entry(
    hass: HomeAssistant,
    entry: SamsungClockConfigEntry,
    async_add_entities: AddConfigEntryEntitiesCallback,
) -> None:
    async_add_entities([NextAlarmSensor(entry)])


class NextAlarmSensor(SensorEntity):
    """Timestamp of the next enabled Samsung Clock alarm; unknown when none."""

    _attr_has_entity_name = True
    _attr_translation_key = "next_alarm"
    _attr_device_class = SensorDeviceClass.TIMESTAMP
    _attr_should_poll = False

    def __init__(self, entry: SamsungClockConfigEntry) -> None:
        self._entry = entry
        self._attr_unique_id = f"{entry.entry_id}_next_alarm"
        self._attr_device_info = DeviceInfo(
            identifiers={(DOMAIN, entry.entry_id)},
            name=entry.title,
            manufacturer="Samsung",
            model="Samsung Clock",
        )
        self._unsub_timer: CALLBACK_TYPE | None = None

    async def async_added_to_hass(self) -> None:
        self.async_on_remove(
            async_dispatcher_connect(
                self.hass, SIGNAL_UPDATED.format(self._entry.entry_id), self._recompute
            )
        )
        self.async_on_remove(self._cancel_timer)
        self._recompute()

    @callback
    def _cancel_timer(self) -> None:
        if self._unsub_timer:
            self._unsub_timer()
            self._unsub_timer = None

    def _read_date(self, now: datetime) -> date:
        read_at = self._entry.runtime_data.read_at
        if read_at and (parsed := dt_util.parse_datetime(read_at)):
            return dt_util.as_local(parsed).date()
        return now.date()

    @callback
    def _recompute(self, *_args) -> None:
        data = self._entry.runtime_data
        now = dt_util.now()
        read_date = self._read_date(now)
        occ = upcoming(data.alarms, now, read_date, days=UPCOMING_DAYS)
        self._attr_native_value = occ[0].when if occ else None
        self._attr_extra_state_attributes = {
            "alarm_name": occ[0].name if occ else None,
            "upcoming": [{"time": o.when.isoformat(), "name": o.name} for o in occ],
            "alarms": data.alarms,
            "clock_next": data.clock_next,
            "read_at": data.read_at,
            "received_at": data.received_at,
            "unparsed": unparsed(data.alarms, read_date),
        }
        # Re-evaluate right after the current next alarm passes; with nothing
        # upcoming, re-check daily so the 7-day window keeps moving.
        self._cancel_timer()
        wake = occ[0].when if occ else dt_util.start_of_local_day(now) + timedelta(days=1)
        self._unsub_timer = async_track_point_in_time(self.hass, self._timer_fired, wake)
        self.async_write_ha_state()

    @callback
    def _timer_fired(self, _now: datetime) -> None:
        self._unsub_timer = None
        self._recompute()
