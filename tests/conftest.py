"""Test fixtures."""
import pytest

# Import our package before HA puts its testing_config/custom_components on sys.path.
import custom_components  # noqa: F401


@pytest.fixture(autouse=True)
def auto_enable_custom_integrations(enable_custom_integrations):
    """Load custom_components/ in every test."""
    yield
