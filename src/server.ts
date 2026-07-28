import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import animeRoutes from './routes/anime.routes';
import { CONFIG } from './config/constants';

dotenv.config();

const app = express();

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

if (process.env.NODE_ENV !== 'production') {
  app.listen(CONFIG.PORT, () => {
    console.log(`==================================================`);
    console.log(`📺 APK-AnimeTV API running at http://localhost:${CONFIG.PORT}`);
    console.log(`🔗 Health Check: http://localhost:${CONFIG.PORT}/api/v1/health`);
    console.log(`==================================================`);
  });
}

export default app;
