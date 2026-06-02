from importlib import import_module
import sys


def test_gateway_package_loads_on_supported_python() -> None:
    package = import_module("app")

    assert package.__name__ == "app"
    assert sys.version_info >= (3, 11)
