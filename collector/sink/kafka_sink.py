import logging

from aiokafka import AIOKafkaProducer

logger = logging.getLogger(__name__)


class KafkaSink:
    def __init__(self, cfg: dict):
        self._cfg = cfg
        self._producer: AIOKafkaProducer | None = None

    async def start(self) -> None:
        # 说明：为避免在不同平台频繁踩压缩库依赖（lz4/snappy 等），
        # 这里暂不在客户端启用压缩，保持默认（无压缩）。
        # 如需开启，再显式设置 compression_type 并保证依赖已安装。
        self._producer = AIOKafkaProducer(
            bootstrap_servers=self._cfg["bootstrap_servers"],
            linger_ms=int(self._cfg.get("linger_ms", 50)),
            max_batch_size=int(self._cfg.get("batch_size", 32768)),
        )
        await self._producer.start()
        logger.info("Kafka producer started: %s", self._cfg["bootstrap_servers"])

    async def stop(self) -> None:
        if self._producer is not None:
            await self._producer.stop()
            self._producer = None
            logger.info("Kafka producer stopped.")

    async def send(self, topic: str, value: bytes) -> None:
        if self._producer is None:
            raise RuntimeError("KafkaSink not started")
        await self._producer.send_and_wait(topic, value)
