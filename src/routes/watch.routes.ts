import { Router, Request, Response } from 'express';
import { ScraperService } from '../services/scraper.service';

const router = Router();

function getBackendBase(req: Request): string {
  const configuredBaseUrl = process.env.PUBLIC_BASE_URL?.replace(/\/$/, '');
  if (configuredBaseUrl) return configuredBaseUrl;

  const host = ((req.headers['x-forwarded-host'] as string) || req.get('host') || '').toLowerCase();
  if (/^(10\.0\.2\.2|localhost|127\.0\.0\.1)(:\d+)?$/.test(host)) {
    return `http://${host}`;
  }
  if (host) {
    return `https://${host}`;
  }
  return 'https://apk-animetv-backend.vercel.app';
}

/**
 * @route GET /api/v1/anime/watch/*
 * @desc Get episode streaming sources for ExoPlayer
 */
router.get('/watch/*', async (req: Request, res: Response) => {
  const startTime = Date.now();
  try {
    const episodeSlug = Array.isArray(req.params[0]) ? req.params[0].join('/') : (req.params[0] || '');
    const backendBase = getBackendBase(req);
    const stream = await ScraperService.getStreamSources(episodeSlug, backendBase);
    console.log(`[Telemetry /watch] ⏱️ Resolved ${episodeSlug} in ${Date.now() - startTime} ms`);
    if (stream.servers.length === 0) {
      res.status(404).json({
        success: false,
        error: 'No hay servidores de reproducción disponibles para este episodio',
      });
      return;
    }
    res.json({ success: true, data: stream });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

export default router;
