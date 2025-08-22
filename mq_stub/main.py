import argparse
import logging
import signal
import sys
from typing import Dict

from .config import load_config, AppConfig
from .connection import MQConnectionManager
from .router import RouteWorker


def _configure_logging(level: str) -> None:
    logging.basicConfig(
        level=getattr(logging, level.upper(), logging.INFO),
        format="%(asctime)s %(levelname)s %(name)s - %(message)s",
    )


def _brokers_to_dict(cfg: AppConfig) -> Dict[str, dict]:
    mapping: Dict[str, dict] = {}
    for b in cfg.brokers:
        mapping[b.name] = {
            "queue_manager": b.queue_manager,
            "channel": b.channel,
            "connection_name": b.connection_name,
            "user": b.user,
            "password": b.password,
            "tls": {
                "enabled": b.tls.enabled,
                "cipher": b.tls.cipher,
                "key_repository": b.tls.key_repository,
                "certificate_label": b.tls.certificate_label,
            },
        }
    return mapping


def run(argv=None) -> int:
    parser = argparse.ArgumentParser(description="IBM MQ routing stub")
    parser.add_argument("--config", required=True, help="Path to config YAML")
    parser.add_argument("--dry-run", action="store_true", help="Parse config and exit")
    args = parser.parse_args(argv)

    app_cfg = load_config(args.config)
    _configure_logging(app_cfg.log_level)

    log = logging.getLogger("mq-stub")

    if args.dry_run:
        log.info("Loaded %d brokers and %d routes", len(app_cfg.brokers), len(app_cfg.routes))
        for r in app_cfg.routes:
            log.info("Route: %s/%s -> %s/%s", r.source.broker, r.source.queue, r.target.broker, r.target.queue)
        return 0

    conn_mgr = MQConnectionManager(_brokers_to_dict(app_cfg))

    workers = [RouteWorker(r, conn_mgr) for r in app_cfg.routes]

    stopping = False

    def _stop(signum, frame):
        nonlocal stopping
        if stopping:
            return
        stopping = True
        log.info("Stopping...")
        for w in workers:
            w.stop()
        for w in workers:
            w.join(timeout=5)
        conn_mgr.close()

    signal.signal(signal.SIGINT, _stop)
    signal.signal(signal.SIGTERM, _stop)

    for w in workers:
        w.start()

    # Wait until all workers finish
    try:
        for w in workers:
            w.join()
    except KeyboardInterrupt:
        _stop(signal.SIGINT, None)

    return 0


if __name__ == "__main__":
    sys.exit(run())