import { Router, Request, Response } from 'express';
import { ScraperService } from '../services/scraper.service';

const router = Router();

function setEdgeCacheHeaders(res: Response, maxAgeSec: number = 300, sMaxAgeSec: number = 3600) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Cache-Control', `public, max-age=${maxAgeSec}, s-maxage=${sMaxAgeSec}, stale-while-revalidate=86400`);
}

/**
 * @route GET /api/v1/anime/hero
 * @desc Get featured anime banners for TV home screen
 */
router.get('/hero', async (req: Request, res: Response) => {
  try {
    const tipo = req.query.tipo as string;
    const banners = await ScraperService.getHeroBanners(tipo);
    setEdgeCacheHeaders(res, 300, 3600);
    res.json({ success: true, data: banners });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * @route GET /api/v1/anime/latest
 * @desc Get latest currently releasing anime (Animes en emisión)
 */
router.get('/latest', async (_req: Request, res: Response) => {
  try {
    const results = await ScraperService.getLatest();
    setEdgeCacheHeaders(res, 300, 3600);
    res.json({ success: true, data: results });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * @route GET /api/v1/anime/latest-episodes
 * @desc Get recently uploaded episodes from JKAnime home page
 */
router.get('/latest-episodes', async (_req: Request, res: Response) => {
  try {
    const results = await ScraperService.getLatestEpisodes();
    setEdgeCacheHeaders(res, 120, 1800);
    res.json({ success: true, data: results });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * @route GET /api/v1/anime/search
 * @desc Search anime by query string
 */
router.get('/search', async (req: Request, res: Response) => {
  try {
    const query = req.query.q as string;
    if (!query || query.trim().length > 100) {
      return res.status(400).json({ success: false, error: 'Query parameter "q" is required' });
    }
    const results = await ScraperService.searchAnime(query);
    setEdgeCacheHeaders(res, 600, 3600);
    res.json({ success: true, data: results });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * @route GET /api/v1/anime/catalog
 * @desc Get anime catalog filtered by genre
 */
router.get('/catalog', async (req: Request, res: Response) => {
  try {
    const genre = req.query.genre as string;
    const categoria = req.query.categoria as string;
    const tipo = req.query.tipo as string;
    const estado = req.query.estado as string;
    const demografia = req.query.demografia as string;
    const filtro = req.query.filtro as string;
    const page = parseInt(req.query.page as string || '1', 10);
    if (!Number.isInteger(page) || page < 1 || page > 100) {
      return res.status(400).json({ success: false, error: 'Page must be an integer between 1 and 100' });
    }
    const results = await ScraperService.getCatalog({ genre, categoria, tipo, estado, demografia, filtro, page });
    setEdgeCacheHeaders(res, 300, 3600);
    res.json({ success: true, data: results });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * @route GET /api/v1/anime/hub/:category
 * @desc Get unified category hub screen data (Latino, Películas, Donghua)
 */
router.get('/hub/:category', async (req: Request, res: Response) => {
  try {
    const category = req.params.category as string;
    if (!['latino', 'peliculas', 'donghua'].includes(category.toLowerCase())) {
      return res.status(400).json({ success: false, error: 'Categoría no válida' });
    }
    const hub = await ScraperService.getCategoryHub(category);
    setEdgeCacheHeaders(res, 300, 3600);
    res.json({ success: true, data: hub });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * @route GET /api/v1/anime/details/:slug
 * @desc Get anime details, metadata, and episode list
 */
router.get('/details/:slug', async (req: Request, res: Response) => {
  try {
    const slug = req.params.slug as string;
    const details = await ScraperService.getAnimeDetails(slug);
    setEdgeCacheHeaders(res, 600, 7200);
    res.json({ success: true, data: details });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

export default router;
