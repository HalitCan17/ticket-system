import { createContext, useContext } from 'react';
import type { TicketsApi } from './tickets.ts';

export const ApiContext = createContext<TicketsApi | null>(null);

export function useTicketsApi(): TicketsApi {
  const api = useContext(ApiContext);
  if (!api) {
    throw new Error('useTicketsApi must be used inside an ApiContext provider');
  }
  return api;
}
