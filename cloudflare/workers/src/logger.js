export function isDebugLogEnabled(env) {
  return ['1', 'true', 'yes', 'on'].includes(String(env?.DEBUG_LOG || '').toLowerCase());
}

export function debugLog(env, ...args) {
  if (isDebugLogEnabled(env)) {
    console.log(...args);
  }
}
