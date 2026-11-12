"""Render the blueprint's trigger template inside Home Assistant."""

from pathlib import Path

import pytest

pytest.importorskip("pytest_homeassistant_custom_component")

from homeassistant.core import HomeAssistant  # noqa: E402
from homeassistant.helpers.template import Template  # noqa: E402
from homeassistant.util.yaml import loader as yaml_loader  # noqa: E402

BP = Path(__file__).resolve().parents[1] / "blueprints/automation/samsung_clock_alarm/before_first_alarm.yaml"


def _trigger_template() -> str:
    data = yaml_loader.load_yaml(BP)
    return data["triggers"][0]["value_template"]


async def _render(hass: HomeAssistant, lead: int) -> bool:
    tpl = Template(_trigger_template(), hass)
    return tpl.async_render(
        {
            "sensors": ["sensor.alex_next_alarm", "sensor.girl_next_alarm"],
            "lead": lead,
            "window_start": "04:00:00",
            "window_end": "11:00:00",
        }
    )


@pytest.fixture
async def phones(hass: HomeAssistant):
    await hass.config.async_set_time_zone("Europe/Riga")
    hass.states.async_set(
        "sensor.alex_next_alarm",
        "2026-11-13T07:30:00+00:00",
        {"upcoming": [
            {"time": "2026-11-13T09:30:00+02:00", "name": "Work"},
            {"time": "2026-11-13T13:55:00+02:00", "name": "Meeting"},
        ]},
    )
    hass.states.async_set(
        "sensor.girl_next_alarm",
        "2026-11-13T05:15:00+00:00",
        {"upcoming": [
            {"time": "2026-11-13T03:30:00+02:00", "name": "too early"},
            {"time": "2026-11-13T07:15:00+02:00", "name": "Uni"},
        ]},
    )


async def test_fires_lead_minutes_before_earliest_morning_alarm(hass, phones, freezer) -> None:
    # Earliest in-window alarm is 07:15 (03:30 is outside 04:00-11:00). Lead 60 -> 06:15.
    freezer.move_to("2026-11-13T04:14:00+00:00")  # 06:14 Riga
    assert await _render(hass, 60) is False
    freezer.move_to("2026-11-13T04:15:00+00:00")  # 06:15
    assert await _render(hass, 60) is True
    freezer.move_to("2026-11-13T05:14:00+00:00")  # 07:14
    assert await _render(hass, 60) is True
    freezer.move_to("2026-11-13T05:15:00+00:00")  # 07:15 alarm rings
    assert await _render(hass, 60) is False


async def test_ignores_missing_sensors(hass, freezer) -> None:
    freezer.move_to("2026-11-13T04:15:00+00:00")
    assert await _render(hass, 30) is False
