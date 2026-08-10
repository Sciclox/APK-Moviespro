import compression from 'compression';
import cors from 'cors';
import dotenv from 'dotenv';
import express, { NextFunction, Request, Response } from 'express';
import animeRoutes from './routes/anime.routes';
import metricsRoutes from './routes/metrics.routes';
import { rateLimit } from './middleware/rateLimit';

dotenv.config();

const app = express();
const corsOrigins = process.env.CORS_ORIGINS?.split(',').map((origin) => origin.trim()).filter(Boolean) ?? [];

app.disable('x-powered-by');
app.use(compression());
app.use(cors({
  origin(origin, callback) {
    // Native Android requests do not send Origin. Browser requests are opt-in.
    if (!origin || corsOrigins.includes(origin)) return callback(null, true);
    return callback(new Error('Origen no permitido por CORS'));
  },
}));
app.use(express.json({ limit: '64kb' }));
app.use(rateLimit(120, 60_000));

app.use('/api/v1/proxy', rateLimit(30, 60_000));
app.use('/api/v1', animeRoutes);
app.use('/api/v1', metricsRoutes);

app.get('/', (_req, res) => {
  res.json({
    message: 'APK-AnimeTV Backend Gateway API active',
    documentation: '/api/v1/health',
    metrics: '/api/v1/metrics',
  });
});

app.use((_req, res) => {
  res.status(404).json({ success: false, error: 'Ruta no encontrada' });
});

app.use((error: Error, _req: Request, res: Response, _next: NextFunction) => {
  console.error('[API Error]', error.message);
  res.status(500).json({ success: false, error: 'Error interno del servidor' });
});

export default app;
