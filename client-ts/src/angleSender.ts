import type { Socket } from "dgram";
import { TARGET_IP, TARGET_PORT, RESPONSE_TIMEOUT_MS } from "./config";

const angles = ["0", "45", "90", "135", "180", "135", "90", "45"];
const PROBE_ANGLE = angles[0];

export function createAngleSender(client: Socket, onResponseTimeout?: (angleStr: string) => void) {
  let currentIndex = 0;
  let responseTimer: NodeJS.Timeout | null = null;

  function clearResponseTimer() {
    if (responseTimer) {
      clearTimeout(responseTimer);
      responseTimer = null;
    }
  }

  function notifyResponseReceived() {
    clearResponseTimer();
  }

  function send(angleStr: string) {
    const message = Buffer.from(angleStr);

    console.log(`\n[TS Client] 모터 각도 전송 명령, ${angleStr}도`);

    client.send(message, TARGET_PORT, TARGET_IP, (err) => {
      if (err) {
        console.error("[송신 실패]:", err);
        return;
      }

      responseTimer = setTimeout(() => {
        responseTimer = null;
        console.warn(
          `[응답 타임아웃] ${angleStr}도 명령에 대한 보드 응답이 ${RESPONSE_TIMEOUT_MS}ms 내에 없습니다. 하드웨어 연결을 확인하세요.`,
        );
        onResponseTimeout?.(angleStr);
      }, RESPONSE_TIMEOUT_MS);
    });
  }

  function sendProbe() {
    send(PROBE_ANGLE);
  }

  function sendAngleCommand() {
    send(angles[currentIndex]);
    currentIndex = (currentIndex + 1) % angles.length;
  }

  return { sendProbe, sendAngleCommand, notifyResponseReceived };
}
