import { RuntimeError, runtimeError } from './bridge/runtimeApi';

export type RuntimeErrorPresentation = {
  code: string;
  message: string;
  details: string;
  recoverable: boolean;
  action: string;
  correlationId: string;
};

export function presentRuntimeError(value: unknown, fallback = 'Something went wrong. Try again.'): RuntimeErrorPresentation {
  const error = value instanceof RuntimeError ? value : new RuntimeError(runtimeError(value));
  return {
    code: error.code || 'RUNTIME_ERROR',
    message: error.message.trim() || fallback,
    details: error.details.trim(),
    recoverable: error.recoverable,
    action: (error.action ?? '').trim(),
    correlationId: error.correlationId.trim()
  };
}

export function runtimeErrorText(value: unknown, fallback?: string): string {
  return presentRuntimeError(value, fallback).message;
}
