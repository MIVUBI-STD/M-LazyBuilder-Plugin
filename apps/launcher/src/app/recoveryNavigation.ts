import type { RecoveryAction } from './bridge/errors';

export type RecoveryNavigationTarget =
  | 'activity'
  | 'overview'
  | 'plugins';

/**
 * Maps only recovery actions whose safe handling is an actual cross-surface
 * navigation. Surface-local actions remain guidance until that surface provides
 * a concrete control, so an error notice never renders a button that appears to
 * do something but only navigates to the page already on screen.
 *
 * Destructive or state-changing actions intentionally return null.
 */
export function recoveryNavigationTarget(action: RecoveryAction | null): RecoveryNavigationTarget | null {
  switch (action) {
    case 'OPEN_ACTIVITY':
      return 'activity';
    case 'REVIEW_PLUGINS':
      return 'plugins';
    default:
      return null;
  }
}
