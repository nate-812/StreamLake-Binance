from __future__ import annotations

import asyncio
import json
import logging
from pathlib import Path

import yaml

from binance.client import BinanceWSClient
from binance.models import TradeEvent
from binance.stream_manager import build_all_stream_paths, chunk_streams
from sink.kafka_sink import KafkaSink

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)
logger = logging.getLogger("main")


def _load_config() -> dict:
    cfg_path = Path(__file__).resolve().parent / "config.yaml"
    with cfg_path.open(encoding="utf-8") as f:
        return yaml.safe_load(f)


def main() -> None:
    asyncio.run(_async_main())


async def _async_main() -> None:
    cfg = _load_config()
    bcfg = cfg["binance"]
    kcfg = cfg["kafka"]

    kafka_sink = KafkaSink(kcfg)
    await kafka_sink.start()
    logger.info("Kafka topic(trade)=%s topic(depth)=%s", kcfg.get("topic_trade"), kcfg.get("topic_depth"))

    stream_paths = build_all_stream_paths(bcfg["symbols"], bcfg["streams"])
    chunks = chunk_streams(stream_paths, int(bcfg["max_streams_per_conn"]))
    base_url = bcfg["ws_base_url"].rstrip("/")

    clients: list[BinanceWSClient] = []
    tasks: list[asyncio.Task[None]] = []
    trade_count = 0
    depth_count = 0

    try:
        for chunk in chunks:
            params = "/".join(chunk)
            url = f"{base_url}?streams={params}"
            logger.info("WS shard: %d streams", len(chunk))

            async def on_message(data: dict, *, _kcfg=kcfg, _topics=bcfg["streams"]) -> None:
                nonlocal trade_count, depth_count
                if "stream" not in data:
                    return
                stream_name = data["stream"]
                if stream_name.endswith("@trade"):
                    try:
                        event = TradeEvent.from_raw(data)
                        payload = event.model_dump_json(by_alias=True).encode("utf-8")
                        await kafka_sink.send(_kcfg["topic_trade"], payload)
                        trade_count += 1
                        if trade_count % 200 == 0:
                            logger.info("trade sent: %d", trade_count)
                    except Exception:
                        logger.exception("send trade failed, stream=%s", stream_name)
                elif "@depth" in stream_name:
                    if "depth100ms" not in _topics:
                        return
                    try:
                        payload = json.dumps(data, separators=(",", ":")).encode("utf-8")
                        await kafka_sink.send(_kcfg["topic_depth"], payload)
                        depth_count += 1
                        if depth_count % 200 == 0:
                            logger.info("depth sent: %d", depth_count)
                    except Exception:
                        logger.exception("send depth failed, stream=%s", stream_name)

            client = BinanceWSClient(
                url=url,
                on_message=on_message,
                reconnect_base=int(bcfg["reconnect_delay_base"]),
                reconnect_max=int(bcfg["reconnect_delay_max"]),
                ping_interval=float(bcfg.get("ping_interval", 20)),
            )
            clients.append(client)
            tasks.append(asyncio.create_task(client.start(), name=f"ws-{len(tasks)}"))

        logger.info("Started %d WebSocket shard(s).", len(tasks))
        await asyncio.gather(*tasks)
    except asyncio.CancelledError:
        raise
    except KeyboardInterrupt:
        logger.info("Shutting down (KeyboardInterrupt)...")
    finally:
        for c in clients:
            c.stop()
        for t in tasks:
            t.cancel()
        if tasks:
            await asyncio.gather(*tasks, return_exceptions=True)
        await kafka_sink.stop()


if __name__ == "__main__":
    main()
