import type { LauncherOperationSnapshot, WorldTaskSnapshot } from '../bridge/runtimeApi';

export const ACTIVE_LAUNCHER_STATES = new Set<LauncherOperationSnapshot['state']>(['QUEUED', 'RUNNING', 'CANCELLING']);

export function launcherOperationActive(operation: LauncherOperationSnapshot) {
  return ACTIVE_LAUNCHER_STATES.has(operation.state);
}

export function worldTaskActive(task: WorldTaskSnapshot) {
  return task.state === 'QUEUED' || task.state === 'RUNNING';
}

export function worldTaskTitle(task: WorldTaskSnapshot) {
  const label = task.taskType.toLowerCase().replace(/_/g, ' ');
  return label.replace(/\b\w/g, (letter) => letter.toUpperCase());
}

export function worldTaskStateLabel(state: WorldTaskSnapshot['state']) {
  return { QUEUED: 'Queued', RUNNING: 'Running', SUCCEEDED: 'Completed', FAILED: 'Failed' }[state];
}

export function launcherStateLabel(state: LauncherOperationSnapshot['state']) {
  return {
    QUEUED: 'Queued',
    RUNNING: 'Running',
    SUCCEEDED: 'Completed',
    FAILED: 'Failed',
    CANCELLING: 'Cancelling',
    CANCELLED: 'Cancelled',
    RECOVERY_REQUIRED: 'Recovery required'
  }[state];
}

export function formatUnixTime(seconds?: number | null) {
  if (!seconds) return '';
  return new Date(seconds * 1000).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
}

export function formatIsoTime(value: string) {
  const parsed = Date.parse(value);
  if (!Number.isFinite(parsed)) return '';
  return new Date(parsed).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
}

export function progressPercent(operation: LauncherOperationSnapshot) {
  const progress = operation.progress;
  if (!progress?.total || progress.total <= 0) return null;
  return Math.max(0, Math.min(100, Math.round((progress.current / progress.total) * 100)));
}

export function formatProgress(operation: LauncherOperationSnapshot) {
  const progress = operation.progress;
  if (!progress) return '';
  if (progress.unit === 'bytes') {
    const format = (value: number) => value >= 1024 ** 3
      ? `${(value / 1024 ** 3).toFixed(1)} GB`
      : `${Math.ceil(value / 1024 ** 2)} MB`;
    return progress.total ? `${format(progress.current)} / ${format(progress.total)}` : format(progress.current);
  }
  return progress.total ? `${progress.current} / ${progress.total} ${progress.unit}` : `${progress.current} ${progress.unit}`;
}

export function hasTechnicalDetails(operation: LauncherOperationSnapshot) {
  return Boolean(operation.phase || operation.details || operation.warnings.length || operation.error?.details || operation.correlationId);
}
