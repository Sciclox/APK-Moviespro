import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import animeRoutes from './routes/anime.routes';
import { CONFIG } from './config/constants';
import compression from 'compression';
import { ScraperService } from './services/scraper.service';

dotenv.config();

const app = express();

app.use(compression());
app.use(cors());
app.use(express.json());

// API Routes
app.use('/api/v1', animeRoutes);

// Root Welcome Route
app.get('/', (_req, res) => {
  res.send({
    message: '🚀 APK-AnimeTV Backend Gateway API active',
    documentation: '/api/v1/health',
  });
});

const PORT = process.env.PORT || CONFIG.PORT || 3000;

// Pre-warm cache on server startup so first app requests are instant
async function warmUpCache() {
  console.log('[WarmUp] 🔥 Pre-loading cache...');
  try {
    const results = await Promise.allSettled([
      ScraperService.getHeroBanners(),
      ScraperService.getCatalog({ filtro: 'popularidad', page: 1 }),
      ScraperService.getLatest(),
      ScraperService.getLatestEpisodes(),
    ]);
    const succeeded = results.filter(r => r.status === 'fulfilled').length;
    console.log(`[WarmUp] ✅ Cache pre-loaded (${succeeded}/4 endpoints ready)`);
  } catch (e) {
    console.warn('[WarmUp] ⚠️ Warm-up partially failed:', e);
  }
}

app.listen(PORT, () => {
  console.log(`==================================================`);
  console.log(`📺 APK-AnimeTV API running at port ${PORT}`);
  console.log(`🔗 Health Check: /api/v1/health`);
  console.log(`==================================================`);

  // Fire warm-up after server is listening (non-blocking)
  warmUpCache();
});

export default app;
