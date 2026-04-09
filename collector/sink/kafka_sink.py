import logging

from aiokafka import AIOKafkaProducer

logger = logging.getLogger(__name__)


class KafkaSink:
    def __init__(self, cfg: dict):
        self._cfg = cfg
        self._producer: AIOKafkaProducer | None = None

    async def start(self) -> None:
        compression_type = self._cfg.get("compression_type")
        try:
            self._producer = AIOKafkaProducer(
                bootstrap_servers=self._cfg["bootstrap_servers"],
                linger_ms=int(self._cfg.get("linger_ms", 50)),
                compression_type=compression_type,
                max_batch_size=int(self._cfg.get("batch_size", 32768)),
            )
        except RuntimeError as e:
            if compression_type:
                raise RuntimeError(
                    f"Kafka compression '{compression_type}' unavailable. "
                    "Install the matching library in this venv "
                    "(e.g. lz4/snappy/zstandard) or clear compression_type in config.yaml."
                ) from e
            raise
        await self._producer.start()
        logger.info(
            "Kafka producer started: %s (compression=%s)",
            self._cfg["bootstrap_servers"],
            compression_type or "none",
        )

    async def stop(self) -> None:
        if self._producer is not None:
            await self._producer.stop()
            self._producer = None
            logger.info("Kafka producer stopped.")

    async def send(self, topic: str, value: bytes) -> None:
        if self._producer is None:
            raise RuntimeError("KafkaSink not started")
        await self._producer.send_and_wait(topic, value)
