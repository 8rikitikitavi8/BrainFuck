import dataclasses
from dataclasses import dataclass, field
from typing import List, Optional, Dict, Any
import yaml


@dataclass
class TLSConfig:
    enabled: bool = False
    cipher: Optional[str] = None
    key_repository: Optional[str] = None  # Path without .kdb suffix
    certificate_label: Optional[str] = None


@dataclass
class BrokerConfig:
    name: str
    queue_manager: str
    channel: str
    connection_name: str  # e.g. "host(1414)" or "host1(1414),host2(1414)"
    user: Optional[str] = None
    password: Optional[str] = None
    tls: TLSConfig = field(default_factory=TLSConfig)


@dataclass
class RouteEndpoint:
    broker: str
    queue: str


@dataclass
class RouteConfig:
    source: RouteEndpoint
    target: RouteEndpoint
    dead_letter_queue: Optional[str] = None
    wait_interval_ms: int = 2000
    idle_sleep_ms: int = 500


@dataclass
class AppConfig:
    brokers: List[BrokerConfig]
    routes: List[RouteConfig]
    log_level: str = "INFO"


class ConfigError(Exception):
    pass


def _parse_tls(tls_dict: Optional[Dict[str, Any]]) -> TLSConfig:
    if not tls_dict:
        return TLSConfig(enabled=False)
    return TLSConfig(
        enabled=bool(tls_dict.get("enabled", False)),
        cipher=tls_dict.get("cipher"),
        key_repository=tls_dict.get("key_repository"),
        certificate_label=tls_dict.get("certificate_label"),
    )


def load_config(path: str) -> AppConfig:
    with open(path, "r", encoding="utf-8") as f:
        raw = yaml.safe_load(f) or {}

    if "brokers" not in raw or not isinstance(raw["brokers"], list) or not raw["brokers"]:
        raise ConfigError("'brokers' section must be a non-empty list")
    if "routes" not in raw or not isinstance(raw["routes"], list) or not raw["routes"]:
        raise ConfigError("'routes' section must be a non-empty list")

    brokers: List[BrokerConfig] = []
    seen_names = set()
    for b in raw["brokers"]:
        try:
            name = b["name"]
            if name in seen_names:
                raise ConfigError(f"Duplicate broker name: {name}")
            seen_names.add(name)
            broker = BrokerConfig(
                name=name,
                queue_manager=b["queue_manager"],
                channel=b["channel"],
                connection_name=b["connection_name"],
                user=b.get("user"),
                password=b.get("password"),
                tls=_parse_tls(b.get("tls")),
            )
        except KeyError as e:
            raise ConfigError(f"Missing broker field: {e}") from e
        brokers.append(broker)

    routes: List[RouteConfig] = []
    for r in raw["routes"]:
        try:
            src = r["from"] if "from" in r else r["source"]
            dst = r["to"] if "to" in r else r["target"]
            route = RouteConfig(
                source=RouteEndpoint(broker=src["broker"], queue=src["queue"]),
                target=RouteEndpoint(broker=dst["broker"], queue=dst["queue"]),
                dead_letter_queue=r.get("dead_letter_queue"),
                wait_interval_ms=int(r.get("wait_interval_ms", 2000)),
                idle_sleep_ms=int(r.get("idle_sleep_ms", 500)),
            )
        except KeyError as e:
            raise ConfigError(f"Missing route field: {e}") from e
        routes.append(route)

    log_level = str(raw.get("log_level", "INFO")).upper()

    # basic referential integrity check
    broker_names = {b.name for b in brokers}
    for route in routes:
        if route.source.broker not in broker_names:
            raise ConfigError(f"Route references unknown source broker '{route.source.broker}'")
        if route.target.broker not in broker_names:
            raise ConfigError(f"Route references unknown target broker '{route.target.broker}'")

    return AppConfig(brokers=brokers, routes=routes, log_level=log_level)