import app from './server';
import http from 'http';

const server = http.createServer(app);

server.listen(3001, async () => {
  console.log('Testing server running on port 3001...');
  
  const endpoints = [
    'http://localhost:3001/api/v1/health',
    'http://localhost:3001/api/v1/anime/hero',
    'http://localhost:3001/api/v1/anime/search?q=naruto',
    'http://localhost:3001/api/v1/anime/catalog?genre=Accion',
    'http://localhost:3001/api/v1/anime/details/sword-art-online',
    'http://localhost:3001/api/v1/anime/watch/sword-art-online/1',
  ];

  for (const url of endpoints) {
    try {
      const res = await fetch(url);
      const json: any = await res.json();
      console.log(`\n✅ GET ${url} -> Status: ${res.status}`);
      console.log('Data keys:', Object.keys(json));
      if (json.data) {
        if (Array.isArray(json.data)) {
          console.log(`Array items count: ${json.data.length}, First item: ${json.data[0]?.title || json.data[0]?.id}`);
        } else {
          console.log(`Object title: ${json.data.title || json.data.animeTitle}`);
        }
      }
    } catch (e) {
      console.error(`❌ GET ${url} -> Error:`, e);
    }
  }

  server.close(() => {
    console.log('\nTesting completed successfully!');
    process.exit(0);
  });
});
