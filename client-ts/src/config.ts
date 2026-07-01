export const TARGET_IP = process.env.TARGET_IP || "127.0.0.1";
export const TARGET_PORT = Number(process.env.TARGET_PORT) || 41234;
export const RESPONSE_TIMEOUT_MS = Number(process.env.RESPONSE_TIMEOUT_MS) || 3000;
export const PROBE_INTERVAL_MS = Number(process.env.PROBE_INTERVAL_MS) || 1000;
