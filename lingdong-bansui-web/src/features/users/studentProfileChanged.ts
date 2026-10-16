// In-process invalidation only; credentials and account responses never enter this notification.
const listeners = new Set<() => void>();

export function notifyStudentProfileChanged(): void {
  for (const listener of listeners) listener();
}

export function subscribeStudentProfileChanged(listener: () => void): () => void {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}
