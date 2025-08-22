from __future__ import annotations

import threading
from dataclasses import dataclass
from typing import Dict, Optional, Tuple


@dataclass
class _ConnectionEntry:
    qmgr: object  # pymqi.QueueManager, kept generic to avoid hard import
    cd: object
    sco: Optional[object]


class MQConnectionError(Exception):
    pass


class MQConnectionManager:
    def __init__(self, broker_configs: Dict[str, dict]):
        self._broker_configs = broker_configs
        self._lock = threading.RLock()
        self._connections: Dict[str, _ConnectionEntry] = {}

    def _get_pymqi(self):
        try:
            import pymqi  # type: ignore
            return pymqi
        except Exception as e:
            raise MQConnectionError(
                "pymqi is not available. Install dependencies and IBM MQ client libraries."
            ) from e

    def _connect(self, alias: str) -> _ConnectionEntry:
        cfg = self._broker_configs[alias]
        pymqi = self._get_pymqi()

        cd = pymqi.CD()
        cd.ChannelName = cfg["channel"].encode()
        cd.ConnectionName = cfg["connection_name"].encode()

        sco = None
        tls_cfg = cfg.get("tls") or {}
        if tls_cfg.get("enabled"):
            sco = pymqi.SCO()
            cipher = tls_cfg.get("cipher")
            if not cipher:
                raise MQConnectionError(f"TLS enabled for broker '{alias}' but 'cipher' not set")
            cd.SSLCipherSpec = cipher.encode()
            key_repo = tls_cfg.get("key_repository")
            if key_repo:
                sco.KeyRepository = key_repo
            cert_label = tls_cfg.get("certificate_label")
            if cert_label:
                try:
                    sco.CertificateLabel = cert_label
                except AttributeError:
                    # Older pymqi may not support CertificateLabel attribute
                    pass

        qmgr = pymqi.QueueManager(None)
        user = cfg.get("user")
        password = cfg.get("password")
        try:
            qmgr.connect_with_options(
                cfg["queue_manager"], cd=cd, sco=sco, user=user, password=password
            )
        except Exception as e:
            raise MQConnectionError(f"Failed to connect to broker '{alias}': {e}") from e

        return _ConnectionEntry(qmgr=qmgr, cd=cd, sco=sco)

    def get_connection(self, alias: str) -> _ConnectionEntry:
        with self._lock:
            entry = self._connections.get(alias)
            if entry is not None:
                return entry
            entry = self._connect(alias)
            self._connections[alias] = entry
            return entry

    def get(self, alias: str, queue_name: str, wait_interval_ms: int) -> Optional[bytes]:
        pymqi = self._get_pymqi()
        entry = self.get_connection(alias)
        open_options = pymqi.CMQC.MQOO_INPUT_AS_Q_DEF | pymqi.CMQC.MQOO_FAIL_IF_QUIESCING
        queue = pymqi.Queue(entry.qmgr, queue_name, open_options)
        try:
            md = pymqi.MD()
            gmo = pymqi.GMO()
            gmo.Options = (
                pymqi.CMQC.MQGMO_NO_SYNCPOINT
                | pymqi.CMQC.MQGMO_WAIT
                | pymqi.CMQC.MQGMO_PROPERTIES_IN_HANDLE
                | pymqi.CMQC.MQGMO_CONVERT
                | pymqi.CMQC.MQGMO_FAIL_IF_QUIESCING
            )
            gmo.WaitInterval = wait_interval_ms
            try:
                message = queue.get(md, gmo)
                return message
            except pymqi.MQMIError as e:
                if e.comp == pymqi.CMQC.MQCC_FAILED and e.reason == pymqi.CMQC.MQRC_NO_MSG_AVAILABLE:
                    return None
                raise
        finally:
            queue.close()

    def put(self, alias: str, queue_name: str, payload: bytes, persistent: bool = True) -> None:
        pymqi = self._get_pymqi()
        entry = self.get_connection(alias)
        open_options = pymqi.CMQC.MQOO_OUTPUT | pymqi.CMQC.MQOO_FAIL_IF_QUIESCING
        queue = pymqi.Queue(entry.qmgr, queue_name, open_options)
        try:
            md = pymqi.MD()
            pmo = pymqi.PMO()
            pmo.Options = pymqi.CMQC.MQPMO_NO_SYNCPOINT | pymqi.CMQC.MQPMO_FAIL_IF_QUIESCING
            if persistent:
                md.Persistence = pymqi.CMQC.MQPER_PERSISTENT
            queue.put(payload, md, pmo)
        finally:
            queue.close()

    def close(self):
        with self._lock:
            for alias, entry in list(self._connections.items()):
                try:
                    entry.qmgr.disconnect()
                except Exception:
                    pass
            self._connections.clear()