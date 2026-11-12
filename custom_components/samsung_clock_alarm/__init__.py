"""Samsung Clock Alarm: receives the Samsung Clock alarm list from the phone app via webhook."""

from __future__ import annotations

from dataclasses import dataclass, field
import logging
from typing import Any

from aiohttp import web

from homeassistant.components import webhook
from homeassistant.config_entries import ConfigEntry
from homeassistant.const import Platform
from homeassistant.core import HomeAssistant
from homeassistant.helpers.dispatcher import async_dispatcher_send
from homeassistant.helpers.storage import Store
from homeassistant.util import dt as dt_util

from .const import CONF_WEBHOOK_ID, DOMAIN, SIGNAL_UPDATED, STORAGE_VERSION
from .schedule import merge

_LOGGER = logging.getLogger(__name__)
PLATFORMS = [Platform.SENSOR]


@dataclass
class PhoneData:
    """Last alarm list received from one phone."""

    store: Store
    alarms: list[dict] = field(default_factory=list)
    clock_next: str | None = None
    read_at: str | None = None  # ISO timestamp from the phone
    received_at: str | None = None


type SamsungClockConfigEntry = ConfigEntry[PhoneData]


def _valid_alarm(a: Any) -> bool:
    return (
        isinstance(a, dict)
        and isinstance(a.get("time"), str)
        and isinstance(a.get("enabled"), bool)
        and isinstance(a.get("schedule", ""), str)
        and isinstance(a.get("name", ""), str)
    )


def _str_or_none(value: Any) -> str | None:
    return value if isinstance(value, str) else None


async def async_setup_entry(hass: HomeAssistant, entry: SamsungClockConfigEntry) -> bool:
    store = Store(hass, STORAGE_VERSION, f"{DOMAIN}.{entry.entry_id}")
    data = PhoneData(store=store)
    if saved := await store.async_load():
        data.alarms = saved.get("alarms", [])
        data.clock_next = saved.get("clock_next")
        data.read_at = saved.get("read_at")
        data.received_at = saved.get("received_at")
    entry.runtime_data = data

    async def handle_webhook(
        hass: HomeAssistant, webhook_id: str, request: web.Request
    ) -> web.Response:
        try:
            payload = await request.json()
        except ValueError:
            return web.json_response({"ok": False, "error": "invalid json"}, status=400)
        alarms = payload.get("alarms") if isinstance(payload, dict) else None
        if not isinstance(alarms, list) or not all(_valid_alarm(a) for a in alarms):
            return web.json_response({"ok": False, "error": "invalid alarms"}, status=400)

        data.alarms = merge(data.alarms, alarms, complete=bool(payload.get("complete", True)))
        data.clock_next = _str_or_none(payload.get("clock_next"))
        data.read_at = _str_or_none(payload.get("read_at"))
        data.received_at = dt_util.utcnow().isoformat()
        await store.async_save(
            {
                "alarms": data.alarms,
                "clock_next": data.clock_next,
                "read_at": data.read_at,
                "received_at": data.received_at,
            }
        )
        _LOGGER.debug("%s: received %d alarms", entry.title, len(alarms))
        async_dispatcher_send(hass, SIGNAL_UPDATED.format(entry.entry_id))
        return web.json_response({"ok": True, "alarms": len(data.alarms)})

    webhook.async_register(
        hass,
        DOMAIN,
        f"Samsung Clock: {entry.title}",
        entry.data[CONF_WEBHOOK_ID],
        handle_webhook,
        local_only=False,
        allowed_methods=["POST"],
    )
    entry.async_on_unload(lambda: webhook.async_unregister(hass, entry.data[CONF_WEBHOOK_ID]))
    await hass.config_entries.async_forward_entry_setups(entry, PLATFORMS)
    return True


async def async_unload_entry(hass: HomeAssistant, entry: SamsungClockConfigEntry) -> bool:
    return await hass.config_entries.async_unload_platforms(entry, PLATFORMS)


async def async_remove_entry(hass: HomeAssistant, entry: ConfigEntry) -> None:
    await Store(hass, STORAGE_VERSION, f"{DOMAIN}.{entry.entry_id}").async_remove()
