import dgram from "dgram";

export function createUdpClient() {
  const client = dgram.createSocket("udp4");

  client.on("message", (msg, rinfo) => {
    console.log(
      `[TS Client] 보드(${rinfo.address}:${rinfo.port}) 응답 수신, ${msg.toString()}`,
    );
  });

  client.on("error", (err) => {
    console.error(`[소켓 에러]: ${err.message}`);
    client.close();
  });

  return client;
}
