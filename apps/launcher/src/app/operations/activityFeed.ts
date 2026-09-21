import { runtimeProduct } from '../bridge/runtimeProductFacade';
import type { SystemActivitySnapshot } from '../bridge/runtimeApi';

type ActivityFeedState = {
  snapshot: SystemActivitySnapshot | null;
  error: string;
  loading: boolean;
};

type ActivityFeedListener = (state: ActivityFeedState) => void;

const ACTIVE_POLL_MS = 2000;
const IDLE_POLL_MS = 10000;
const listeners = new Set<ActivityFeedListener>();

let state: ActivityFeedState = { snapshot: null, error: '', loading: true };
let timer: number | null = null;
let refreshInFlight = false;
let lifecycleInstalled = false;

function hasActiveWork(snapshot: SystemActivitySnapshot | null) {
  if (!snapshot) return false;
  return snapshot.launcherOperations.some((operation) =>
    operation.state === 'QUEUED' || operation.state === 'RUNNING' || operation.state === 'CANCELLING'
  ) || snapshot.worldTasks.some((task) => task.state === 'QUEUED' || task.state === 'RUNNING');
}

function publish(next: ActivityFeedState) {
  state = next;
  for (const listener of listeners) listener(state);
}

function clearTimer() {
  if (timer !== null && typeof window !== 'undefined') window.clearTimeout(timer);
  timer = null;
}

function schedule() {
  clearTimer();
  if (listeners.size === 0 || typeof document === 'undefined' || document.hidden) return;
  const delay = hasActiveWork(state.snapshot) ? ACTIVE_POLL_MS : IDLE_POLL_MS;
  timer = window.setTimeout(() => {
    timer = null;
    void refreshActivityFeed().finally(schedule);
  }, delay);
}

function handleVisibility() {
  if (document.hidden) {
    clearTimer();
    return;
  }
  void refreshActivityFeed().finally(schedule);
}

function handleFocus() {
  void refreshActivityFeed().finally(schedule);
}

function installLifecycle() {
  if (lifecycleInstalled || typeof window === 'undefined' || typeof document === 'undefined') return;
  document.addEventListener('visibilitychange', handleVisibility);
  window.addEventListener('focus', handleFocus);
  lifecycleInstalled = true;
}

function uninstallLifecycle() {
  if (!lifecycleInstalled || typeof window === 'undefined' || typeof document === 'undefined') return;
  document.removeEventListener('visibilitychange', handleVisibility);
  window.removeEventListener('focus', handleFocus);
  lifecycleInstalled = false;
}

export async function refreshActivityFeed() {
  if (refreshInFlight) return;
  refreshInFlight = true;
  try {
    const snapshot = await runtimeProduct.system.activity();
    publish({ snapshot, error: '', loading: false });
  } catch (value) {
    const error = value instanceof Error ? value.message : 'Could not load activity.';
    publish({ snapshot: state.snapshot, error, loading: false });
  } finally {
    refreshInFlight = false;
  }
}

export function subscribeActivityFeed(listener: ActivityFeedListener) {
  listeners.add(listener);
  listener(state);
  if (listeners.size === 1) {
    installLifecycle();
    void refreshActivityFeed().finally(schedule);
  }
  return () => {
    listeners.delete(listener);
    if (listeners.size === 0) {
      clearTimer();
      uninstallLifecycle();
    }
  };
}
