import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import animeRoutes from './routes/anime.routes';
import metricsRoutes from './routes/metrics.routes';
import { CONFIG } from './config/constants';
import compression from 'compression';
import { startCacheWorker } from './workers/cacheWorker';

dotenv.config();

const app = express();

app.use(compression());
app.use(cors());
app.use(express.json());

// API Routes
app.use('/api/v1', animeRoutes);
app.use('/api/v1', metricsRoutes);

// Root Welcome Route
app.get('/', (_req, res) => {
  res.send({
    message: '🚀 APK-AnimeTV Backend Gateway API active',
    documentation: '/api/v1/health',
    metrics: '/api/v1/metrics',
  });
});

const PORT = process.env.PORT || CONFIG.PORT || 3000;

app.listen(PORT, () => {
  console.log(`==================================================`);
  console.log(`📺 APK-AnimeTV API running at port ${PORT}`);
  console.log(`🔗 Health Check: /api/v1/health`);
  console.log(`📊 Metrics: /api/v1/metrics`);
  console.log(`==================================================`);

  // Start background proactive cache worker (refreshes popular feeds every 10 min)
  startCacheWorker(10);
});

export default app;
