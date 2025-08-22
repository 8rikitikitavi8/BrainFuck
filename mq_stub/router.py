import logging
import threading
import time
from typing import Optional

from .connection import MQConnectionManager
from .config import RouteConfig


class RouteWorker(threading.Thread):
    def __init__(self, route: RouteConfig, conn_mgr: MQConnectionManager, name: Optional[str] = None):
        super().__init__(name=name or f"route-{route.source.broker}.{route.source.queue}-to-{route.target.broker}.{route.target.queue}", daemon=True)
        self._route = route
        self._conn_mgr = conn_mgr
        self._stop_event = threading.Event()
        self._log = logging.getLogger(self.name)

    def stop(self):
        self._stop_event.set()

    def run(self):
        self._log.info(
            "Starting route: %s/%s -> %s/%s",
            self._route.source.broker,
            self._route.source.queue,
            self._route.target.broker,
            self._route.target.queue,
        )
        while not self._stop_event.is_set():
            try:
                msg = self._conn_mgr.get(
                    self._route.source.broker,
                    self._route.source.queue,
                    self._route.wait_interval_ms,
                )
                if msg is None:
                    time.sleep(self._route.idle_sleep_ms / 1000.0)
                    continue

                self._conn_mgr.put(
                    self._route.target.broker,
                    self._route.target.queue,
                    msg,
                    persistent=True,
                )
            except Exception as e:
                self._log.exception("Route error: %s", e)
                # Backoff a little to avoid tight error loop
                time.sleep(1.0)
        self._log.info("Route stopped")