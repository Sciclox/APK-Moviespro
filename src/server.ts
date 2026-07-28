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

const PORT = process.env.PORT || CONFIG.PORT || 3000;

app.listen(PORT, () => {
  console.log(`==================================================`);
  console.log(`📺 APK-AnimeTV API running at port ${PORT}`);
  console.log(`🔗 Health Check: /api/v1/health`);
  console.log(`==================================================`);
});

export default app;
