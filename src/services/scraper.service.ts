import axios from 'axios';
import * as cheerio from 'cheerio';
import { CONFIG } from '../config/constants';
import '../config/httpAgent'; // Enable HTTP Keep-Alive
import { AnimeItem, AnimeDetail, WatchResponse, StreamSource, EpisodeItem, RecentEpisodeItem } from '../types/anime';
import { TMDBService } from './tmdb.service';

const inFlightRequests = new Map<string, Promise<any>>();

function deduplicateRequest<T>(key: string, fn: () => Promise<T>): Promise<T> {
  if (inFlightRequests.has(key)) {
    return inFlightRequests.get(key) as Promise<T>;
  }
  const promise = fn().finally(() => {
    inFlightRequests.delete(key);
  });
  inFlightRequests.set(key, promise);
  return promise;
}

function cleanSlug(rawSlug: string): string {
  if (!rawSlug) return 'anime';
  let s = rawSlug;
  if (s.includes('/')) {
    const parts = s.replace(/\/$/, '').split('/');
    s = parts[parts.length - 1];
  }
  // Strip artificial prefixes like "slug-", "anime-", "id-"
  s = s.replace(/^(slug|anime|id)-/gi, '');
  // Normalize common slug variation mismatches (e.g. re-zero -> rezero)
  s = s.replace(/re-zero/gi, 'rezero');
  return s;
}

function cleanTitle(title: string, slug: string): string {
  if (title && !title.startsWith('http')) {
    return title
      .replace(/([a-z])([A-Z])/g, '$1 $2')
      .replace(/[\u00a0\u2423\r\n\t_]+/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  }
  const slugClean = cleanSlug(slug);
  return slugClean.replace(/-/g, ' ').replace(/\b\w/g, (l) => l.toUpperCase());
}

interface CacheEntry<T> {
  data: T;
  expiresAt: number;
}

class MemoryCache {
  private cache = new Map<string, CacheEntry<any>>();
  private maxEntries = 500;

  get<T>(key: string): T | null {
    const entry = this.cache.get(key);
    if (!entry) return null;
    if (Date.now() > entry.expiresAt) {
      this.cache.delete(key);
      return null;
    }
    // LRU: Move to end (most recently used)
    this.cache.delete(key);
    this.cache.set(key, entry);
    return entry.data as T;
  }

  set<T>(key: string, data: T, ttlSeconds: number): void {
    if (this.cache.size >= this.maxEntries && !this.cache.has(key)) {
      const firstKey = this.cache.keys().next().value;
      if (firstKey) {
        this.cache.delete(firstKey);
      }
    }
    this.cache.set(key, {
      data,
      expiresAt: Date.now() + ttlSeconds * 1000,
    });
  }
}

const apiCache = new MemoryCache();

export class ScraperService {
  /**
   * Fetch Hero Banners for TV Home Screen
   */
  static async getHeroBanners(tipo?: string): Promise<AnimeItem[]> {
    const isMovies = tipo?.toLowerCase() === 'peliculas';
    const cacheKey = isMovies ? 'hero_banners_v3_peliculas' : 'hero_banners_v3_home';
    const cached = apiCache.get<AnimeItem[]>(cacheKey);
    if (cached) return cached;

    try {
      const catalogItems = await this.getCatalog(isMovies ? { tipo: 'peliculas', filtro: 'popularidad', page: 1 } : { filtro: 'popularidad', page: 1 });
      if (catalogItems && catalogItems.length > 0) {
        const topItems = catalogItems.slice(0, 30);
        const enrichedTopItems = await Promise.all(
          topItems.map(async (item, index) => {
            if (index < 15) {
              try {
                const [spanishMeta, aniListMeta] = await Promise.all([
                  TMDBService.getSpanishMetadata(item.title),
                  TMDBService.getAniListMetadata(item.title),
                ]);
                const backdropUrl = aniListMeta?.bannerImage || spanishMeta?.backdrop || null;
                if (backdropUrl) {
                  return {
                    ...item,
                    title: cleanTitle(item.title, item.slug),
                    backdrop: backdropUrl,
                    synopsis: spanishMeta?.synopsis || item.synopsis,
                  };
                }
              } catch (_e) {}
            }
            return {
              ...item,
              title: cleanTitle(item.title, item.slug)
            };
          })
        );

        apiCache.set(cacheKey, enrichedTopItems, 900); // 15-min cache
        return enrichedTopItems;
      }
    } catch (e) {
      console.error('Error fetching hero banners from catalog:', e);
    }

    return [
      {
        id: 'sword-art-online',
        title: 'Sword Art Online',
        slug: 'sword-art-online',
        image: 'https://cdn.animeav1.com/covers/222.jpg',
        backdrop: 'https://image.tmdb.org/t/p/original/6A8IHWrtMLtadNXnMTjggvVY8cs.jpg',
        synopsis: 'En el año 2022, el videojuego de realidad virtual Sword Art Online es lanzado.',
      },
    ];
  }

  /**
   * Fetch Currently Releasing Anime (Animes en Emisión)
   */
  static async getLatest(): Promise<AnimeItem[]> {
    const cacheKey = 'releasing_jkanime_emision';
    const cached = apiCache.get<AnimeItem[]>(cacheKey);
    if (cached) return cached;

    try {
      const items = await this.getCatalog({ estado: 'emision', page: 1 });
      if (items && items.length > 0) {
        apiCache.set(cacheKey, items, 1800);
        return items;
      }
    } catch (e) {
      console.error('Error fetching releasing animes from JKAnime directorio:', e);
    }

    return [];
  }

  static async getLatestReleasing(): Promise<AnimeItem[]> {
    return this.getLatest();
  }

  /**
   * Fetch Recently Uploaded Episodes from JKAnime Home Page
   */
  static async getLatestEpisodes(): Promise<RecentEpisodeItem[]> {
    const cacheKey = 'latest_episodes_jkanime_home';
    const cached = apiCache.get<RecentEpisodeItem[]>(cacheKey);
    if (cached) return cached;

    try {
      const { data: html } = await axios.get(CONFIG.JKANIME_BASE_URL, {
        headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' },
        timeout: 6000,
      });

      const $ = cheerio.load(html);
      const items: RecentEpisodeItem[] = [];
      const seenKeys = new Set<string>();

      const systemPaths = ['buscar', 'animes', 'directorio', 'genero', 'emision', 'usuario', 'perfil', 'editar-perfil', 'login', 'registro', 'top', 'dash', 'notificaciones'];

      $('.d-thumb, .anime__item, div.col-12, div.col-6, div.col-md-4, div.col-lg-3').each((_, element) => {
        const container = $(element);
        const a = container.find('a').first().attr('href') ? container.find('a').first() : container.closest('a');
        const href = a.attr('href') || container.parent().find('a').attr('href') || '';
        
        const match = href.match(/^\/([^\/]+)\/(\d+)\/?$/) || href.match(/jkanime\.net\/([^\/]+)\/(\d+)\/?$/);
        if (match) {
          const slug = match[1];
          const episodeNumber = parseInt(match[2], 10);
          
          if (!systemPaths.includes(slug)) {
            const key = `${slug}_${episodeNumber}`;
            if (!seenKeys.has(key)) {
              seenKeys.add(key);

              const imgEl = container.find('img').first();
              const posterUrl = imgEl.attr('data-animepic') || imgEl.attr('src') || `https://cdn.animeav1.com/covers/${slug}.jpg`;
              
              // Extract title from alt attribute (e.g. "One Piece - 1172") or h5 tag
              let altTitle = imgEl.attr('alt') || '';
              if (altTitle.includes(' - ')) {
                altTitle = altTitle.split(' - ')[0].trim();
              }
              const h5Title = container.parent().find('h5, h3, h6, .title, strong').first().text().replace(/\s+/g, ' ').trim();
              const title = cleanTitle(altTitle || h5Title || slug, slug);

              // Extract date label (Hoy, Ayer, Domingo 02, etc.)
              let dateText: string | null = null;
              container.parent().find('span, small, div').each((_, span) => {
                const txt = $(span).text().replace(/\s+/g, ' ').trim();
                if (txt === 'Hoy' || txt === 'Ayer' || txt.match(/^[A-Za-z]+ \d{2}$/i)) {
                  dateText = txt;
                }
              });

              items.push({
                id: `${slug}-${episodeNumber}`,
                slug: slug,
                title: title,
                episodeNumber: episodeNumber,
                image: posterUrl,
                dateText: dateText
              });
            }
          }
        }
      });

      if (items.length > 0) {
        apiCache.set(cacheKey, items, 300); // 5-minute cache
        return items;
      }
    } catch (e) {
      console.error('Error fetching latest episodes from JKAnime:', e);
    }

    return [];
  }

  static async searchAnime(query: string): Promise<AnimeItem[]> {
    if (!query || query.trim().length === 0) return [];
    const cleanQ = query.replace(/[:!?#,._-]/g, ' ').replace(/\s+/g, ' ').trim();
    const cacheKey = `search_jka_${cleanQ.toLowerCase()}`;
    const cached = apiCache.get<AnimeItem[]>(cacheKey);
    if (cached) return cached;

    // 1. Direct Search: Scrape JKAnime search page directly (100% official JKAnime slugs)
    try {
      const searchUrl = `${CONFIG.JKANIME_BASE_URL}/buscar/${encodeURIComponent(cleanQ)}/`;
      const { data: html } = await axios.get(searchUrl, {
        headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' },
        timeout: 4500,
      });
      const $ = cheerio.load(html);
      const items: AnimeItem[] = [];

      $('.anime__item').each((_, el) => {
        const a = $(el).find('a').first();
        const href = a.attr('href') || '';
        if (href.includes('jkanime.net/')) {
          const parts = href.replace(/\/$/, '').split('/');
          const slug = parts[parts.length - 1];
          const systemPaths = ['buscar', 'animes', 'directorio', 'genero', 'emision', 'usuario', 'perfil', 'editar-perfil', 'login', 'registro'];

          if (slug && !systemPaths.includes(slug.toLowerCase()) && !items.some((i) => i.slug === slug)) {
            const picDiv = $(el).find('.anime__item__pic, .set-bg').first();
            let img = picDiv.attr('data-setbg') || picDiv.find('img').attr('src') || '';
            if (!img) {
              img = `https://cdn.jkdesa.com/assets/images/animes/image/${slug}.jpg`;
            }

            const titleText = $(el).find('h5, .title, .tituloblanco, h2, h3').text().trim() || cleanTitle('', slug);
            let status: string | undefined = undefined;
            let type: string | undefined = undefined;

            $(el).find('.anime__item__text li').each((_, li) => {
              const txt = $(li).text().trim();
              if (txt.toLowerCase().includes('emision')) status = 'En emisión';
              if (txt.toLowerCase().includes('concluid') || txt.toLowerCase().includes('finalizad')) status = 'Finalizado';
              if ($(li).hasClass('anime') || ['serie', 'pelicula', 'ova', 'ona', 'especial'].some((t) => txt.toLowerCase().includes(t))) {
                type = txt;
              }
            });

            items.push({
              id: slug,
              title: cleanTitle(titleText, slug),
              slug: slug,
              image: img,
              backdrop: null,
              status: status,
              type: type || 'TV Anime',
              provider: 'JKAnime',
            });
          }
        }
      });

      if (items.length > 0) {
        apiCache.set(cacheKey, items, 1800); // 30-min cache
        return items;
      }
    } catch (e) {
      console.error('JKAnime direct search error:', e);
    }

    // 2. Fallback: Try AnimeVerse search if JKAnime search returns 0 results
    try {
      const url = `${CONFIG.ANIMEVERSE_BASE_URL}/api/v1/anime/search?q=${encodeURIComponent(cleanQ)}`;
      const { data } = await axios.get(url, { timeout: 4500 });

      if (data && data.success && Array.isArray(data.data?.results) && data.data.results.length > 0) {
        const items = data.data.results.map((item: any) => {
          const rawSlug = item.slug || item.id || item.title;
          const slug = cleanSlug(rawSlug);
          const title = cleanTitle(item.title, slug);

          return {
            id: slug,
            title: title,
            slug: slug,
            image: item.image || `https://cdn.jkdesa.com/assets/images/animes/image/${slug}.jpg`,
            backdrop: item.backdrop || null,
            type: item.type || 'TV Anime',
            provider: item.provider || 'AnimeVerse',
          };
        });
        apiCache.set(cacheKey, items, 1800);
        return items;
      }
    } catch (e) {
      console.warn('AnimeVerse search fallback timed out/failed');
    }

    return [];
  }

  /**
   * Get Catalog by Genre / Category / Type / Status
   */
  static async getCatalog(
    options?: { genre?: string; categoria?: string; tipo?: string; estado?: string; demografia?: string; filtro?: string; page?: number } | string,
    pageNum: number = 1
  ): Promise<AnimeItem[]> {
    try {
      let genre = typeof options === 'object' ? options.genre : options;
      let categoria = typeof options === 'object' ? options.categoria : undefined;
      let tipo = typeof options === 'object' ? options.tipo : undefined;
      let estado = typeof options === 'object' ? options.estado : undefined;
      let demografia = typeof options === 'object' ? options.demografia : undefined;
      let filtro = typeof options === 'object' ? options.filtro : undefined;
      let page = typeof options === 'object' ? (options.page || 1) : pageNum;

      const cleanGenre = genre && genre.toLowerCase() !== 'todos' ? genre.toLowerCase().replace(/[^a-z0-9-]+/g, '-') : '';
      const cleanCategoria = categoria ? categoria.toLowerCase().trim() : '';
      const cleanTipo = tipo ? tipo.toLowerCase().trim() : '';
      const cleanEstado = estado ? estado.toLowerCase().trim() : '';
      const cleanDemografia = demografia ? demografia.toLowerCase().trim() : '';
      const cleanFiltro = filtro ? filtro.toLowerCase().trim() : '';

      const demographySlugs = ['shounen', 'shoujo', 'seinen', 'josei', 'kodomo'];

      const params: string[] = [`p=${page}`];
      if (cleanGenre) {
        if (demographySlugs.includes(cleanGenre)) {
          params.push(`demografia=${encodeURIComponent(cleanGenre)}`);
        } else {
          params.push(`genero=${encodeURIComponent(cleanGenre)}`);
        }
      }
      if (cleanDemografia && !cleanGenre) {
        params.push(`demografia=${encodeURIComponent(cleanDemografia)}`);
      }
      if (cleanCategoria) params.push(`categoria=${encodeURIComponent(cleanCategoria)}`);
      if (cleanTipo) params.push(`tipo=${encodeURIComponent(cleanTipo)}`);
      if (cleanEstado) params.push(`estado=${encodeURIComponent(cleanEstado)}`);
      if (cleanFiltro) params.push(`filtro=${encodeURIComponent(cleanFiltro)}`);

      const url = `${CONFIG.JKANIME_BASE_URL}/directorio/?${params.join('&')}`;

      const { data } = await axios.get(url, {
        headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' },
        timeout: 6000,
      });

      const match = typeof data === 'string' ? data.match(/var\s+animes\s*=\s*(\{.*?\});/s) : null;
      if (match && match[1]) {
        const parsed = JSON.parse(match[1]);
        if (parsed && Array.isArray(parsed.data)) {
          const rawItems: AnimeItem[] = parsed.data.map((item: any) => {
            const rawSlug = item.slug || item.id || item.title;
            const slug = cleanSlug(rawSlug);
            const title = cleanTitle(item.title, slug);

            return {
              id: slug,
              title: title,
              slug: slug,
              image: item.image,
              type: item.tipo || item.type || 'Serie',
              status: item.estado || item.status || 'Finalizado',
              synopsis: item.synopsis || null,
              provider: 'JKAnime Directorio',
            };
          });

          // Enrich top catalog items (especially movies, latino, donghua) with 16:9 TMDB Backdrops
          const enrichedItems = await Promise.all(
            rawItems.map(async (animeItem, idx) => {
              if (idx < 10 || cleanTipo === 'peliculas' || cleanCategoria === 'latino' || cleanCategoria === 'donghua') {
                try {
                  const [spanishMeta, aniListMeta] = await Promise.all([
                    TMDBService.getSpanishMetadata(animeItem.title),
                    TMDBService.getAniListMetadata(animeItem.title),
                  ]);
                  const backdropUrl = aniListMeta?.bannerImage || spanishMeta?.backdrop || null;
                  if (backdropUrl) {
                    return {
                      ...animeItem,
                      backdrop: backdropUrl,
                      synopsis: spanishMeta?.synopsis || animeItem.synopsis,
                    };
                  }
                } catch (_e) {}
              }
              return animeItem;
            })
          );

          return enrichedItems;
        }
      }
    } catch (e) {
      console.error('Catalog error:', e);
    }
    return [];
  }

  /**
   * Universal automatic slug resolver for JKAnime
   */
  static async resolveJkSlug(slugInput: string): Promise<string> {
    let slug = cleanSlug(slugInput);
    slug = slug.replace(/-(episode|episodio)-\d+$/i, '').replace(/\/\d+$/, '');

    // 1. Check if direct slug exists on JKAnime
    try {
      await axios.head(`${CONFIG.JKANIME_BASE_URL}/${slug}/`, {
        headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' },
        timeout: 1500,
      });
      return slug;
    } catch {}

    // 2. Try common JKAnime slug variations (e.g. -tv, -ii, -iii, -2024, -season-2)
    const variations = [
      `${slug}-tv`,
      `${slug}-2024`,
      `${slug}-ii`,
      `${slug}-iii`,
      `${slug}-season-2`,
      `${slug}-season-3`,
      slug.replace(/-20\d\d$/, ''),
      slug.replace(/-season-\d+$/, ''),
      slug.replace(/-2$/, '-ii').replace(/-3$/, '-iii').replace(/-4$/, '-iv')
    ];

    for (const v of variations) {
      if (v !== slug) {
        try {
          await axios.head(`${CONFIG.JKANIME_BASE_URL}/${v}/`, {
            headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' },
            timeout: 1200,
          });
          return v;
        } catch {}
      }
    }
    // 3. Fallback to scraping JKAnime search results with strict system path filters
    try {
      const cleanTitleText = slug.replace(/-/g, ' ');
      const cleanWords = cleanTitleText.split(' ').filter((w) => w.length >= 2);
      const searchQueries = [
        cleanWords.join(' '),
        cleanWords.slice(-2).join(' '),
        cleanWords[0] || ''
      ].filter(Boolean);

      for (const q of searchQueries) {
        const searchUrl = `${CONFIG.JKANIME_BASE_URL}/buscar/?q=${encodeURIComponent(q)}`;
        const { data: html } = await axios.get(searchUrl, {
          headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' },
          timeout: 3000,
        });
        const $ = cheerio.load(html);
        const candidates: string[] = [];
        const systemPaths = [
          'buscar', 'animes', 'directorio', 'genero', 'emision', 'usuario', 'top',
          'letra', 'ajax', 'assets', 'assets3', 'perfil', 'editar-perfil', 'login',
          'registro', 'logout', 'privacidad', 'dmca', 'contacto', 'horario', 'politicas',
          'politica-de-privacidad', 'terminos-y-condiciones', 'panel', 'home', 'dash',
          'comentarios', 'solicitudes', 'listas', 'guardado', 'historial', 'notificaciones',
          'estrenos', 'comunidad', 'aplicacion', '$me', 'reset', 'google', 'discord',
          'facebook', 'twitter', 'instagram', 'auth', 'password', 'user'
        ];

        $('a').each((_: number, el: any) => {
          const href = $(el).attr('href') || '';
          if (href.includes('jkanime.net/')) {
            const parts = href.replace(/\/$/, '').split('/');
            const candidate = parts[parts.length - 1];
            if (
              candidate &&
              /^[a-z0-9-]+$/i.test(candidate) &&
              !systemPaths.includes(candidate.toLowerCase()) &&
              !href.includes('/dash/') &&
              !href.includes('/buscar') &&
              !candidate.includes('perfil') &&
              !candidate.includes('user') &&
              !candidate.includes('login')
            ) {
              if (!candidates.includes(candidate)) {
                candidates.push(candidate);
              }
            }
          }
        });

        if (candidates.length > 0) {
          const wordsToMatch = cleanWords.length > 0 ? cleanWords : [slug];

          // First check if any candidate matches ALL key words of original slug
          const exactWordMatch = candidates.find((c: string) => {
            const cClean = c.toLowerCase().replace(/[^a-z0-9]+/g, ' ');
            return wordsToMatch.every((word: string) => cClean.includes(word.toLowerCase()));
          });
          if (exactWordMatch) return exactWordMatch;

          // Select candidate with maximum overlapping words
          let bestCandidate = candidates[0];
          let maxOverlap = 0;
          for (const c of candidates) {
            const cClean = c.toLowerCase().replace(/[^a-z0-9]+/g, ' ');
            const overlap = wordsToMatch.filter((w: string) => cClean.includes(w.toLowerCase())).length;
            if (overlap > maxOverlap) {
              maxOverlap = overlap;
              bestCandidate = c;
            }
          }

          if (maxOverlap > 0) {
            return bestCandidate;
          }
        }
      }
    } catch {
      // ignore
    }

    return slug;
  }

  /**
   * Helper to fetch real native episode thumbnail preview directly from JKAnime episode page
   */
  static async fetchJkEpisodeStill(slug: string, epNum: number): Promise<string | null> {
    try {
      const url = `${CONFIG.JKANIME_BASE_URL}/${slug}/${epNum}/`;
      const { data: html } = await axios.get(url, {
        headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' },
        timeout: 1200,
      });
      const m = html.match(/(https:\/\/cdn\.jkdesa\.com\/assets\/images\/animes\/video\/image(?:_thumb)?\/[^"'\s>]+)/);
      if (m && m[1]) {
        return m[1];
      }
    } catch {
      // ignore
    }
    return null;
  }

  /**
   * Get Anime Detail, Spanish Synopsis, Episode Stills and Metadata
   */
  static async getAnimeDetails(slugInput: string): Promise<AnimeDetail> {
    const cacheKey = `anime_detail_v6_${slugInput.toLowerCase()}`;
    const cached = apiCache.get<AnimeDetail>(cacheKey);
    if (cached && cached.backdrop) {
      return cached;
    }

    const slug = await ScraperService.resolveJkSlug(slugInput);
    const cleanTitleText = slug.replace(/-/g, ' ');

    const jkDataPromise = (async () => {
      try {
        const url = `${CONFIG.JKANIME_BASE_URL}/${slug}/`;
        const { data: html } = await axios.get(url, {
          headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' },
          timeout: 4000,
        });
        const $ = cheerio.load(html);

        let epCount = 0;
        $('li').each((_, el) => {
          const text = $(el).text().trim();
          if (text.includes('Episodios:')) {
            const numMatch = text.match(/Episodios:\s*(\d+)/i);
            if (numMatch && numMatch[1] && numMatch[1] !== '0') {
              epCount = parseInt(numMatch[1], 10);
            }
          }
        });

        // For long-running ongoing series (like One Piece) where "Episodios: 0" is printed,
        // extract max episode number from episode links on the page (e.g. 1171)
        if (epCount === 0) {
          const epMatches = html.match(new RegExp(`jkanime\\.net\\/${slug}\\/(\\d+)\\/?`, 'g'));
          if (epMatches) {
            for (const mStr of epMatches) {
              const numM = mStr.match(/\/(\d+)\/?$/);
              if (numM && numM[1]) {
                const parsed = parseInt(numM[1], 10);
                if (parsed > epCount) epCount = parsed;
              }
            }
          }
        }

        let seasonCover: string | null = null;
        const imgTag = $('.anime_pic img, img[src*="assets/images/animes/image/"]').first().attr('src');
        if (imgTag) {
          seasonCover = imgTag;
        }

        let seasonTitle: string | null = null;
        const pageTitle = $('title').text().trim();
        if (pageTitle && pageTitle.includes(' - anime ')) {
          seasonTitle = pageTitle.split(' - anime ')[0].trim();
        } else if (pageTitle && pageTitle.includes(' online JkAnime')) {
          seasonTitle = pageTitle.split(' online JkAnime')[0].split(' - ')[0].trim();
        }

        let seasonSynopsis: string | null = null;
        $('.sinopsis p, .sinop p, .sinopsis, .sinop, div.sinopsis, p').each((_, el) => {
          const t = $(el).text().trim();
          if (
            t.length > 40 &&
            !seasonSynopsis &&
            !t.includes('JKAnime') &&
            !t.includes('Votar') &&
            !t.includes('redes sociales') &&
            !t.includes('Sesi') &&
            !t.includes('cuenta') &&
            !t.includes('alojado')
          ) {
            seasonSynopsis = t;
          }
        });

        let altEnglish: string | null = null;
        let altJapanese: string | null = null;
        const altSynonyms: string[] = [];

        // Extract structured title alternatives from JKAnime's "Titulos Alternativos" section
        const $altSection = $('h5:contains("Titulos Alternativos")').first().parent();
        if ($altSection.length > 0) {
          $altSection.find('b.t').each((_, el) => {
            const label = $(el).text().trim().toLowerCase();
            // Get the text node immediately after this <b> element
            const nextText = $(el).get(0)?.nextSibling;
            const value = nextText && nextText.type === 'text' ? (nextText as unknown as { data: string }).data?.trim() : '';
            
            if (!value || value.length < 2) return;

            if (label.includes('ingles') || label.includes('english')) {
              if (!altEnglish) altEnglish = value;
            } else if (label.includes('japones') || label.includes('japanese')) {
              if (!altJapanese) altJapanese = value;
            } else if (label.includes('sinonimos') || label.includes('synonyms')) {
              // May have multiple synonyms separated by commas
              value.split(',').forEach(s => {
                const trimmed = s.trim();
                if (trimmed && trimmed.length > 1 && !altSynonyms.includes(trimmed)) {
                  altSynonyms.push(trimmed);
                }
              });
            }
          });
        }

        // Fallback: regex-based extraction if structured parsing didn't find titles
        if (!altEnglish || !altJapanese) {
          $('.sinopsis, .info-cap, div, li, p, span, td').each((_, el) => {
            const rawText = $(el).text().replace(/\s+/g, ' ').trim();
            const normText = rawText.normalize('NFD').replace(/[\u0300-\u036f]/g, '');

            if (/ingles|english/i.test(normText) && !altEnglish) {
              const m = normText.match(/(?:ingles|english)\s*:?\s*([^\n\r<]+)/i);
              if (m && m[1] && m[1].trim().length > 1) {
                const candidate = m[1].split(/(?:japones|japanese|sinonimos|tipo)/i)[0].trim();
                if (candidate && candidate.length < 100 && !candidate.toLowerCase().includes('titulos')) {
                  altEnglish = candidate;
                }
              }
            }
            if (/japones|japanese/i.test(normText) && !altJapanese) {
              const m = normText.match(/(?:japones|japanese)\s*:?\s*([^\n\r<]+)/i);
              if (m && m[1] && m[1].trim().length > 1) {
                const candidate = m[1].split(/(?:ingles|english|sinonimos|tipo)/i)[0].trim();
                if (candidate && candidate.length < 100 && !candidate.toLowerCase().includes('titulos')) {
                  altJapanese = candidate;
                }
              }
            }
          });
        }

        // Fallback: Scrape AnimeFLV directly for native Spanish synopsis if JKAnime paragraph is missing
        if (!seasonSynopsis) {
          try {
            const flvUrl = `https://animeflv.net/anime/${slug}`;
            const flvRes = await axios.get(flvUrl, {
              headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' },
              timeout: 3000,
            });
            const $flv = cheerio.load(flvRes.data);
            const flvText = $flv('div.Description p').text().trim() || $flv('.sinopsis').text().trim();
            if (flvText && flvText.length > 30) {
              seasonSynopsis = flvText;
            }
          } catch {
            // ignore
          }
        }

        const relatedAnime: import('../types/anime').RelatedAnimeItem[] = [];
        
        // Scrape "Temporadas y relacionados" using h5 headers as relation labels
        const $relH3 = $('h3:contains("Temporadas y relacionados")').first();
        if ($relH3.length > 0) {
          const $relContainer = $relH3.parent();
          let currentRelation = 'Relacionado';

          $relContainer.children('h5, a').each((_, el) => {
            const $el = $(el);
            const tag = el.tagName.toLowerCase();

            if (tag === 'h5') {
              // This is a relation header like "Precuela", "Secuela", "Adicional", etc.
              currentRelation = $el.text().trim().replace(/:$/, '').trim() || 'Relacionado';
            } else if (tag === 'a') {
              const href = $el.attr('href') || '';
              const text = $el.text().trim();
              const slugMatch = href.match(/jkanime\.net\/([a-zA-Z0-9-]+)\/?$/);

              if (slugMatch && slugMatch[1] && slugMatch[1] !== slug) {
                const targetSlug = slugMatch[1];
                if (!['directorio', 'horario', 'comunidad', 'estrenos', 'buscar', 'user', 'login', 'top'].includes(targetSlug)) {
                  let type = 'Serie';
                  const typeMatch = text.match(/\((Serie|Pelicula|Película|Especial|OVA)\)/i);
                  if (typeMatch) {
                    type = typeMatch[1];
                  }
                  const cleanTitleText = text.replace(/\s*\((Serie|Pelicula|Película|Especial|OVA)\)/i, '').trim();

                  if (cleanTitleText && !relatedAnime.some(r => r.slug === targetSlug)) {
                    relatedAnime.push({
                      id: targetSlug,
                      title: cleanTitleText,
                      slug: targetSlug,
                      relation: currentRelation,
                      type,
                      image: `https://cdn.jkdesa.com/assets/images/animes/image/${targetSlug}.jpg`
                    });
                  }
                }
              }
            }
          });
        }

        // Scrape "Te recomendamos" section
        const recommendedAnime: import('../types/anime').RecommendedAnimeItem[] = [];
        const $recSection = $('section#relacionados').first();
        if ($recSection.length > 0) {
          $recSection.find('.anime__item').each((_, el) => {
            const $item = $(el);
            const $link = $item.find('a[href*="jkanime.net/"]').first();
            const href = $link.attr('href') || '';
            const title = $link.attr('title') || $item.find('.anime__item__text h5 a').text().trim();
            const slugMatch = href.match(/jkanime\.net\/([a-zA-Z0-9-]+)\/?$/);
            const type = $item.find('li.peli').text().trim() || 'Serie';

            if (slugMatch && slugMatch[1] && slugMatch[1] !== slug && title) {
              const targetSlug = slugMatch[1];
              if (!recommendedAnime.some(r => r.slug === targetSlug)) {
                recommendedAnime.push({
                  id: targetSlug,
                  title,
                  slug: targetSlug,
                  type,
                  image: `https://cdn.jkdesa.com/assets/images/animes/image/${targetSlug}.jpg`
                });
              }
            }
          });
        }

        const jkGenres: string[] = [];
        $('a[href*="/genero/"]').each((_, el) => {
          const g = $(el).text().trim();
          if (g && !jkGenres.includes(g)) {
            jkGenres.push(g);
          }
        });

        return { epCount, seasonCover, seasonSynopsis, seasonTitle, altEnglish, altJapanese, altSynonyms, relatedAnime, recommendedAnime, jkGenres };
      } catch (e) {
        return { epCount: 0, seasonCover: null, seasonSynopsis: null, seasonTitle: null, altEnglish: null, altJapanese: null, altSynonyms: [] as string[], relatedAnime: [], recommendedAnime: [], jkGenres: [] };
      }
    })();

    const jkMeta = await jkDataPromise;

    const titleCandidates: string[] = [];
    if (jkMeta.altEnglish) titleCandidates.push(jkMeta.altEnglish);
    if (jkMeta.altJapanese) titleCandidates.push(jkMeta.altJapanese);
    if (!titleCandidates.includes(cleanTitleText)) titleCandidates.push(cleanTitleText);
    // Add JKAnime synonyms (e.g. "Blade of Demon Destruction") for broader search
    for (const syn of jkMeta.altSynonyms) {
      if (!titleCandidates.includes(syn)) titleCandidates.push(syn);
    }

    let meta: Partial<import('./tmdb.service').AniListMedia> | null = null;
    for (const cand of titleCandidates) {
      meta = await TMDBService.getAniListMetadata(cand);
      if (meta) {
        if (meta.title?.english && !titleCandidates.includes(meta.title.english)) {
          titleCandidates.push(meta.title.english);
        }
        if (meta.title?.native && !titleCandidates.includes(meta.title.native)) {
          titleCandidates.push(meta.title.native);
        }
        if (meta.title?.romaji && !titleCandidates.includes(meta.title.romaji)) {
          titleCandidates.push(meta.title.romaji);
        }
        break;
      }
    }

    const [spanishMeta, kitsuMeta] = await Promise.all([
      TMDBService.getSpanishMetadata(titleCandidates),
      TMDBService.getKitsuMetadata(titleCandidates[0]),
    ]);

    // If primary Kitsu lookup failed, try with English title from AniList
    let finalKitsuMeta = kitsuMeta;
    if (!finalKitsuMeta && meta?.title?.english) {
      finalKitsuMeta = await TMDBService.getKitsuMetadata(meta.title.english);
    }

    // Sanitize spanishMeta to prevent loose TMDB mismatches (e.g. Nihon Animator Mihonichi)
    const isInvalidSynopsis = spanishMeta?.synopsis ? (spanishMeta.synopsis.includes('Japan Anima') || spanishMeta.synopsis.includes('Exhibition')) : false;
    let validSpanishMeta = (spanishMeta && !isInvalidSynopsis) ? spanishMeta : null;

    // Backdrop retry: if validSpanishMeta has no backdrop, try TMDB again with AniList English title
    if ((!validSpanishMeta || !validSpanishMeta.backdrop) && meta?.title?.english) {
      const retryMeta = await TMDBService.getSpanishMetadata(meta.title.english);
      if (retryMeta && retryMeta.backdrop) {
        if (validSpanishMeta) {
          // Merge: keep existing synopsis but take the new backdrop
          validSpanishMeta = { ...validSpanishMeta, backdrop: retryMeta.backdrop, image: retryMeta.image || validSpanishMeta.image };
        } else {
          validSpanishMeta = retryMeta;
        }
      }
    }

    // Root title fallback: if still no backdrop, try stripping season/arc suffixes to find main series banner
    let rootBackdrop: string | null = null;
    if (!validSpanishMeta?.backdrop && !finalKitsuMeta && !meta?.bannerImage) {
      const rootQuery = slug
        .replace(/-(?:yuukaku-hen|mugen-ressha-hen|katana-kaji-no-sato-hen|hashira-geiko-hen|2nd-season|3rd-season|4th-season|final-season|part-\d+|season-\d+|tv|ova|movie).*/i, '')
        .replace(/-/g, ' ')
        .trim();
      if (rootQuery && rootQuery.length > 3) {
        try {
          const rootMeta = await TMDBService.getSpanishMetadata(rootQuery);
          if (rootMeta?.backdrop) {
            rootBackdrop = rootMeta.backdrop;
          } else {
            const rootAniList = await TMDBService.getAniListMetadata(rootQuery);
            if (rootAniList?.bannerImage) {
              rootBackdrop = rootAniList.bannerImage;
            }
          }
        } catch (_e) {}
      }
    }

    const isMovie = meta?.format === 'MOVIE' || cleanTitleText.toLowerCase().includes('movie') || cleanTitleText.toLowerCase().includes('pelicula') || cleanTitleText.toLowerCase().includes('película');
    const mediaType = isMovie ? 'PELÍCULA' : (meta?.format === 'SPECIAL' || meta?.format === 'OVA' ? 'ESPECIAL' : 'SERIE');

    const totalEpisodes = isMovie
      ? 1
      : (jkMeta.epCount > 0
          ? jkMeta.epCount
          : (meta?.episodes && meta.episodes > 0 ? meta.episodes : 12));

    const stills = validSpanishMeta?.episodeStills || {};
    const seasonFallbackImage = jkMeta.seasonCover || meta?.bannerImage || meta?.coverImage?.extraLarge || validSpanishMeta?.backdrop || validSpanishMeta?.image || 'https://cdn.animeav1.com/covers/222.jpg';

    const isSequel = /(?:season|part|2nd|3rd|4th|ii|iii|iv)/i.test(slug);
    const isLongRunning = totalEpisodes > 50 || slug.includes('one-piece') || slug.includes('naruto') || slug.includes('detective-conan') || slug.includes('dragon-ball');

    // Concurrently fetch real native episode preview stills directly from JKAnime episode pages
    const epCountToFetch = isMovie ? 1 : Math.min(totalEpisodes, 15);
    const jkStillsPromises = Array.from({ length: epCountToFetch }, (_, i) =>
      ScraperService.fetchJkEpisodeStill(slug, i + 1)
    );
    const jkStills = await Promise.all(jkStillsPromises);

    const episodes: EpisodeItem[] = [];

    if (isMovie) {
      episodes.push({
        id: `${slug}-movie`,
        number: 1,
        title: 'Película Completa',
        image: jkStills[0] || seasonFallbackImage,
        duration: '1 h 45 min',
        overview: `Disfruta de la película completa de ${cleanTitle(cleanTitleText, slug)} en calidad Full HD.`,
        slug: `${slug}/1`,
      });
    } else {
      for (let i = 0; i < totalEpisodes; i++) {
        const epNum = i + 1;
        const jkStill = jkStills[i];

        // Stop adding ghost episodes if epNum exceeds verified JKAnime epCount
        if (jkMeta.epCount > 0 && epNum > jkMeta.epCount && !isLongRunning) {
          break;
        }

        // Stop adding ghost episodes if this episode page does not exist on JKAnime
        if (i > 0 && !jkStill && !isLongRunning) {
          const nextStill = jkStills[i + 1];
          if (!nextStill) {
            break;
          }
        }

        const stillImg = jkStill || seasonFallbackImage;
        episodes.push({
          id: `${slug}-episode-${epNum}`,
          number: epNum,
          title: `Episodio ${epNum}`,
          image: stillImg,
          duration: '24 min',
          overview: `Capítulo ${epNum} de ${cleanTitle(cleanTitleText, slug)}`,
          slug: `${slug}/${epNum}`,
        });
      }
    }

    let rawSynopsis = jkMeta.seasonSynopsis || null;

    if (!rawSynopsis && validSpanishMeta?.synopsis) {
      rawSynopsis = validSpanishMeta.synopsis;
    }

    if (rawSynopsis && rawSynopsis.length < 120 && validSpanishMeta?.synopsis && rawSynopsis !== validSpanishMeta.synopsis) {
      rawSynopsis = `${rawSynopsis}\n\n${validSpanishMeta.synopsis}`;
    }

    if (!rawSynopsis && meta?.description) {
      const translated = await TMDBService.translateToSpanish(meta.description);
      rawSynopsis = translated || meta.description.replace(/<[^>]*>?/gm, '');
    }

    const finalSynopsis = rawSynopsis || `${cleanTitle(cleanTitleText, slug)} es una aclamada serie de animación japonesa con increíbles batallas e historia envolvente.`;

    const rawStatus = meta?.status || 'FINISHED';
    const formattedStatus = rawStatus === 'RELEASING' ? '🟢 EN EMISIÓN' : '🔴 FINALIZADO';

    const baseTitle = jkMeta.seasonTitle || cleanTitle(cleanTitleText, slug);
    const displayTitle = baseTitle
      .replace(/([a-zA-Z])(\d+)/g, '$1 $2')
      .replace(/Season(\d+)/i, 'Season $1')
      .replace(/\s{2,}/g, ' ')
    const rawEnglishTitle = meta?.title?.english || null;
    const formattedEnglishTitle = rawEnglishTitle
      ? rawEnglishTitle
          .replace(/([a-zA-Z])(\d+)/g, '$1 $2')
          .replace(/Season(\d+)/i, 'Season $1')
          .replace(/\s{2,}/g, ' ')
          .trim()
      : null;

    const firstEpisodeStill = jkStills.find(s => s && s.length > 10 && !s.includes('covers/')) || null;

    const resultDetail: AnimeDetail = {
      id: slug,
      title: displayTitle,
      englishTitle: formattedEnglishTitle,
      japaneseTitle: meta?.title?.native || '',
      slug: slug,
      synopsis: finalSynopsis,
      image: meta?.coverImage?.extraLarge || validSpanishMeta?.image || jkMeta.seasonCover || 'https://cdn.animeav1.com/covers/222.jpg',
      backdrop: meta?.bannerImage || validSpanishMeta?.backdrop || finalKitsuMeta || rootBackdrop || firstEpisodeStill || jkMeta.seasonCover || meta?.coverImage?.extraLarge || null,
      genres: (jkMeta.jkGenres && jkMeta.jkGenres.length > 0) ? jkMeta.jkGenres : (meta?.genres || ['Acción', 'Aventura', 'Fantasía']),
      status: formattedStatus,
      score: validSpanishMeta?.score || (meta?.averageScore ? meta.averageScore / 10 : 8.5),
      year: validSpanishMeta?.year || 2024,
      type: mediaType,
      episodes: episodes,
      relatedAnime: jkMeta.relatedAnime,
      recommendedAnime: jkMeta.recommendedAnime,
    };

    apiCache.set(cacheKey, resultDetail, 3600); // 1-hour in-memory cache
    return resultDetail;
  }

  /**
   * Scrape Real Video Stream Links for Specified Anime & Episode with Smart Slug Resolution
   */
  static async getStreamSources(episodeSlug: string, backendBase: string = 'https://apk-animetv-backend.onrender.com'): Promise<WatchResponse> {
    const cacheKey = `stream_v13_${episodeSlug}`;
    const cached = apiCache.get<WatchResponse>(cacheKey);
    if (cached) {
      return cached;
    }

    const parts = episodeSlug.split('/');
    let rawAnimeSlug = parts[0] || 'anime';
    let epNum = parseInt(parts[1] || '1', 10);

    if (rawAnimeSlug.includes('-episode-')) {
      const m = rawAnimeSlug.match(/^(.*?)-episode-(\d+)$/i);
      if (m) {
        rawAnimeSlug = m[1];
        if (isNaN(epNum) || epNum === 1) epNum = parseInt(m[2], 10);
      }
    }

    const animeSlug = cleanSlug(rawAnimeSlug);

    const servers: StreamSource[] = [];

    const DEFAULT_HEADERS = {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
      'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
      'Accept-Language': 'es-ES,es;q=0.9,en-US;q=0.8,en;q=0.7',
    };

    const extractJkServers = async (targetSlug: string, targetEp: number): Promise<StreamSource[]> => {
      const results: StreamSource[] = [];
      try {
        const epUrl = `https://jkanime.net/${targetSlug}/${targetEp}/`;
        const { data: epHtml } = await axios.get(epUrl, {
          headers: DEFAULT_HEADERS,
          timeout: 4500,
        });

        const playerUrls = (epHtml.match(/https?:\/\/jkanime\.net\/jkplayer\/[^"'\s<>]+/g) || []).slice(0, 6);
        const playerPromises = playerUrls.map(async (playerUrl: string, idx: number) => {
          try {
            const { data: playerHtml } = await axios.get(playerUrl, {
              headers: {
                ...DEFAULT_HEADERS,
                Referer: epUrl,
              },
              timeout: 3500,
            });

            const m3u8Match = playerHtml.match(/(https?:\/\/[^\s"'<>]+\.m3u8[^\s"'<>]*)/i);
            const mp4Match = playerHtml.match(/url:\s*['"](https?:\/\/[^'"]+\.mp4[^'"]*)['"]/i) || playerHtml.match(/(https?:\/\/jkplayers\.com\/stream\/[^\s"'<>]+)/i);

            if (m3u8Match && m3u8Match[1]) {
              const proxyUrl = `${backendBase}/api/v1/proxy/m3u8?url=${encodeURIComponent(m3u8Match[1])}`;
              return {
                server: `JKAnime HLS ${idx + 1}`,
                title: `Servidor Principal ${idx + 1} (HLS)`,
                url: proxyUrl,
                format: 'hls' as const,
                audio: 'sub',
                isDirect: true,
              };
            } else if (mp4Match && mp4Match[1]) {
              const rawMp4 = mp4Match[1];
              const proxyUrl = `${backendBase}/api/v1/proxy/video?url=${encodeURIComponent(rawMp4)}`;
              return {
                server: `JKAnime MP4 Direct`,
                title: `Servidor Principal MP4 Direct`,
                url: proxyUrl,
                format: 'mp4' as const,
                audio: 'sub',
                isDirect: true,
              };
            }
          } catch {
            return null;
          }
          return null;
        });

        const settled = await Promise.allSettled(playerPromises);
        for (const res of settled) {
          if (res.status === 'fulfilled' && res.value) {
            if (!results.some(s => s.url === res.value!.url)) {
              results.push(res.value);
            }
          }
        }
      } catch {
        // continue
      }
      return results;
    };

    const extractSecondaryProvider = async (targetSlug: string, targetEp: number): Promise<StreamSource[]> => {
      const secServers: StreamSource[] = [];

      const parsePage = async (url: string) => {
        try {
          const { data: html } = await axios.get(url, {
            headers: DEFAULT_HEADERS,
            timeout: 4500,
          });
          const $ = cheerio.load(html);

          const elements = $('button[data-player], a[data-player]').toArray();
          for (const el of elements) {
            const sName = $(el).text().trim() || 'Servidor';
            const b64 = $(el).attr('data-player');
            if (b64) {
              try {
                const decodedUrl = Buffer.from(b64, 'base64').toString('utf-8');
                const cleanName = sName.toUpperCase();

                const SUPPORTED_HOSTS = ['yourupload', 'mp4upload'];
                const isSupportedProxy = SUPPORTED_HOSTS.some(h => decodedUrl.toLowerCase().includes(h));

                // Check for REAL file extensions, not substrings of domain names
                // e.g. '.mp4upload.com' should NOT match as a .mp4 file
                const hasRealM3u8 = /\.m3u8(\?|#|$)/i.test(decodedUrl);
                const hasRealMp4 = /\.mp4(\?|#|$)/i.test(decodedUrl);

                if (hasRealM3u8 || hasRealMp4) {
                  // Direct playable stream — no proxy needed
                  const proxyUrl = `${backendBase}/api/v1/proxy/video?url=${encodeURIComponent(decodedUrl)}`;
                  secServers.push({
                    server: `AnimeFLV / ${cleanName}`,
                    title: `Servidor AnimeFLV ${targetEp} (${cleanName})`,
                    url: proxyUrl,
                    format: hasRealM3u8 ? 'hls' : 'mp4',
                    audio: 'sub',
                    isDirect: true,
                  });
                } else if (isSupportedProxy) {
                  // Embed page — route through proxy extractor to get real video URL
                  const proxyUrl = `${backendBase}/api/v1/proxy/video?url=${encodeURIComponent(decodedUrl)}`;
                  secServers.push({
                    server: `AnimeFLV / ${cleanName}`,
                    title: `Servidor AnimeFLV ${targetEp} (${cleanName})`,
                    url: proxyUrl,
                    format: 'mp4',
                    audio: 'sub',
                    isDirect: true,
                  });
                }
                // else: unsupported host — skip to avoid black screen
              } catch {
                // ignore
              }
            }
          }
        } catch {
          // ignore
        }
      };

      // 1. Direct slug try
      await parsePage(`https://monoschinos2.com/ver/${targetSlug}-episodio-${targetEp}`);

      // 2. Candidate search fallback if direct slug yielded 0 servers
      if (secServers.length === 0) {
        try {
          const cleanQuery = targetSlug.replace(/-/g, ' ');
          const searchUrl = `https://monoschinos2.com/buscar?q=${encodeURIComponent(cleanQuery)}`;
          const { data: searchHtml } = await axios.get(searchUrl, {
            headers: DEFAULT_HEADERS,
            timeout: 4000,
          });
          const $ = cheerio.load(searchHtml);
          const candidateSlugs: string[] = [];

          $('a').each((_, el) => {
            const href = $(el).attr('href') || '';
            if (href.includes('/anime/') || href.includes('/ver/')) {
              const cleaned = href.replace(/https?:\/\/[^\/]+/g, '').replace('/anime/', '').replace('/ver/', '').replace(/-episodio-\d+$/g, '');
              if (cleaned && !candidateSlugs.includes(cleaned)) {
                candidateSlugs.push(cleaned);
              }
            }
          });

          for (const cand of candidateSlugs.slice(0, 3)) {
            await parsePage(`https://monoschinos2.com/ver/${cand}-episodio-${targetEp}`);
            if (secServers.length > 0) break;
          }
        } catch {
          // ignore
        }
      }

      return secServers;
    };

    // Run JKAnime and AnimeFLV/Secondary scrapers in parallel
    const [jkResult, secResult] = await Promise.allSettled([
      extractJkServers(animeSlug, epNum),
      extractSecondaryProvider(animeSlug, epNum),
    ]);

    let jkServers: StreamSource[] = [];
    if (jkResult.status === 'fulfilled') {
      jkServers = jkResult.value;
    }

    // If direct JKAnime slug failed, search by title keyword
    if (jkServers.length === 0) {
      const cleanTitleText = animeSlug.replace(/-/g, ' ');
      try {
        const searchUrl = `${CONFIG.JKANIME_BASE_URL}/buscar/${encodeURIComponent(cleanTitleText)}/1/`;
        const { data: searchHtml } = await axios.get(searchUrl, {
          headers: DEFAULT_HEADERS,
          timeout: 3500,
        });
        const $ = cheerio.load(searchHtml);
        const candidateSlugs: string[] = [];

        $('a').each((_, el) => {
          const href = $(el).attr('href') || '';
          if (href.includes('jkanime.net/') && href.split('/').filter(Boolean).length === 4) {
            const pathParts = href.split('/').filter(Boolean);
            const foundSlug = pathParts[pathParts.length - 1];
            if (foundSlug && !['buscar', 'api', 'auth'].includes(foundSlug)) {
              candidateSlugs.push(foundSlug);
            }
          }
        });

        for (const candidate of candidateSlugs.slice(0, 3)) {
          const foundList = await extractJkServers(candidate, epNum);
          if (foundList.length > 0) {
            jkServers = foundList;
            break;
          }
        }
      } catch {
        // ignore
      }
    }

    if (jkServers.length > 0) {
      servers.push(...jkServers);
    }

    // Add secondary provider (AnimeFLV) servers
    if (secResult.status === 'fulfilled' && secResult.value.length > 0) {
      servers.push(...secResult.value);
    }

    // Fallback if no servers found anywhere
    if (servers.length === 0) {
      servers.push({
        server: 'AnimeTV HLS CDN',
        title: `Servidor HLS HD ${epNum}`,
        url: 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8',
        format: 'hls',
        audio: 'sub',
        isDirect: true,
      });
    }

    const response: WatchResponse = {
      animeTitle: animeSlug,
      episodeNumber: epNum,
      servers: servers,
    };

    if (servers.length > 0 && !servers[0].url.includes('mux.dev')) {
      apiCache.set(cacheKey, response, 7200); // 2-hour TTL cache
    }

    return response;
  }
}
