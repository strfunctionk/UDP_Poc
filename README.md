# UDP Poc

ESP8266(ESP32 전환 예정) 보드에 UDP로 각도 명령을 보내 서보 모터를 제어하는 PoC 프로젝트입니다. 같은 프로토콜을 쓰는 클라이언트를 TypeScript와 Kotlin 두 가지로 구현해뒀습니다.

- `client-ts` - 검증용 (익숙한 언어로 먼저 로직 검증)
- `client-kts` - 실제 개발용 PoC (추후 안드로이드 Kotlin 이식 대비)

## 구성

- `firmware-esp32/` - 보드 펌웨어 (PlatformIO, 실제로는 ESP8266 사용 중)
- `client-ts/` - TypeScript UDP 클라이언트 (검증용)
- `client-kts/` - Kotlin 스크립트(`.kts`) UDP 클라이언트 (실제 개발 대비 PoC)

## 동작 방식

1. 클라이언트가 켜지면 보드가 응답할 때까지 `0도` 프로브 명령을 짧은 간격(`PROBE_INTERVAL_MS`)으로 반복 전송합니다.
2. 보드가 `MOVED`/`INVALID_ANGLE` 응답을 보내면 연결된 것으로 간주하고, 이후 `0 → 45 → 90 → 135 → 180 → 135 → 90 → 45` 각도 시퀀스를 일정 간격(2초)으로 반복 전송합니다.
3. 명령을 보낸 뒤 `RESPONSE_TIMEOUT_MS` 내에 응답이 없으면 연결이 끊긴 것으로 보고, 다시 1단계(프로빙)로 돌아갑니다.

펌웨어(`firmware-esp32/src/main.cpp`)는 UDP로 받은 문자열을 각도(0~180)로 파싱해 서보를 움직이고, `MOVED` 또는 `INVALID_ANGLE`을 응답으로 보냅니다.

## client-ts

```
cd client-ts
pnpm install
pnpm dev
```

- 실행 진입점: `src/index.ts`
- `src/config.ts`, `src/udpClient.ts`, `src/angleSender.ts`, `src/connectionManager.ts`로 역할이 분리되어 있습니다.
- `.env`에 `TARGET_IP`, `TARGET_PORT`, `RESPONSE_TIMEOUT_MS`, `PROBE_INTERVAL_MS` 설정.

## client-kts

```
cd client-kts
kotlin index.kts
```

- 단일 스크립트 실행 방식(`kotlin index.kts`)이라 파일을 여러 개로 나누지 않고, 한 파일 안에서 `Config`/`UdpClient`/`AngleSender`/`ConnectionManager` 클래스로 역할을 분리했습니다.
- 모든 상태 변경(연결 여부, 타이머)은 단일 `ScheduledExecutorService` 스레드에서만 처리해 레이스 컨디션을 피합니다.
- `.env`에 `TARGET_IP`, `TARGET_PORT`, `RESPONSE_TIMEOUT_MS`, `PROBE_INTERVAL_MS` 설정.
- 사전 준비: Kotlin 컴파일러(`kotlinc`/`kotlin`)가 PATH에 등록되어 있어야 합니다.

## firmware-esp32

PlatformIO 프로젝트입니다. `secret.ini`(gitignore 대상)에 WiFi SSID/비밀번호를 설정한 뒤 빌드/업로드합니다.

```
[wifi_secrets]
ssid = "..."
pass = "..."
```
