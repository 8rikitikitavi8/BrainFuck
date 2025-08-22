# IBM MQ Routing Stub

A minimal, configurable IBM MQ "stub" that listens on queues and forwards messages to other queues, across one or multiple brokers. Supports TLS and non-TLS connections.

## Features
- Configure multiple brokers (TLS/non-TLS)
- Define routes: source broker/queue -> target broker/queue
- Concurrent workers, graceful shutdown

## Requirements
- Python 3.9+
- `pymqi` and IBM MQ Client libraries installed on the host
- For TLS: key repository and cipher suite configured on the client and MQ side

## Install
```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

IBM MQ Client libraries are required for `pymqi`. On Linux, install MQ Client (e.g., MQ 9.3) and export library paths (`LD_LIBRARY_PATH`) accordingly.

## Configuration
See `config.example.yaml` for a full example.

Fields:
- `brokers[]`: name, queue_manager, channel, connection_name, user, password, tls
- `routes[]`: from/source {broker, queue}, to/target {broker, queue}, wait_interval_ms, idle_sleep_ms
- `log_level`: DEBUG/INFO/WARN/ERROR

## Run
```bash
python -m mq_stub.main --config ./config.yaml
```

Dry run (validate config and print routes):
```bash
python -m mq_stub.main --config ./config.yaml --dry-run
```

## Notes
- TLS ciphers and key repository must match your MQ server configuration.
- This tool forwards payload bytes as-is and does not transform headers/properties.
