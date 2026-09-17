import { invoke } from '@tauri-apps/api/core';
import { RuntimeError, runtimeError } from './errors';

export async function invokeRuntime<T>(command: string, args?: Record<string, unknown>): Promise<T> {
  try {
    return await invoke<T>(command, args);
  } catch (value) {
    throw new RuntimeError(runtimeError(value));
  }
}
