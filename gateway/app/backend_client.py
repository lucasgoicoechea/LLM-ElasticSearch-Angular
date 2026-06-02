import httpx


class BackendUnavailableError(RuntimeError):
    pass


class BackendClient:
    def __init__(
        self,
        *,
        base_url: str,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self._base_url = base_url
        self._transport = transport

    async def request(
        self,
        method: str,
        path: str,
        *,
        json: dict[str, str] | None = None,
    ) -> httpx.Response:
        try:
            async with httpx.AsyncClient(
                base_url=self._base_url,
                transport=self._transport,
                timeout=5.0,
            ) as client:
                return await client.request(method, path, json=json)
        except httpx.RequestError as exception:
            raise BackendUnavailableError from exception
