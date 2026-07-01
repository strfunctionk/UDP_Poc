import { createUdpClient } from "./udpClient";
import { createAngleSender } from "./angleSender";
import { createConnectionManager } from "./connectionManager";

const client = createUdpClient();

const { sendProbe, sendAngleCommand, notifyResponseReceived } = createAngleSender(client, () =>
  connection.handleTimeout(),
);

const connection = createConnectionManager(sendProbe, sendAngleCommand);

client.on("message", () => {
  notifyResponseReceived();
  connection.handleResponse();
});

console.log("[UDP PoC] UDP Client started");
connection.startProbing();
