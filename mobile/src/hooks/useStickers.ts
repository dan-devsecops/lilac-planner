import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';

/** The catalog is static config server-side (StickerCatalog) - cache it for the app session. */
export function useStickerCatalog() {
  return useQuery({
    queryKey: ['stickers'],
    queryFn: () => api.getStickers(),
    staleTime: Infinity,
  });
}
