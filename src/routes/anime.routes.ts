import { Router, Request, Response } from 'express';
import catalogRoutes from './catalog.routes';
import watchRoutes from './watch.routes';
import proxyRoutes from './proxy.routes';

const router = Router();

/**
 * @route GET /api/v1/health
 * @desc Health check
 */
router.get('/health', (_req: Request, res: Response) => {
  res.json({
    status: 'ok',
    timestamp: new Date().toISOString(),
    service: 'APK-AnimeTV Backend Gateway',
  });
});

// Mount Sub-Routers
router.use('/anime', catalogRoutes);
router.use('/anime', watchRoutes);
router.use('/proxy', proxyRoutes);

export default router;
