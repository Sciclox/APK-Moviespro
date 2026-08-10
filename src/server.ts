import { CONFIG } from './config/constants';
import { startCacheWorker } from './workers/cacheWorker';
import app from './app';

const PORT = process.env.PORT || CONFIG.PORT || 3000;

if (require.main === module) {
  app.listen(PORT, () => {
    console.log(`APK-AnimeTV API running at port ${PORT}`);
    startCacheWorker(30);
  });
}

export default app;
