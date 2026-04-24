from decimal import Decimal

from pydantic import BaseModel, ConfigDict, Field


class TradeEvent(BaseModel):
    """Binance @trade combined-stream 消息，Kafka JSON 与 Flink TradeEvent（camelCase）对齐。"""

    model_config = ConfigDict(populate_by_name=True)

    stream: str
    symbol: str
    event_time: int = Field(alias="eventTime")
    time: int
    trade_id: int = Field(alias="tradeId")
    price: str
    qty: str
    quote_qty: str = Field(alias="quoteQty")
    is_buyer_maker: bool = Field(alias="isBuyerMaker")

    @classmethod
    def from_raw(cls, data: dict) -> "TradeEvent":
        d = data["data"]
        price, qty = d["p"], d["q"]
        quote = str(Decimal(price) * Decimal(qty))
        ts = int(d["T"])
        return cls(
            stream=data["stream"],
            symbol=d["s"],
            event_time=ts,
            time=ts,
            trade_id=int(d["t"]),
            price=price,
            qty=qty,
            quote_qty=quote,
            is_buyer_maker=bool(d["m"]),
        )
