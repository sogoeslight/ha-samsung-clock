"""End-to-end tests inside a real Home Assistant (pytest-homeassistant-custom-component)."""

from datetime import datetime, timedelta

import pytest

pytest.importorskip("pytest_homeassistant_custom_component")

from homeassistant import config_entries  # noqa: E402
from homeassistant.core import HomeAssistant  # noqa: E402
from homeassistant.data_entry_flow import FlowResultType  # noqa: E402
from homeassistant.util import dt as dt_util  # noqa: E402
from pytest_homeassistant_custom_component.common import (  # noqa: E402
    MockConfigEntry,
    async_fire_time_changed,
)

from .test_schedule import PHONE_ALARMS  # noqa: E402

DOMAIN = "samsung_clock_alarm"
PAYLOAD = {
    "alarms": PHONE_ALARMS,
    "complete": True,
    "clock_next": "Fri 13 Nov, 04:31",
    "read_at": "2026-11-12T21:40:00+02:00",
}


async def _setup(hass: HomeAssistant) -> MockConfigEntry:
    await hass.config.async_set_time_zone("Europe/Riga")
    entry = MockConfigEntry(
        domain=DOMAIN, title="Alex", data={"name": "Alex", "webhook_id": "hook123"}
    )
    entry.add_to_hass(hass)
    assert await hass.config_entries.async_setup(entry.entry_id)
    await hass.async_block_till_done()
    return entry


async def test_config_flow_creates_entry_with_webhook(hass: HomeAssistant) -> None:
    result = await hass.config_entries.flow.async_init(
        DOMAIN, context={"source": config_entries.SOURCE_USER}
    )
    assert result["type"] is FlowResultType.FORM
    result = await hass.config_entries.flow.async_configure(result["flow_id"], {"name": "Alex"})
    assert result["step_id"] == "url"
    assert "/api/webhook/" in result["description_placeholders"]["url"]
    result = await hass.config_entries.flow.async_configure(result["flow_id"], {})
    assert result["type"] is FlowResultType.CREATE_ENTRY
    assert result["title"] == "Alex"
    assert len(result["data"]["webhook_id"]) > 10


async def test_webhook_sets_next_alarm_and_rolls_over(
    hass: HomeAssistant, hass_client_no_auth, freezer
) -> None:
    freezer.move_to("2026-11-12T19:40:00+00:00")  # Thu 21:40 Riga
    await _setup(hass)
    state = hass.states.get("sensor.alex_next_alarm")
    assert state.state == "unknown"

    client = await hass_client_no_auth()
    resp = await client.post("/api/webhook/hook123", json=PAYLOAD)
    assert resp.status == 200
    assert (await resp.json())["alarms"] == 6
    await hass.async_block_till_done()

    state = hass.states.get("sensor.alex_next_alarm")
    assert dt_util.parse_datetime(state.state) == datetime.fromisoformat("2026-11-13T04:31:00+02:00")
    assert state.attributes["clock_next"] == "Fri 13 Nov, 04:31"
    assert state.attributes["unparsed"] == []
    assert [u["name"] for u in state.attributes["upcoming"][:3]] == ["", "Work", "Work 2"]

    # After the one-off 04:31 rings, the sensor moves on without any new data.
    freezer.move_to("2026-11-13T02:31:30+00:00")
    async_fire_time_changed(hass, dt_util.utcnow())
    await hass.async_block_till_done()
    state = hass.states.get("sensor.alex_next_alarm")
    assert dt_util.parse_datetime(state.state) == datetime.fromisoformat("2026-11-13T09:30:00+02:00")
    assert state.attributes["alarm_name"] == "Work"


async def test_data_survives_reload(hass: HomeAssistant, hass_client_no_auth, freezer) -> None:
    freezer.move_to("2026-11-12T19:40:00+00:00")
    entry = await _setup(hass)
    client = await hass_client_no_auth()
    await client.post("/api/webhook/hook123", json=PAYLOAD)
    await hass.async_block_till_done()
    assert await hass.config_entries.async_reload(entry.entry_id)
    await hass.async_block_till_done()
    state = hass.states.get("sensor.alex_next_alarm")
    assert dt_util.parse_datetime(state.state) == datetime.fromisoformat("2026-11-13T04:31:00+02:00")


async def test_bad_payload_rejected(hass: HomeAssistant, hass_client_no_auth) -> None:
    await _setup(hass)
    client = await hass_client_no_auth()
    resp = await client.post("/api/webhook/hook123", json={"alarms": [{"time": 5}]})
    assert resp.status == 400
    resp = await client.post("/api/webhook/hook123", data="not json")
    assert resp.status == 400
