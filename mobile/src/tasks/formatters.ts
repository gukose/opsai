export function formatDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return date.toLocaleString([], {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  });
}

export function formatSlaCountdown(value: string): string {
  const deadline = new Date(value);
  const diffMs = deadline.getTime() - Date.now();
  if (!Number.isFinite(diffMs)) {
    return value;
  }

  if (diffMs <= 0) {
    return "Overdue";
  }

  const totalMinutes = Math.max(1, Math.ceil(diffMs / 60000));
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;

  if (hours === 0) {
    return `${minutes}m`;
  }

  return `${hours}h ${minutes}m`;
}
