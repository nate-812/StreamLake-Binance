import asyncio
import json
import logging

import websockets
from websockets.exceptions import ConnectionClosed

logger = logging.getLogger(__name__)


class BinanceWSClient:
    def __init__(
        self,
        url: str,
        on_message,
        reconnect_base: int = 5,
        reconnect_max: int = 60,
        ping_interval: float = 20,
        ping_timeout: float = 10,
    ):
        self.url = url
        self.on_message = on_message
        self.reconnect_base = reconnect_base
        self.reconnect_max = reconnect_max
        self.ping_interval = ping_interval
        self.ping_timeout = ping_timeout
        self._running = False

    async def start(self) -> None:
        self._running = True
        delay = self.reconnect_base
        while self._running:
            try:
                preview = self.url[:120] + ("..." if len(self.url) > 120 else "")
                logger.info("Connecting to %s", preview)
                async with websockets.connect(
                    self.url,
                    ping_interval=self.ping_interval,
                    ping_timeout=self.ping_timeout,
                    max_size=2**22,
                ) as ws:
                    delay = self.reconnect_base
                    logger.info("Connected.")
                    async for raw_msg in ws:
                        if not self._running:
                            return
                        data = json.loads(raw_msg)
                        await self.on_message(data)

            except ConnectionClosed as e:
                logger.warning("WS closed: %s. Reconnecting in %ss...", e, delay)
            except Exception as e:
                logger.exception("WS error: %s. Reconnecting in %ss...", e, delay)

            if self._running:
                await asyncio.sleep(delay)
                delay = min(delay * 2, self.reconnect_max)

    def stop(self) -> None:
        self._running = False
