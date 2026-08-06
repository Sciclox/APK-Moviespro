import { Router, Request, Response } from 'express';

const router = Router();

/**
 * @route GET /api/v1/metrics
 * @desc Expose real-time server health, memory usage, and runtime performance stats
 */
router.get('/metrics', (_req: Request, res: Response) => {
  const memory = process.memoryUsage();

  res.json({
    status: 'ok',
    uptimeSeconds: Math.floor(process.uptime()),
    timestamp: new Date().toISOString(),
    memoryUsageMB: {
      rss: Math.round(memory.rss / (1024 * 1024)),
      heapTotal: Math.round(memory.heapTotal / (1024 * 1024)),
      heapUsed: Math.round(memory.heapUsed / (1024 * 1024)),
      external: Math.round(memory.external / (1024 * 1024)),
    },
    nodeVersion: process.version,
    platform: process.platform,
  });
});

export default router;
