import { invokeRuntime } from './invokeRuntime';
import type { ClientIntegrationStatus } from './runtimeTypes';

export const clientApi = {
  status: () => invokeRuntime<ClientIntegrationStatus>('client_integration_status'),
  selectProfile: (profilePath: string) =>
    invokeRuntime<ClientIntegrationStatus>('client_integration_select_profile', { profilePath }),
  pickProfile: () => invokeRuntime<ClientIntegrationStatus>('client_integration_pick_profile'),
  sync: () => invokeRuntime<ClientIntegrationStatus>('client_integration_sync')
};
