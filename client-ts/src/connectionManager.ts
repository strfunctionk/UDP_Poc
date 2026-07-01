import { PROBE_INTERVAL_MS } from "./config";

const MAIN_INTERVAL_MS = 2000;

export function createConnectionManager(sendProbe: () => void, sendCommand: () => void) {
  let connected = false;
  let mainInterval: NodeJS.Timeout | null = null;
  let probeInterval: NodeJS.Timeout | null = null;

  function stopProbing() {
    if (probeInterval) {
      clearInterval(probeInterval);
      probeInterval = null;
    }
  }

  function startProbing() {
    if (probeInterval) return;
    console.log("[UDP PoC] 보드 연결 대기 중...");
    sendProbe();
    probeInterval = setInterval(sendProbe, PROBE_INTERVAL_MS);
  }

  function handleResponse() {
    if (connected) return;
    connected = true;
    console.log("[TS Client] 보드 연결 확인됨. 명령 전송을 시작합니다.");
    stopProbing();
    mainInterval = setInterval(sendCommand, MAIN_INTERVAL_MS);
  }

  function handleTimeout() {
    if (!connected) return;
    connected = false;
    console.warn("[TS Client] 보드 응답 끊김. 명령 전송을 중단하고 재연결을 시도합니다.");
    if (mainInterval) {
      clearInterval(mainInterval);
      mainInterval = null;
    }
    startProbing();
  }

  return { startProbing, handleResponse, handleTimeout };
}
