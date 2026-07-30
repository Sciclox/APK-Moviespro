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
   * Fetch metadata enriched with 100% Spanish synopsis & episode stills from TMDB / AnimeVerse
   */
  static async getSpanishMetadata(title: string): Promise<EnrichedMetadata | null> {
    try {
      const url = `${CONFIG.ANIMEVERSE_BASE_URL}/api/v1/anime/enrich?title=${encodeURIComponent(title)}`;
      const { data } = await axios.get(url, { timeout: 5000 });

      if (data) {
        const details = data.details || data.tmdb || {};
        const overview = details.overview || null;
        const backdropPath = details.backdrop_path ? `${CONFIG.TMDB_IMAGE_BASE}${details.backdrop_path}` : null;
        const posterPath = details.poster_path ? `${CONFIG.TMDB_IMAGE_BASE}${details.poster_path}` : null;
        const voteAverage = details.vote_average ? Math.round(details.vote_average * 10) / 10 : null;
        const year = details.first_air_date ? parseInt(details.first_air_date.split('-')[0], 10) : null;
        const episodeStills: Record<string, string> = data.episodeStills || {};

        return {
          synopsis: overview,
          backdrop: backdropPath,
          image: posterPath,
          score: voteAverage,
          year: year,
          episodeStills: episodeStills,
        };
      }
    } catch {
      // ignore
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
