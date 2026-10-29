/** Only the home completion entry opts into the compact, in-progress form. */
export function isCompactTaskDetail(layout: unknown, status: string | undefined): boolean {
  return layout === 'compact' && status === 'IN_PROGRESS';
}

export function studentTodayTaskUrl(id: string): string {
  return `/pages/task-detail/task-detail?id=${encodeURIComponent(id)}&layout=compact`;
}
