import { ScraperService } from '../services/scraper.service';

/**
 * Background Cache Worker — Proactively pre-fetches and refreshes
 * high-traffic anime data every 10 minutes so users ALWAYS get <5ms cache hits.
 */
export function startCacheWorker(intervalMinutes: number = 10): NodeJS.Timeout {
  console.log(`[CacheWorker] ⚙️ Initializing proactive background worker (Interval: ${intervalMinutes}m)`);

  const refreshHighTrafficData = async () => {
    console.log('[CacheWorker] 🔄 Running proactive background cache refresh...');
    try {
      const results = await Promise.allSettled([
        ScraperService.getHeroBanners(),
        ScraperService.getCatalog({ filtro: 'popularidad', page: 1 }),
        ScraperService.getLatest(),
        ScraperService.getLatestEpisodes(),
      ]);

      const succeeded = results.filter((r) => r.status === 'fulfilled').length;
      console.log(`[CacheWorker] ✅ Refresh complete (${succeeded}/4 active feeds warm in cache)`);
    } catch (error) {
      console.warn('[CacheWorker] ⚠️ Cache refresh cycle encountered non-fatal error:', error);
    }
  };

  // Run first cycle immediately
  refreshHighTrafficData();

  // Schedule recurring interval
  const intervalMs = intervalMinutes * 60 * 1000;
  return setInterval(refreshHighTrafficData, intervalMs);
}
