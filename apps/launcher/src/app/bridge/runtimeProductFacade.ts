import { runtimeApi } from './runtimeApi';
import { runtimePreviewProduct } from './runtimePreviewProduct';

/**
 * Production uses the Tauri runtime. Vite visual-preview mode swaps only the
 * runtime data source so the real Svelte UI can be rendered deterministically
 * in CI without requiring a local Windows/Tauri session.
 */
export const runtimeProduct = import.meta.env.MODE === 'visual-preview'
  ? runtimePreviewProduct
  : runtimeApi;
