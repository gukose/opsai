export function targetDurationSeconds(intentType: string, title = ""): number {
  const value = `${intentType} ${title}`.toUpperCase();
  if (value.includes("MINIBAR")) return 10 * 60;
  if (value.includes("DEPARTURE") || value.includes("CHECK_OUT") || value.includes("CHECKOUT")) return 40 * 60;
  if (value.includes("TECHNICAL") || value.includes("MAINTENANCE") || value.includes("REPAIR")) return 45 * 60;
  if (value.includes("GUEST_REQUEST")) return 15 * 60;
  if (value.includes("VIP")) return 20 * 60;
  return 30 * 60;
}

export function formatDurationShort(seconds: number): string {
  const safe = Math.max(0, Math.floor(seconds));
  return `${String(Math.floor(safe / 60)).padStart(2, "0")}:${String(safe % 60).padStart(2, "0")}`;
}

/** Returns the non-negative SLA budget remaining, with all inputs in seconds. */
export function calculateSlaRemainingSeconds(targetSeconds: number, effectiveWorkingSeconds: number): number {
  const target = Number.isFinite(targetSeconds) ? Math.max(0, Math.floor(targetSeconds)) : 0;
  const worked = Number.isFinite(effectiveWorkingSeconds) ? Math.max(0, Math.floor(effectiveWorkingSeconds)) : 0;
  return Math.max(0, target - worked);
}
