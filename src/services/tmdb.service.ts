import axios from 'axios';
import { CONFIG } from '../config/constants';

export interface AniListMedia {
  id: number;
  title: {
    romaji: string;
    english: string;
    native: string;
  };
  format: string;
  episodes: number | null;
  status: string | null;
  bannerImage: string | null;
  coverImage: {
    extraLarge: string;
    large: string;
  };
  description: string;
  genres: string[];
  averageScore: number;
}

export interface EnrichedMetadata {
  synopsis: string | null;
  backdrop: string | null;
  image: string | null;
  score: number | null;
  year: number | null;
  episodeStills: Record<string, string>;
}

export class TMDBService {


  /**
   * Generates smart prioritized search candidates from complex anime titles
   */
  private static buildSearchCandidates(title: string): string[] {
    const candidates = new Set<string>();
    if (!title) return [];

    const rawTitle = title.trim();

    // 1. Subtitle after ':' (e.g. "Sword Art Online: Unanswered//butterfly" -> "Unanswered//butterfly")
    if (rawTitle.includes(':')) {
      const afterColon = rawTitle.split(':').slice(1).join(':').trim();
      if (afterColon) candidates.add(afterColon);
    }

    // 2. Subtitle or title with slashes replaced with space or slash
    if (rawTitle.includes('//')) {
      candidates.add(rawTitle.replace(/\/\//g, ' / '));
      candidates.add(rawTitle.replace(/\/\//g, ' '));
    }

    // 3. Cleaned subtitle without slashes
    if (rawTitle.includes(':') && rawTitle.includes('//')) {
      const afterColon = rawTitle.split(':').slice(1).join(':').trim();
      if (afterColon) candidates.add(afterColon.replace(/\/\//g, ' ').trim());
    }

    // 4. Cleaned full title without punctuation
    const cleanTitle = rawTitle
      .replace(/[\/\/:_\-~]+/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
    if (cleanTitle) candidates.add(cleanTitle);

    // 5. Full original title
    candidates.add(rawTitle);

    // 6. Base franchise title before ':' (e.g. "Sword Art Online")
    if (rawTitle.includes(':')) {
      const mainFranchise = rawTitle.split(':')[0].trim();
      if (mainFranchise) candidates.add(mainFranchise);
    }

    return Array.from(candidates);
  }

  /**
   * Fetch metadata enriched with 100% Spanish synopsis & episode stills from TMDB / AnimeVerse
   */
  static async getSpanishMetadata(title: string): Promise<EnrichedMetadata | null> {
    if (!title) return null;

    const candidates = this.buildSearchCandidates(title);

    // 1. Try TMDB / AnimeVerse with each candidate in waterfall priority
    for (const queryTitle of candidates) {
      try {
        const url = `${CONFIG.ANIMEVERSE_BASE_URL}/api/v1/anime/enrich?title=${encodeURIComponent(queryTitle)}`;
        const { data } = await axios.get(url, { timeout: 4000 });

        if (data) {
          const details = data.details || data.tmdb || {};
          const overview = details.overview || null;
          const backdropPath = details.backdrop_path ? `${CONFIG.TMDB_IMAGE_BASE}${details.backdrop_path}` : null;
          const posterPath = details.poster_path ? `${CONFIG.TMDB_IMAGE_BASE}${details.poster_path}` : null;
          const voteAverage = details.vote_average ? Math.round(details.vote_average * 10) / 10 : null;
          const year = details.first_air_date ? parseInt(details.first_air_date.split('-')[0], 10) : null;
          const episodeStills: Record<string, string> = data.episodeStills || {};

          if (backdropPath || overview) {
            return {
              synopsis: overview,
              backdrop: backdropPath,
              image: posterPath,
              score: voteAverage,
              year: year,
              episodeStills: episodeStills,
            };
          }
        }
      } catch {
        // Try next candidate
      }
    }

    // 2. Fallback: Query AniList across generated candidates for official 16:9 anime bannerImage
    for (const queryTitle of candidates) {
      try {
        const aniList = await this.getAniListMetadata(queryTitle);
        if (aniList && aniList.bannerImage) {
          return {
            synopsis: aniList.description ? await this.translateToSpanish(aniList.description) : null,
            backdrop: aniList.bannerImage,
            image: aniList.coverImage?.extraLarge || aniList.coverImage?.large || null,
            score: aniList.averageScore ? Math.round((aniList.averageScore / 10) * 10) / 10 : null,
            year: null,
            episodeStills: {},
          };
        }
      } catch (_e) {}
    }

    return null;
  }

  /**
   * Fetch AniList media info (episodes count, genres, native title)
   */
  static async getAniListMetadata(title: string): Promise<Partial<AniListMedia> | null> {
    const query = `
      query ($search: String) {
        Media (search: $search, type: ANIME) {
          id
          format
          title {
            romaji
            english
            native
          }
          episodes
          status
          bannerImage
          coverImage {
            extraLarge
            large
          }
          description
          genres
          averageScore
        }
      }
    `;

    try {
      const response = await axios.post(
        CONFIG.ANILIST_API_URL,
        {
          query,
          variables: { search: title },
        },
        { timeout: 4000 }
      );

      return response.data?.data?.Media || null;
    } catch {
      return null;
    }
  }

  /**
   * Fetch Kitsu cover image banner
   */
  static async getKitsuMetadata(title: string): Promise<string | null> {
    try {
      const url = `https://kitsu.io/api/edge/anime?filter[text]=${encodeURIComponent(title)}`;
      const { data } = await axios.get(url, {
        headers: {
          'Accept': 'application/vnd.api+json',
          'Content-Type': 'application/vnd.api+json',
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
        },
        timeout: 4000,
      });
      if (data && Array.isArray(data.data) && data.data.length > 0) {
        const coverImage = data.data[0].attributes?.coverImage;
        return coverImage?.original || coverImage?.large || coverImage?.small || null;
      }
    } catch {
      // ignore
    }
    return null;
  }

  /**
   * Translate English text to Spanish using Google Translate API
   */
  static async translateToSpanish(text: string): Promise<string | null> {
    if (!text) return null;
    try {
      const cleanText = text.replace(/<[^>]*>?/gm, '').trim();
      if (!cleanText) return null;
      const url = `https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=es&dt=t&q=${encodeURIComponent(cleanText)}`;
      const res = await axios.get(url, { timeout: 4000 });
      if (res.data && res.data[0]) {
        const translated = res.data[0].map((item: any) => item[0]).join('');
        if (translated && translated.trim().length > 0) {
          return translated.trim();
        }
      }
    } catch {
      // ignore
    }
    return null;
  }
}
