"""Config flow: one entry per phone, each with its own webhook."""

from __future__ import annotations

from typing import Any

import voluptuous as vol

from homeassistant.components import webhook
from homeassistant.config_entries import ConfigFlow, ConfigFlowResult
from homeassistant.const import CONF_NAME
from homeassistant.helpers.network import NoURLAvailableError

from .const import CONF_WEBHOOK_ID, DOMAIN


class SamsungClockConfigFlow(ConfigFlow, domain=DOMAIN):
    VERSION = 1

    def __init__(self) -> None:
        self._name = ""
        self._webhook_id = ""

    async def async_step_user(self, user_input: dict[str, Any] | None = None) -> ConfigFlowResult:
        if user_input is not None:
            self._name = user_input[CONF_NAME].strip()
            self._webhook_id = webhook.async_generate_id()
            return await self.async_step_url()
        return self.async_show_form(
            step_id="user", data_schema=vol.Schema({vol.Required(CONF_NAME): str})
        )

    async def async_step_url(self, user_input: dict[str, Any] | None = None) -> ConfigFlowResult:
        if user_input is not None:
            return self.async_create_entry(
                title=self._name,
                data={CONF_NAME: self._name, CONF_WEBHOOK_ID: self._webhook_id},
            )
        try:
            url = webhook.async_generate_url(
                self.hass, self._webhook_id, allow_external=True, allow_ip=True
            )
        except NoURLAvailableError:
            url = f"http://<home-assistant>:8123/api/webhook/{self._webhook_id}"
        return self.async_show_form(
            step_id="url",
            data_schema=vol.Schema({}),
            description_placeholders={"url": url},
        )
