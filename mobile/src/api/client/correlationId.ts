export function createCorrelationId(): string {
  const crypto = globalThis.crypto as Crypto | undefined;
  if (crypto?.randomUUID) {
    return crypto.randomUUID();
  }

  return `corr-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}
