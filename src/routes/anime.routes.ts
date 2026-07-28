import { Router, Request, Response } from 'express';
import { ScraperService } from '../services/scraper.service';

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

/**
 * @route GET /api/v1/anime/hero
 * @desc Get featured anime banners for TV home screen
 */
router.get('/anime/hero', async (_req: Request, res: Response) => {
  try {
    const banners = await ScraperService.getHeroBanners();
    res.json({ success: true, data: banners });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * @route GET /api/v1/anime/latest
 * @desc Get latest currently releasing anime (Animes en emisión)
 */
router.get('/anime/latest', async (_req: Request, res: Response) => {
  try {
    const results = await ScraperService.getLatestReleasing();
    res.json({ success: true, data: results });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * @route GET /api/v1/anime/search
 * @desc Search anime by query string
 */
router.get('/anime/search', async (req: Request, res: Response) => {
  try {
    const query = req.query.q as string;
    if (!query) {
      return res.status(400).json({ success: false, error: 'Query parameter "q" is required' });
    }
    const results = await ScraperService.searchAnime(query);
    res.json({ success: true, data: results });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * @route GET /api/v1/anime/catalog
 * @desc Get anime catalog filtered by genre
 */
router.get('/anime/catalog', async (req: Request, res: Response) => {
  try {
    const genre = req.query.genre as string;
    const page = parseInt(req.query.page as string || '1', 10);
    const results = await ScraperService.getCatalog(genre, page);
    res.json({ success: true, data: results });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * @route GET /api/v1/anime/details/:slug
 * @desc Get anime details, metadata, and episode list
 */
router.get('/anime/details/:slug', async (req: Request, res: Response) => {
  try {
    const slug = req.params.slug as string;
    const details = await ScraperService.getAnimeDetails(slug);
    res.json({ success: true, data: details });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * @route GET /api/v1/anime/watch/*
 * @desc Get episode streaming sources for ExoPlayer
 */
router.get('/anime/watch/*', async (req: Request, res: Response) => {
  try {
    const episodeSlug = Array.isArray(req.params[0]) ? req.params[0].join('/') : (req.params[0] || '');
    const stream = await ScraperService.getStreamSources(episodeSlug);
    res.json({ success: true, data: stream });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

export default router;
